package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.model.GameState

/**
 * One rule of the game, expressed as a pure transformation of the world.
 *
 * Every stage has the same shape, so `AdvanceGameUseCase` composes them by folding rather than by
 * knowing what any of them do. Adding a rule — wind, power-ups, a boss — means adding a stage to
 * the list, not editing the orchestrator.
 */
fun interface SimulationStage {

    /** Returns the state [state] becomes after [dt] seconds of this rule being applied. */
    operator fun invoke(state: GameState, dt: Float): GameState
}
