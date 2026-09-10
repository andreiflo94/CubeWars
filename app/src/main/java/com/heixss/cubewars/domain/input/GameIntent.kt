package com.heixss.cubewars.domain.input

/**
 * Something the player asked for.
 *
 * Intents are the only way the outside world can influence the simulation. The presentation layer
 * translates touches and key presses into these and pushes them onto a flow; `GameEngine` folds
 * each one into the state. Nothing in the domain knows a touchscreen exists.
 */
import com.heixss.cubewars.domain.model.Vec2

sealed interface GameIntent {

    /** Horizontal stick position, clamped to `[-1, 1]`. */
    data class Move(val axis: Float) : GameIntent

    data class HoldJump(val held: Boolean) : GameIntent

    data class HoldFire(val held: Boolean) : GameIntent

    /**
     * Points the gun. [direction] need not be normalised; null drops the free aim and returns the
     * gun to whichever way the cube is facing.
     */
    data class Aim(val direction: Vec2?) : GameIntent

    /** Leaves the title card, or restarts after a loss. */
    data object Start : GameIntent

    data object TogglePause : GameIntent

    /** Abandons the current run and returns to the title card. */
    data object Restart : GameIntent

    /** Releases every held control, e.g. when the app loses focus. */
    data object ReleaseAll : GameIntent
}
