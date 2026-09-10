package com.heixss.cubewars.domain.effect

import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.ParticleTint
import com.heixss.cubewars.domain.model.SimulationCursor
import com.heixss.cubewars.domain.model.Vec2
import kotlin.math.PI

/**
 * Recipes for the debris the game throws around.
 *
 * Particles are cosmetic, but they are still simulation state: they are spawned from the same
 * deterministic cursor as everything else, so two runs fed the same inputs sparkle identically.
 */
object ParticleEffects {

    private const val TAU = (PI * 2.0).toFloat()

    /** A ring of debris flying out of [origin] — used for hits and deaths. */
    fun burst(
        cursor: SimulationCursor,
        origin: Vec2,
        count: Int,
        tint: ParticleTint,
        minSpeed: Float,
        maxSpeed: Float,
        minSize: Float = 1.5f,
        maxSize: Float = 3.5f,
        minLife: Float = 0.25f,
        maxLife: Float = 0.6f,
        upwardBias: Float = 0f,
    ): List<Particle> = List(count) {
        val angle = cursor.range(0f, TAU)
        val speed = cursor.range(minSpeed, maxSpeed)
        val life = cursor.range(minLife, maxLife)
        Particle(
            id = cursor.id(),
            position = origin,
            velocity = Vec2(1f, 0f).rotated(angle) * speed + Vec2(0f, upwardBias),
            size = cursor.range(minSize, maxSize),
            remainingLife = life,
            totalLife = life,
            tint = tint,
            rotation = cursor.range(0f, TAU),
            angularVelocity = cursor.signed(9f),
        )
    }

    /** A short cone of sparks leaving a gun barrel. */
    fun muzzleFlash(
        cursor: SimulationCursor,
        origin: Vec2,
        direction: Vec2,
        count: Int = 4,
    ): List<Particle> = List(count) {
        val life = cursor.range(0.06f, 0.16f)
        Particle(
            id = cursor.id(),
            position = origin,
            velocity = direction.rotated(cursor.signed(0.4f)) * cursor.range(60f, 150f),
            size = cursor.range(1.5f, 3f),
            remainingLife = life,
            totalLife = life,
            tint = ParticleTint.MUZZLE,
            angularVelocity = cursor.signed(6f),
            drag = 5f,
        )
    }

    /** A low puff kicked up by a jump or a landing. */
    fun groundPuff(
        cursor: SimulationCursor,
        origin: Vec2,
        spreadWidth: Float,
        count: Int = 5,
        strength: Float = 1f,
    ): List<Particle> = List(count) {
        val life = cursor.range(0.18f, 0.36f)
        Particle(
            id = cursor.id(),
            position = Vec2(origin.x + cursor.signed(spreadWidth / 2f), origin.y),
            velocity = Vec2(cursor.signed(45f * strength), cursor.range(10f, 55f) * strength),
            size = cursor.range(1.5f, 3f),
            remainingLife = life,
            totalLife = life,
            tint = ParticleTint.DUST,
            angularVelocity = cursor.signed(4f),
            drag = 3.4f,
        )
    }

    /** Sparks where a bullet met geometry. */
    fun impact(cursor: SimulationCursor, origin: Vec2, incoming: Vec2): List<Particle> {
        val bounce = Vec2(-incoming.x, -incoming.y).normalized()
        return List(5) {
            val life = cursor.range(0.1f, 0.26f)
            Particle(
                id = cursor.id(),
                position = origin,
                velocity = bounce.rotated(cursor.signed(1.1f)) * cursor.range(50f, 140f),
                size = cursor.range(1f, 2.5f),
                remainingLife = life,
                totalLife = life,
                tint = ParticleTint.IMPACT,
                angularVelocity = cursor.signed(8f),
                drag = 4f,
            )
        }
    }
}
