package com.heixss.cubewars.domain.model

/**
 * A purely functional pseudo random generator.
 *
 * The generator state lives inside [GameState], which keeps `AdvanceGameUseCase` a total function
 * of `(state, dt) -> state`: the same state advanced by the same delta always yields the same next
 * state. That is what makes the simulation unit-testable without any test doubles.
 */
@JvmInline
value class Rng(val seed: Long) {

    /** The generator advanced by one step. */
    fun next(): Rng = Rng(seed * 6364136223846793005L + 1442695040888963407L)

    /** A value in `[0, 1)` derived from the current state. */
    val unit: Float
        get() = ((seed ushr 40).toInt() and 0xFFFFFF) / 16_777_216f
}
