package com.heixss.cubewars.domain.model

/**
 * The player's currently-held controls.
 *
 * Intents fold into this; the simulation only ever reads it. Keeping input inside the state means
 * `AdvanceGameUseCase` needs no second argument beyond the delta, and a recorded state stream
 * replays the player's hands as well as the world.
 */
data class InputState(
    /** -1 full left, +1 full right. */
    val moveAxis: Float = 0f,
    val jumpHeld: Boolean = false,
    val fireHeld: Boolean = false,
    /**
     * Where the firing thumb is pointing, as a unit vector, or null for "no opinion" — in which case
     * the cube shoots the way it is facing.
     */
    val aim: Vec2? = null,
) {
    val isMoving: Boolean get() = moveAxis != 0f
}
