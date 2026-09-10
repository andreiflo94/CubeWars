package com.heixss.cubewars.domain.model

/** Who fired a bullet decides who it can hurt. */
enum class ProjectileOwner {
    PLAYER,
    ENEMY,
}

/** A cube small enough to hurt. */
data class Projectile(
    val id: Long,
    val owner: ProjectileOwner,
    val position: Vec2,
    val velocity: Vec2,
    val size: Float,
    val damage: Int,
    val remainingLife: Float,
    /**
     * How hard this round falls, as a fraction of world gravity.
     *
     * Every bullet carries its own figure rather than reading one constant, which is the whole of the
     * difference between a sniper's flat round, the player's gentle drop and a mortar shell that has
     * to be lobbed over cover. Zero restores straight-line flight.
     */
    val gravityScale: Float = 0f,
    /** Seconds since spawn; drives the muzzle-flash stretch in the renderer. */
    val animationTime: Float = 0f,
    /**
     * Whether the player was off the ground when this round left the barrel.
     *
     * Carried on the bullet rather than read at impact because the weakness it feeds
     * ([EnemyWeakness.AIRBORNE]) is a statement about the shot, not about where the player happened
     * to be a second later when it landed.
     */
    val firedAirborne: Boolean = false,
    /** Where the round was fired from, so range-based weak spots can be judged at impact. */
    val origin: Vec2 = position,
) {
    val bounds: Aabb get() = Aabb.centeredSquare(position, size)

    /** How far this round has travelled from the muzzle. */
    val travelled: Float get() = (position - origin).length
}
