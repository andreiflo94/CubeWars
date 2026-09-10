package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.InputState
import com.heixss.cubewars.domain.model.Vec2

/**
 * Folds one player intent into the state.
 *
 * Intents mostly only change [InputState] or [GameStatus], and the simulation stages read the result
 * on the next tick. That separation is why input can arrive on any thread at any rate without
 * desynchronising the world.
 *
 * The two exceptions are the edges of the jump button, which have to be handled here precisely
 * *because* they are edges: a press stamps the jump buffer and a release clips the arc. Testing the
 * held/not-held level once per tick instead cannot tell "released while this jump was rising" apart
 * from "was never held to begin with".
 */
class ApplyIntentUseCase(
    private val createGame: CreateGameUseCase,
) {

    operator fun invoke(state: GameState, intent: GameIntent): GameState = when (intent) {
        is GameIntent.Move -> state.copy(
            input = state.input.copy(moveAxis = intent.axis.coerceIn(-1f, 1f)),
        )

        is GameIntent.HoldJump -> state.copy(
            input = state.input.copy(jumpHeld = intent.held),
            player = if (intent.held) {
                // Stamping the buffer on the press edge is what lets a player mash jump just
                // before landing and still get the jump.
                state.player.copy(jumpBufferTimer = GameConfig.JUMP_BUFFER_TIME)
            } else {
                // Releasing clips whatever jump is rising *right now*. A jump that is still sitting
                // in the buffer has not started yet, so it is untouched and launches at full height
                // when the player lands — releasing early cannot retroactively shrink it.
                state.player.clipRisingJump()
            },
        )

        is GameIntent.HoldFire -> state.copy(input = state.input.copy(fireHeld = intent.held))

        // Normalising here rather than at the call site keeps every aim in the state a unit vector,
        // so the simulation never has to care how far the thumb happened to travel.
        is GameIntent.Aim -> state.copy(
            input = state.input.copy(
                aim = intent.direction?.normalized()?.takeIf { it != Vec2.ZERO },
            ),
        )

        GameIntent.Start -> when (state.status) {
            GameStatus.READY -> state.copy(status = GameStatus.RUNNING)
            GameStatus.GAME_OVER -> restart(state)
            GameStatus.PAUSED -> state.copy(status = GameStatus.RUNNING)
            GameStatus.RUNNING -> state
        }

        GameIntent.TogglePause -> when (state.status) {
            GameStatus.RUNNING -> state.copy(status = GameStatus.PAUSED, input = InputState())
            GameStatus.PAUSED -> state.copy(status = GameStatus.RUNNING)
            else -> state
        }

        GameIntent.Restart -> restart(state).copy(status = GameStatus.READY)

        GameIntent.ReleaseAll -> state.copy(input = InputState())
    }

    /**
     * Rebuilds the world while carrying the two things that outlive a run: the best score, and the
     * generator position — so a retry is a genuinely different fight.
     *
     * A retry always starts at the first arena. Progress through the rotation is the run; keeping it
     * would be keeping the part that was earned.
     */
    private fun restart(state: GameState): GameState = createGame(
        seed = state.rng.next().seed,
        highScore = maxOf(state.highScore, state.score),
        status = GameStatus.RUNNING,
    )
}
