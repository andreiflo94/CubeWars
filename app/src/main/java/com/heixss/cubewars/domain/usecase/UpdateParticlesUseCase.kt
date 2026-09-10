package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.util.countdown
import kotlin.math.exp

/**
 * Ages the debris field.
 *
 * Particles never influence gameplay, so this stage is free to drop the oldest ones once the field
 * exceeds [GameConfig.MAX_PARTICLES] — a hard bound on the per-frame allocation cost of the state.
 */
class UpdateParticlesUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        if (state.particles.isEmpty()) return state

        val survivors = ArrayList<Particle>(state.particles.size)
        for (particle in state.particles) {
            val remainingLife = countdown(particle.remainingLife, dt)
            if (remainingLife <= 0f) continue

            // Exponential drag keeps debris from sliding forever without needing a ground check.
            val damping = exp(-particle.drag * dt)
            val velocity = particle.velocity.copy(
                x = particle.velocity.x * damping,
                y = particle.velocity.y * damping +
                    GameConfig.GRAVITY * GameConfig.PARTICLE_GRAVITY_SCALE * dt,
            )
            survivors += particle.copy(
                position = particle.position + velocity * dt,
                velocity = velocity,
                remainingLife = remainingLife,
                rotation = particle.rotation + particle.angularVelocity * dt,
            )
        }

        val trimmed = if (survivors.size > GameConfig.MAX_PARTICLES) {
            survivors.subList(survivors.size - GameConfig.MAX_PARTICLES, survivors.size).toList()
        } else {
            survivors
        }

        return state.copy(particles = trimmed)
    }
}
