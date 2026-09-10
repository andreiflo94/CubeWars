package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.effect.ParticleEffects
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.cursor
import com.heixss.cubewars.domain.physics.WorldPhysics
import com.heixss.cubewars.domain.util.countdown

/**
 * Flies every bullet forward and retires the ones that expired, left the arena, or hit a wall.
 *
 * Bullets are cubes, and cubes fall: each one is integrated under its own
 * [Projectile.gravityScale] rather than travelling in a straight line, which is what turns a mortar
 * shell into something that arcs over cover and a sniper's round into something that does not. A
 * scale of zero costs nothing and restores the old straight flight exactly.
 *
 * Bullet-versus-cube hits are deliberately *not* handled here — that is combat, and it lives in
 * [ResolveCombatUseCase]. This stage only knows about bullets and geometry.
 */
class UpdateProjectilesUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        if (state.projectiles.isEmpty()) return state

        val cursor = state.cursor()
        val survivors = ArrayList<Projectile>(state.projectiles.size)
        val impacts = mutableListOf<Particle>()

        for (projectile in state.projectiles) {
            val velocity = if (projectile.gravityScale == 0f) {
                projectile.velocity
            } else {
                WorldPhysics.applyGravity(projectile.velocity, dt, projectile.gravityScale)
            }
            val moved = projectile.copy(
                position = projectile.position + velocity * dt,
                velocity = velocity,
                remainingLife = countdown(projectile.remainingLife, dt),
                animationTime = projectile.animationTime + dt,
            )

            if (moved.remainingLife <= 0f || isOutsideArena(moved, state)) continue

            if (WorldPhysics.collidesWithSolid(moved.bounds, state.level)) {
                impacts += ParticleEffects.impact(cursor, moved.position, moved.velocity.normalized())
                continue
            }

            survivors += moved
        }

        return cursor.commit(
            state.copy(
                projectiles = survivors,
                particles = state.particles + impacts,
            ),
        )
    }

    private fun isOutsideArena(projectile: Projectile, state: GameState): Boolean {
        val margin = 32f
        return projectile.position.x < -margin ||
            projectile.position.x > state.level.width + margin ||
            projectile.position.y < GameConfig.KILL_PLANE_Y ||
            projectile.position.y > state.level.height + margin
    }
}
