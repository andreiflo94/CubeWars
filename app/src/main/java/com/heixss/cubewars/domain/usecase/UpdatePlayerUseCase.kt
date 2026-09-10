package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.effect.ParticleEffects
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.cursor
import com.heixss.cubewars.domain.physics.WorldPhysics
import com.heixss.cubewars.domain.util.approach
import com.heixss.cubewars.domain.util.countdown
import com.heixss.cubewars.domain.util.smoothTowards
import kotlin.math.abs

/**
 * Drives the player cube: acceleration, jumping, aiming and firing.
 *
 * The feel comes from four forgiveness mechanics that a naive implementation leaves out — coyote
 * time, jump buffering, a variable jump height and a second jump in the air — all of which live in
 * the state rather than in hidden renderer variables.
 */
class UpdatePlayerUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        val player = state.player
        if (!player.isAlive) return state

        val input = state.input
        val cursor = state.cursor()
        val events = mutableListOf<GameEvent>()
        val spawnedBullets = mutableListOf<Projectile>()
        val spawnedParticles = mutableListOf<Particle>()

        // --- Horizontal steering ------------------------------------------------------------
        var velocity = player.velocity
        val acceleration =
            if (player.onGround) GameConfig.PLAYER_GROUND_ACCELERATION else GameConfig.PLAYER_AIR_ACCELERATION
        val friction =
            if (player.onGround) GameConfig.PLAYER_GROUND_FRICTION else GameConfig.PLAYER_AIR_FRICTION
        velocity = if (input.isMoving) {
            velocity.withX(
                approach(velocity.x, input.moveAxis * GameConfig.PLAYER_MAX_SPEED, acceleration * dt),
            )
        } else {
            velocity.withX(approach(velocity.x, 0f, friction * dt))
        }

        // --- Jumping --------------------------------------------------------------------------
        var coyoteTimer = if (player.onGround) GameConfig.COYOTE_TIME else countdown(player.coyoteTimer, dt)
        var jumpBufferTimer = countdown(player.jumpBufferTimer, dt)
        // Standing on anything at all is what refills the air jumps; nothing else does.
        var airJumpsRemaining =
            if (player.onGround) GameConfig.PLAYER_AIR_JUMPS else player.airJumpsRemaining
        var squash = player.squash

        // A press is spent on the ground jump when one is available, and only otherwise on an air
        // jump — which is why a player mashing jump on approach still lands into a full-height
        // launch rather than burning the second jump a few units above the floor.
        if (jumpBufferTimer > 0f && coyoteTimer > 0f) {
            velocity = velocity.withY(GameConfig.PLAYER_JUMP_VELOCITY)
            jumpBufferTimer = 0f
            coyoteTimer = 0f
            squash = Vec2(0.76f, 1.26f)
            spawnedParticles += ParticleEffects.groundPuff(
                cursor = cursor,
                origin = Vec2(player.position.x + player.size / 2f, player.position.y),
                spreadWidth = player.size,
                count = 4,
                strength = 0.8f,
            )
        } else if (jumpBufferTimer > 0f && airJumpsRemaining > 0) {
            // The second jump replaces whatever the cube was doing rather than adding to it, so a
            // long fall and a clipped hop both recover to exactly the same arc.
            velocity = velocity.withY(GameConfig.PLAYER_AIR_JUMP_VELOCITY)
            jumpBufferTimer = 0f
            airJumpsRemaining -= 1
            squash = Vec2(0.7f, 1.32f)
            // A ring of dust under the cube's feet, hanging in mid-air, is what tells the player the
            // second jump was the thing that just happened.
            spawnedParticles += ParticleEffects.groundPuff(
                cursor = cursor,
                origin = Vec2(player.position.x + player.size / 2f, player.position.y),
                spreadWidth = player.size * 1.6f,
                count = 7,
                strength = 0.55f,
            )
            events += GameEvent.PlayerAirJumped
        }

        // The variable jump height lives on the button's release edge, in ApplyIntentUseCase —
        // clipping here on the held/not-held level would also gut a buffered jump that launches
        // after the player has already let go.

        velocity = WorldPhysics.applyGravity(velocity, dt)

        // --- Collision ------------------------------------------------------------------------
        val wasAirborne = !player.onGround
        val move = WorldPhysics.move(
            position = player.position,
            size = player.size,
            velocity = velocity,
            dt = dt,
            level = state.level,
        )
        velocity = move.velocity
        var position = move.position

        if (wasAirborne && move.onGround) {
            airJumpsRemaining = GameConfig.PLAYER_AIR_JUMPS
            squash = Vec2(1.28f, 0.74f)
            spawnedParticles += ParticleEffects.groundPuff(
                cursor = cursor,
                origin = Vec2(position.x + player.size / 2f, position.y),
                spreadWidth = player.size * 1.4f,
                count = 6,
            )
        }

        // --- Falling out of the world ---------------------------------------------------------
        var health = player.health
        var invulnerabilityTimer = countdown(player.invulnerabilityTimer, dt)
        var hitFlashTimer = countdown(player.hitFlashTimer, dt)
        if (position.y < GameConfig.KILL_PLANE_Y) {
            health -= 1
            position = state.level.playerSpawn
            velocity = Vec2.ZERO
            airJumpsRemaining = GameConfig.PLAYER_AIR_JUMPS
            invulnerabilityTimer = GameConfig.PLAYER_INVULNERABILITY_TIME
            hitFlashTimer = GameConfig.HIT_FLASH_TIME
            // The death transition itself belongs to ResolveCombatUseCase, which owns every way
            // the run can end.
            events += GameEvent.PlayerDamaged(health)
        }

        // --- Aiming and firing ------------------------------------------------------------------
        // A free aim with any sideways lean turns the cube: pointing the gun is a stronger statement
        // about which way you are looking than the leg you happen to be walking on.
        val facing = when {
            input.aim != null && abs(input.aim.x) > GameConfig.AIM_FACING_THRESHOLD ->
                if (input.aim.x > 0f) Facing.RIGHT else Facing.LEFT
            input.moveAxis > 0f -> Facing.RIGHT
            input.moveAxis < 0f -> Facing.LEFT
            else -> player.facing
        }
        val aim = input.aim ?: Vec2(facing.sign, 0f)

        var fireCooldown = countdown(player.fireCooldown, dt)
        val aimedPlayer = player.copy(position = position, facing = facing, aim = aim)
        if (input.fireHeld && fireCooldown <= 0f && health > 0) {
            fireCooldown = GameConfig.PLAYER_FIRE_INTERVAL
            val muzzle = aimedPlayer.muzzle
            val direction = aimedPlayer.aim
            spawnedBullets += Projectile(
                id = cursor.id(),
                owner = ProjectileOwner.PLAYER,
                position = muzzle,
                velocity = direction * GameConfig.PLAYER_BULLET_SPEED,
                size = GameConfig.PLAYER_BULLET_SIZE,
                damage = GameConfig.PLAYER_BULLET_DAMAGE,
                remainingLife = GameConfig.BULLET_LIFETIME,
                gravityScale = GameConfig.PLAYER_BULLET_GRAVITY_SCALE,
                // Both stamped at the muzzle: whether this was an air shot, and how far it has come
                // by the time it lands, are the two things weak spots are judged on.
                firedAirborne = !move.onGround,
                origin = muzzle,
            )
            spawnedParticles += ParticleEffects.muzzleFlash(cursor, muzzle, direction)
            // A touch of recoil sells the shot without making the player hard to steer.
            velocity = velocity.withX(velocity.x - direction.x * 26f)
            squash = Vec2(1.14f, 0.9f)
            events += GameEvent.PlayerFired
        }

        // --- Cosmetic timers ---------------------------------------------------------------------
        val recoveredSquash = Vec2(
            smoothTowards(squash.x, 1f, GameConfig.SQUASH_RECOVERY, dt),
            smoothTowards(squash.y, 1f, GameConfig.SQUASH_RECOVERY, dt),
        )

        val updated = player.copy(
            position = position,
            velocity = velocity,
            facing = facing,
            aim = aim,
            health = health,
            onGround = move.onGround,
            coyoteTimer = coyoteTimer,
            jumpBufferTimer = jumpBufferTimer,
            airJumpsRemaining = airJumpsRemaining,
            fireCooldown = fireCooldown,
            invulnerabilityTimer = invulnerabilityTimer,
            hitFlashTimer = hitFlashTimer,
            squash = recoveredSquash,
            animationTime = player.animationTime + dt,
        )

        return cursor.commit(
            state.copy(
                player = updated,
                projectiles = state.projectiles + spawnedBullets,
                particles = state.particles + spawnedParticles,
                frameEvents = state.frameEvents + events,
            ),
        )
    }
}
