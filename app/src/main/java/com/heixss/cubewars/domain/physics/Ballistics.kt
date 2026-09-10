package com.heixss.cubewars.domain.physics

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The arithmetic of a falling bullet.
 *
 * Now that every round carries its own gravity, "point at the target" is no longer the same thing as
 * "hit the target": a shot fired straight at a cube fifteen tiles away lands a body-length short of
 * it. This is the one place that knows how to correct for that, and both sides of the fight use it —
 * the enemies to aim, and the renderer to draw the player the arc their own shot will take.
 *
 * Everything here is a pure function of its arguments. No state, no randomness, no clock.
 */
object Ballistics {

    /**
     * The launch direction that drops a round of [speed] onto [target], given how hard that round
     * falls.
     *
     * Two arcs reach any point inside a gun's envelope — a flat one and a lobbed one. [lofted]
     * chooses between them: a rifle wants the flat solution, a mortar wants the high one that clears
     * whatever is in between.
     *
     * Returns `null` when the target is simply out of range, which the caller should read as "do not
     * take this shot" rather than as an excuse to fire blindly.
     */
    fun launchDirection(
        origin: Vec2,
        target: Vec2,
        speed: Float,
        gravityScale: Float,
        lofted: Boolean = false,
    ): Vec2? {
        val toTarget = target - origin
        if (speed <= 0f) return null
        // A round that does not fall flies where it is pointed.
        if (gravityScale <= 0.0001f) return toTarget.normalized().takeIf { it != Vec2.ZERO }

        val gravity = -GameConfig.GRAVITY * gravityScale
        val horizontal = abs(toTarget.x)
        // Directly overhead: there is no arc to solve, only up or down.
        if (horizontal < 0.5f) return toTarget.normalized().takeIf { it != Vec2.ZERO }

        val speedSquared = speed * speed
        val discriminant = speedSquared * speedSquared -
            gravity * (gravity * horizontal * horizontal + 2f * toTarget.y * speedSquared)
        if (discriminant < 0f) return null

        val root = sqrt(discriminant)
        val tangent = (if (lofted) speedSquared + root else speedSquared - root) / (gravity * horizontal)
        val direction = Vec2(horizontal, horizontal * tangent).normalized()
        return if (toTarget.x < 0f) Vec2(-direction.x, direction.y) else direction
    }

    /**
     * Where the target will be by the time a round fired now could reach it.
     *
     * Two refinements are plenty: the first guesses the flight time from the current gap, the second
     * corrects it against the lead the first produced. A third would move the answer by less than a
     * pixel, and this runs for every marksman on the field every time it pulls the trigger.
     */
    fun leadTarget(origin: Vec2, target: Vec2, targetVelocity: Vec2, speed: Float): Vec2 {
        if (speed <= 0f) return target
        var predicted = target
        repeat(2) {
            val flightTime = (predicted - origin).length / speed
            predicted = target + targetVelocity * flightTime
        }
        return predicted
    }

    /** Where a round launched from [origin] at [velocity] will be after [time] seconds. */
    fun positionAt(origin: Vec2, velocity: Vec2, gravityScale: Float, time: Float): Vec2 = Vec2(
        origin.x + velocity.x * time,
        origin.y + velocity.y * time + 0.5f * GameConfig.GRAVITY * gravityScale * time * time,
    )
}
