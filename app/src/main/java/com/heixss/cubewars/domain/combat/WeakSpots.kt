package com.heixss.cubewars.domain.combat

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.EnemyWeakness
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.Projectile

/**
 * Whether a shot found the one thing a cube cannot take.
 *
 * This lives apart from `ResolveCombatUseCase` because two very different callers need the same
 * answer: combat, to multiply the damage, and the renderer, to draw the weak spot on the cube before
 * the player has to guess. Keeping the rule in one place is what guarantees the marker the player
 * aims at and the band the bullet is measured against are the same band.
 *
 * Every predicate is a pure function of the cube and the round that hit it. Nothing here reads the
 * clock, the level, or the player.
 */
object WeakSpots {

    /** Does this round land where this archetype cannot afford to be hit? */
    fun isWeakSpot(enemy: EnemyCube, bullet: Projectile): Boolean {
        val band = enemy.size * GameConfig.WEAK_SPOT_BAND
        return when (enemy.weakness) {
            EnemyWeakness.NONE -> false
            EnemyWeakness.HEADSHOT -> bullet.position.y >= enemy.bounds.top - band
            EnemyWeakness.UNDERBELLY -> bullet.position.y <= enemy.bounds.bottom + band
            EnemyWeakness.BACKSTAB -> isBehind(enemy, bullet)
            EnemyWeakness.PLUNGING_FIRE -> isPlunging(bullet)
            EnemyWeakness.POINT_BLANK -> bullet.travelled <= GameConfig.POINT_BLANK_RANGE
            EnemyWeakness.LONG_SHOT -> bullet.travelled >= GameConfig.LONG_SHOT_RANGE
            EnemyWeakness.AIRBORNE -> bullet.firedAirborne
        }
    }

    /**
     * Turned aside rather than absorbed.
     *
     * A shield covers the face of a cube, not the top of it, so a round arriving on the way down
     * goes over it. That is the intended answer to a charger: lob one, or get behind it.
     */
    fun isDeflected(enemy: EnemyCube, bullet: Projectile): Boolean =
        enemy.archetype.has(EnemyPower.FRONTAL_SHIELD) &&
            !isBehind(enemy, bullet) &&
            !isPlunging(bullet)

    /** True when the round came in on the side the cube is not looking at. */
    fun isBehind(enemy: EnemyCube, bullet: Projectile): Boolean {
        val fromTheRight = bullet.position.x > enemy.center.x
        return if (enemy.facing == Facing.RIGHT) !fromTheRight else fromTheRight
    }

    /** True when the round is already on its way down hard enough to be called plunging fire. */
    fun isPlunging(bullet: Projectile): Boolean =
        bullet.velocity.y <= -GameConfig.PLUNGING_FIRE_SPEED
}
