package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.CameraState
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.domain.repository.LevelRepository

/**
 * Carries the player through the exit gate into the next arena.
 *
 * This is the only stage that swaps the level out from under the simulation, which is why it runs
 * last: every other rule has finished with the old world by the time the new one is laid down. It
 * needs no randomness and no clock, so a run remains reproducible from its seed.
 *
 * Levels rotate rather than run out — [LevelRepository.loadLevel] wraps — and the wave index carries
 * across untouched, so a second lap through the same geometry is fought against a much worse crowd.
 */
class AdvanceLevelUseCase(
    private val levelRepository: LevelRepository,
) : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        if (state.wave.phase != WavePhase.COMPLETE) return state
        if (!state.player.isAlive) return state
        if (!state.player.bounds.overlaps(state.level.exit)) return state

        val next = levelRepository.loadLevel(state.level.id + 1)
        val levelNumber = state.levelNumber + 1

        return state.copy(
            level = next,
            player = state.player.copy(
                position = next.playerSpawn,
                velocity = Vec2.ZERO,
                onGround = false,
                // One point of health back per arena — enough that a long run is survivable, not so
                // much that taking hits stops mattering.
                health = (state.player.health + 1).coerceAtMost(state.player.maxHealth),
                coyoteTimer = 0f,
                jumpBufferTimer = 0f,
                fireCooldown = 0f,
                invulnerabilityTimer = GameConfig.PLAYER_INVULNERABILITY_TIME,
            ),
            // Nothing from the last arena follows the player through the gate.
            enemies = emptyList(),
            projectiles = emptyList(),
            particles = emptyList(),
            camera = CameraState(center = next.cameraStart),
            wave = state.wave.copy(
                phase = WavePhase.INTERMISSION,
                phaseTimer = GameConfig.LEVEL_INTRO_TIME,
                clearedInLevel = 0,
                remainingToSpawn = 0,
                spawnTimer = 0f,
                killsThisWave = 0,
            ),
            levelNumber = levelNumber,
            score = state.score + GameConfig.LEVEL_CLEAR_BONUS * state.levelNumber,
            frameEvents = state.frameEvents + GameEvent.LevelEntered(next.name, levelNumber),
        )
    }
}
