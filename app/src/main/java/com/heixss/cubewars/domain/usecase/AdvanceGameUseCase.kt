package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.repository.LevelRepository

/**
 * Advances the whole world by one step.
 *
 * This is the heart of the design: `(GameState, dt) -> GameState`, with no I/O, no clock, no
 * randomness outside the state, and no knowledge of libGDX or Android. Everything it does is
 * delegated to an ordered list of [SimulationStage]s, and the order is the specification:
 *
 * 1. the wave director decides who exists,
 * 2. the player and the enemies decide where they want to be,
 * 3. bullets fly,
 * 4. combat resolves every collision at once,
 * 5. debris ages and the camera reframes,
 * 6. and only then, with every rule finished with the old world, the exit may swap the level.
 *
 * Resolving combat *after* all movement means a bullet and a cube that swap places in the same step
 * still trade damage, and it guarantees no entity gets to act on a half-updated world.
 */
class AdvanceGameUseCase(
    private val stages: List<SimulationStage>,
    private val epilogueStages: List<SimulationStage>,
) {

    operator fun invoke(state: GameState, dt: Float): GameState {
        val active = when (state.status) {
            // After a loss the world keeps breathing — debris settles, the camera drifts back —
            // but nothing is allowed to change the outcome.
            GameStatus.GAME_OVER -> epilogueStages
            GameStatus.RUNNING -> stages
            GameStatus.READY, GameStatus.PAUSED -> return state.clearEvents()
        }

        val stepped = state.clearEvents().copy(elapsedTime = state.elapsedTime + dt)
        return active.fold(stepped) { current, stage -> stage(current, dt) }
    }

    /** Frame events describe the step that produced them, so every step starts from empty. */
    private fun GameState.clearEvents(): GameState =
        if (frameEvents.isEmpty()) this else copy(frameEvents = emptyList())

    companion object {
        /** The stage list the real game runs. */
        fun default(levelRepository: LevelRepository): AdvanceGameUseCase = AdvanceGameUseCase(
            stages = listOf(
                UpdateWaveUseCase(),
                UpdatePlayerUseCase(),
                UpdateEnemiesUseCase(),
                UpdateProjectilesUseCase(),
                ResolveCombatUseCase(),
                UpdateParticlesUseCase(),
                UpdateCameraUseCase(),
                AdvanceLevelUseCase(levelRepository),
            ),
            epilogueStages = listOf(
                UpdateProjectilesUseCase(),
                UpdateParticlesUseCase(),
                UpdateCameraUseCase(),
            ),
        )
    }
}
