package com.heixss.cubewars.presentation.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.Vec2

/**
 * Turns fingers and keys into [GameIntent]s.
 *
 * This is the whole of the input layer: it resolves which virtual button a pointer is over, tracks
 * multi-touch so the player can strafe and shoot at once, reads the fire pad as an aiming stick, and
 * emits intents on state *changes* only. It knows nothing about the simulation — it cannot, because
 * intents are the only channel it has.
 */
class GameInputProcessor(
    private val viewport: Viewport,
    private val dispatch: (GameIntent) -> Unit,
) : InputAdapter() {

    /**
     * False on the title card, the pause screen and the game-over screen, where every touch means
     * "get on with it" rather than "shoot".
     */
    var gameplayEnabled: Boolean = false

    /** Which button each active pointer is holding, so lifting one finger releases only its button. */
    private val pointerAssignments = HashMap<Int, ControlButton>()

    private val heldByKeyboard = HashSet<ControlButton>()

    /** Aim asked for by the finger on the fire pad, or null while it is resting on the centre. */
    private var touchAim: Vec2? = null

    private var aimUpKey = false
    private var aimDownKey = false

    private val scratch = Vector3()

    /** Buttons currently held by anything at all. */
    private val held: Set<ControlButton>
        get() = pointerAssignments.values.toSet() + heldByKeyboard

    // -----------------------------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------------------------

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val point = unproject(screenX, screenY)
        val zone = TouchControls.hitTest(point.x, point.y)

        if (zone?.button == ControlButton.PAUSE) {
            dispatch(GameIntent.TogglePause)
            return true
        }

        if (!gameplayEnabled) {
            dispatch(GameIntent.Start)
            return true
        }

        if (zone == null) return false
        pointerAssignments[pointer] = zone.button
        // A tap on the pad shoots straight ahead; the aim only appears once the thumb moves.
        if (zone.button == ControlButton.FIRE) touchAim = aimFrom(point.x, point.y)
        publish()
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!gameplayEnabled) return false
        val point = unproject(screenX, screenY)

        // The fire pad is a stick, not a button: once a finger has claimed it, it keeps it however
        // far the thumb wanders, and the offset from the pad's centre becomes the aim.
        if (pointerAssignments[pointer] == ControlButton.FIRE) {
            val aim = aimFrom(point.x, point.y)
            if (aim == touchAim) return false
            touchAim = aim
            publish()
            return true
        }

        // Everything else: sliding between the two movement buttons changes direction rather than
        // sticking to the button the finger started on.
        val next = TouchControls.hitTest(point.x, point.y)
            ?.button
            ?.takeIf { it != ControlButton.PAUSE }
        val current = pointerAssignments[pointer]
        if (next == current) return false

        if (next == null) pointerAssignments.remove(pointer) else pointerAssignments[pointer] = next
        if (next == ControlButton.FIRE) touchAim = aimFrom(point.x, point.y)
        publish()
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val released = pointerAssignments.remove(pointer) ?: return false
        if (released == ControlButton.FIRE && ControlButton.FIRE !in pointerAssignments.values) {
            touchAim = null
        }
        publish()
        return true
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        touchUp(screenX, screenY, pointer, button)

    private fun unproject(screenX: Int, screenY: Int): Vector3 {
        scratch.set(screenX.toFloat(), screenY.toFloat(), 0f)
        return viewport.unproject(scratch)
    }

    /** The aim a finger at this virtual-screen point asks for, or null inside the dead zone. */
    private fun aimFrom(x: Float, y: Float): Vec2? {
        val zone = TouchControls.zoneFor(ControlButton.FIRE)
        val offset = Vec2(x - zone.centerX, y - zone.centerY)
        return if (offset.length < TouchControls.AIM_DEAD_ZONE) null else offset.normalized()
    }

    // -----------------------------------------------------------------------------------------
    // Keyboard, for emulators and attached hardware
    // -----------------------------------------------------------------------------------------

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.P || keycode == Input.Keys.ESCAPE) {
            dispatch(GameIntent.TogglePause)
            return true
        }
        if (!gameplayEnabled && keycode == Input.Keys.ENTER) {
            dispatch(GameIntent.Start)
            return true
        }
        if (isAimKey(keycode)) {
            if (!gameplayEnabled) {
                dispatch(GameIntent.Start)
                return true
            }
            setAimKey(keycode, down = true)
            publish()
            return true
        }
        val button = keyMapping(keycode) ?: return false
        if (!gameplayEnabled) {
            dispatch(GameIntent.Start)
            return true
        }
        heldByKeyboard += button
        publish()
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        if (isAimKey(keycode)) {
            setAimKey(keycode, down = false)
            publish()
            return true
        }
        val button = keyMapping(keycode) ?: return false
        heldByKeyboard -= button
        publish()
        return true
    }

    private fun keyMapping(keycode: Int): ControlButton? = when (keycode) {
        Input.Keys.LEFT, Input.Keys.A -> ControlButton.MOVE_LEFT
        Input.Keys.RIGHT, Input.Keys.D -> ControlButton.MOVE_RIGHT
        Input.Keys.SPACE, Input.Keys.W -> ControlButton.JUMP
        Input.Keys.J, Input.Keys.CONTROL_LEFT -> ControlButton.FIRE
        else -> null
    }

    private fun isAimKey(keycode: Int): Boolean =
        keycode == Input.Keys.UP || keycode == Input.Keys.DOWN

    private fun setAimKey(keycode: Int, down: Boolean) {
        if (keycode == Input.Keys.UP) aimUpKey = down else aimDownKey = down
    }

    /**
     * The keyboard's stand-in for the aiming stick: up/down tilt the gun, and holding a movement key
     * at the same time gives the diagonals.
     */
    private fun keyboardAim(): Vec2? {
        val y = (if (aimUpKey) 1f else 0f) + (if (aimDownKey) -1f else 0f)
        if (y == 0f) return null
        val x = when {
            ControlButton.MOVE_LEFT in heldByKeyboard && ControlButton.MOVE_RIGHT in heldByKeyboard -> 0f
            ControlButton.MOVE_LEFT in heldByKeyboard -> -1f
            ControlButton.MOVE_RIGHT in heldByKeyboard -> 1f
            else -> 0f
        }
        return Vec2(x, y).normalized()
    }

    // -----------------------------------------------------------------------------------------

    private var lastAxis = 0f
    private var lastJump = false
    private var lastFire = false
    private var lastAim: Vec2? = null

    /**
     * Recomputes the control state and pushes only what changed.
     *
     * Emitting edges rather than levels matters: `HoldJump(true)` stamps the jump buffer, so
     * re-sending it every time an unrelated finger moved would let a held jump button fire again
     * the instant the player walked off a ledge.
     */
    private fun publish() {
        val down = held
        val axis = when {
            ControlButton.MOVE_LEFT in down && ControlButton.MOVE_RIGHT in down -> 0f
            ControlButton.MOVE_LEFT in down -> -1f
            ControlButton.MOVE_RIGHT in down -> 1f
            else -> 0f
        }
        val jump = ControlButton.JUMP in down
        val fire = ControlButton.FIRE in down
        // The thumb on the pad wins over the keyboard, since only one of the two is ever in use.
        val aim = touchAim ?: keyboardAim()

        if (axis != lastAxis) dispatch(GameIntent.Move(axis)).also { lastAxis = axis }
        if (jump != lastJump) dispatch(GameIntent.HoldJump(jump)).also { lastJump = jump }
        if (fire != lastFire) dispatch(GameIntent.HoldFire(fire)).also { lastFire = fire }
        if (aim != lastAim) dispatch(GameIntent.Aim(aim)).also { lastAim = aim }
    }

    /** Buttons to draw as pressed. */
    fun pressedButtons(): Set<ControlButton> = held

    /** Drops every held control, e.g. when the app loses focus. */
    fun releaseAll() {
        pointerAssignments.clear()
        heldByKeyboard.clear()
        touchAim = null
        aimUpKey = false
        aimDownKey = false
        lastAxis = 0f
        lastJump = false
        lastFire = false
        lastAim = null
        dispatch(GameIntent.ReleaseAll)
    }
}
