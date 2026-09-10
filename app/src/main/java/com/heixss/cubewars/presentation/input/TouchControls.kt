package com.heixss.cubewars.presentation.input

/** The on-screen buttons, in virtual-screen coordinates with the origin at the bottom-left. */
enum class ControlButton {
    MOVE_LEFT,
    MOVE_RIGHT,
    JUMP,
    FIRE,
    PAUSE,
}

/** One touchable circle. */
data class ControlZone(
    val button: ControlButton,
    val centerX: Float,
    val centerY: Float,
    /** Drawn radius. */
    val radius: Float,
) {
    /**
     * Fingers are imprecise and there is no haptic edge to feel for, so the touchable area is
     * deliberately larger than the circle the player can see.
     */
    private val touchRadiusSquared = (radius * TOUCH_FORGIVENESS) * (radius * TOUCH_FORGIVENESS)

    fun contains(x: Float, y: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= touchRadiusSquared
    }

    private companion object {
        const val TOUCH_FORGIVENESS = 1.3f
    }
}

/**
 * Where the controls sit on the 480x270 virtual screen.
 *
 * Movement lives under the left thumb and the guns under the right, with the fire button largest
 * because it is the one held down the most — and because it doubles as an aiming stick: the
 * direction from its centre to the finger is the direction the player shoots.
 */
object TouchControls {

    val zones: List<ControlZone> = listOf(
        ControlZone(ControlButton.MOVE_LEFT, centerX = 44f, centerY = 46f, radius = 25f),
        ControlZone(ControlButton.MOVE_RIGHT, centerX = 103f, centerY = 46f, radius = 25f),
        ControlZone(ControlButton.JUMP, centerX = 373f, centerY = 58f, radius = 25f),
        ControlZone(ControlButton.FIRE, centerX = 437f, centerY = 60f, radius = 29f),
        ControlZone(ControlButton.PAUSE, centerX = 462f, centerY = 252f, radius = 13f),
    )

    /**
     * How far the finger must leave the fire pad's centre before it counts as aiming.
     *
     * A plain tap should still shoot straight ahead, and a thumb resting on a button never sits
     * perfectly still, so the first few pixels of travel mean nothing.
     */
    const val AIM_DEAD_ZONE = 7f

    /** The zone under a virtual-screen point, or null. */
    fun hitTest(x: Float, y: Float): ControlZone? = zones.firstOrNull { it.contains(x, y) }

    fun zoneFor(button: ControlButton): ControlZone = zones.first { it.button == button }
}
