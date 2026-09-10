package com.heixss.cubewars.domain.model

/**
 * Which palette entry a particle draws with. The domain stays colour-blind: it names a role and the
 * presentation layer decides what that role looks like.
 */
enum class ParticleTint {
    PLAYER,
    ENEMY,
    MUZZLE,
    IMPACT,
    DUST,

    /** A shot that found a weak spot. Nothing else in the game is this colour. */
    WEAK_SPOT,
}

/** A short-lived cube of debris. Purely cosmetic — particles never affect the simulation. */
data class Particle(
    val id: Long,
    val position: Vec2,
    val velocity: Vec2,
    val size: Float,
    val remainingLife: Float,
    val totalLife: Float,
    val tint: ParticleTint,
    val rotation: Float = 0f,
    val angularVelocity: Float = 0f,
    /** Particles with drag settle instead of flying forever. */
    val drag: Float = 1.6f,
) {
    /** 1 when freshly spawned, 0 at the moment of death. */
    val lifeFraction: Float get() = (remainingLife / totalLife).coerceIn(0f, 1f)
}
