package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.effect.ParticleEffects
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.MovementStyle
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.SimulationCursor
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.cursor
import com.heixss.cubewars.domain.physics.Ballistics
import com.heixss.cubewars.domain.physics.WorldPhysics
import com.heixss.cubewars.domain.util.approach
import com.heixss.cubewars.domain.util.countdown
import kotlin.math.abs

/**
 * Steers every hostile cube and decides when it pulls the trigger.
 *
 * Nothing here switches on the archetype. Movement reads [MovementStyle] — one branch per style, and
 * five styles cover eight archetypes — and everything a cube does *well* is a member of its
 * [EnemyPower] set, asked for by name. Giving the grunt a marksman's aim is a one-word change to its
 * power set, not a new branch in this file.
 *
 * Firing is ballistic. Every round now falls, so pointing at the player is no longer the same thing
 * as hitting them: each shot is solved through [Ballistics] for the launch angle that actually
 * arrives. That is the difference between a turret whose flat rounds barely dip and a mortar that
 * drops shells behind the cover you are hiding under.
 */
class UpdateEnemiesUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        if (state.enemies.isEmpty()) return state

        val cursor = state.cursor()
        val events = mutableListOf<GameEvent>()
        val spawnedBullets = mutableListOf<Projectile>()
        val spawnedParticles = mutableListOf<Particle>()
        val playerCenter = state.player.bounds.center
        val playerIsTarget = state.player.isAlive

        val updated = state.enemies.map { enemy ->
            val animationTime = enemy.animationTime + dt
            val settling = enemy.copy(
                animationTime = animationTime,
                hitFlashTimer = countdown(enemy.hitFlashTimer, dt),
                weakSpotFlashTimer = countdown(enemy.weakSpotFlashTimer, dt),
                stateTimer = countdown(enemy.stateTimer, dt),
                fireCooldown = countdown(enemy.fireCooldown, dt),
            )

            // While materialising an enemy is inert: it falls, but it does not act.
            val steered = if (settling.isMaterialising || !playerIsTarget) {
                settling
            } else {
                steer(settling, state, playerCenter, dt, cursor, spawnedBullets, spawnedParticles, events)
            }

            integrate(steered, state, dt)
        }

        return cursor.commit(
            state.copy(
                enemies = updated,
                projectiles = state.projectiles + spawnedBullets,
                particles = state.particles + spawnedParticles,
                frameEvents = state.frameEvents + events,
            ),
        )
    }

    private fun steer(
        enemy: EnemyCube,
        state: GameState,
        playerCenter: Vec2,
        dt: Float,
        cursor: SimulationCursor,
        bullets: MutableList<Projectile>,
        particles: MutableList<Particle>,
        events: MutableList<GameEvent>,
    ): EnemyCube {
        val archetype = enemy.archetype
        val toPlayer = playerCenter - enemy.center
        val distance = toPlayer.length
        val playerSide = if (toPlayer.x >= 0f) Facing.RIGHT else Facing.LEFT
        val inSight = distance <= archetype.sightRange

        val moved = when (archetype.movement) {
            MovementStyle.PATROL -> patrol(enemy, state, dt, faceTarget = inSight, targetSide = playerSide)
            MovementStyle.CHARGE -> charge(enemy, state, dt, committed = inSight, targetSide = playerSide)
            MovementStyle.ROOTED -> {
                val turn = traverse(enemy, if (inSight) playerSide else enemy.facing)
                enemy.copy(
                    facing = turn.facing,
                    stateTimer = turn.timer,
                    velocity = enemy.velocity.withX(approach(enemy.velocity.x, 0f, 400f * dt)),
                )
            }

            MovementStyle.HOP -> hop(enemy, playerSide, dt, cursor, particles)
            MovementStyle.HOVER -> hover(enemy, playerCenter, playerSide, dt)
        }

        val wantsToShoot = archetype.bulletsPerShot > 0 &&
            inSight &&
            abs(toPlayer.y) <= archetype.firingArc &&
            moved.fireCooldown <= 0f &&
            // A cube shoots down its own barrel, not over its shoulder. For the heavy weapons this
            // is what makes flanking work: get behind one and it has to come round before it can
            // answer, and coming round takes time it does not have.
            moved.facing == playerSide
        if (!wantsToShoot) return moved

        return fire(moved, state, playerCenter, cursor, bullets, particles, events)
    }

    /** The two halves of a turn: where the cube ends up looking, and how long until it may turn again. */
    private data class Traverse(val facing: Facing, val timer: Float)

    /**
     * Swings a heavy cube round toward [targetSide], slowly.
     *
     * The timer is reset for as long as the cube is already looking the right way and only counts
     * down while the player is behind it, so [EnemyArchetype.traverseTime] reads as "how long the
     * player gets to stand in the blind spot" — which is exactly what the backstab weakness is
     * asking them to earn.
     */
    private fun traverse(enemy: EnemyCube, targetSide: Facing): Traverse {
        val delay = enemy.archetype.traverseTime
        return when {
            enemy.facing == targetSide -> Traverse(targetSide, delay)
            enemy.stateTimer > 0f -> Traverse(enemy.facing, enemy.stateTimer)
            else -> Traverse(targetSide, delay)
        }
    }

    /**
     * Solves the arc, then puts the volley down it.
     *
     * A cube that cannot find an arc to the player simply does not shoot — the target is beyond what
     * its gun can throw, and firing anyway would only litter the arena with rounds that fall short.
     * Its cooldown is left alone, so it takes the shot the moment the player comes into range.
     */
    private fun fire(
        enemy: EnemyCube,
        state: GameState,
        playerCenter: Vec2,
        cursor: SimulationCursor,
        bullets: MutableList<Projectile>,
        particles: MutableList<Particle>,
        events: MutableList<GameEvent>,
    ): EnemyCube {
        val archetype = enemy.archetype
        val barrel = Vec2(
            enemy.position.x + enemy.size / 2f + enemy.facing.sign * (enemy.size / 2f),
            enemy.position.y + enemy.size * 0.5f,
        )

        // A marksman shoots where the player is going; everyone else shoots where they are.
        val target = if (archetype.has(EnemyPower.MARKSMAN)) {
            Ballistics.leadTarget(barrel, playerCenter, state.player.velocity, archetype.bulletSpeed)
        } else {
            playerCenter
        }

        val aim = Ballistics.launchDirection(
            origin = barrel,
            target = target,
            speed = archetype.bulletSpeed,
            gravityScale = archetype.bulletGravityScale,
            // A lobbed shell goes over the cover a flat one would bury itself in.
            lofted = archetype.has(EnemyPower.ARCING_SHOT),
        ) ?: return enemy

        val jitter = if (archetype.has(EnemyPower.STEADY_AIM)) 0.012f else 0.06f
        repeat(archetype.bulletsPerShot) { index ->
            val offset = if (archetype.bulletsPerShot == 1) {
                0f
            } else {
                -archetype.spread / 2f + archetype.spread * index / (archetype.bulletsPerShot - 1)
            }
            bullets += Projectile(
                id = cursor.id(),
                owner = ProjectileOwner.ENEMY,
                position = barrel,
                velocity = aim.rotated(offset + cursor.signed(jitter)) * archetype.bulletSpeed,
                size = 5f,
                damage = archetype.bulletDamage,
                remainingLife = GameConfig.BULLET_LIFETIME,
                gravityScale = archetype.bulletGravityScale,
                origin = barrel,
            )
        }
        particles += ParticleEffects.muzzleFlash(cursor, barrel, aim, count = 3)
        events += GameEvent.EnemyFired(archetype)
        return enemy.copy(fireCooldown = archetype.fireInterval)
    }

    /**
     * Walks back and forth, turning at walls and ledges, and squares up to the player on sight.
     *
     * Which way a cube *looks* and which way it *walks* are deliberately two different decisions. A
     * grunt holding the far lip of a pit turns to face the player and stands its ground: it will not
     * walk into the hole, but a cube that answered a pit by looking the other way would be no threat
     * at all — and, now that a shot only leaves the barrel it is pointed down, would never fire.
     */
    private fun patrol(
        enemy: EnemyCube,
        state: GameState,
        dt: Float,
        faceTarget: Boolean,
        targetSide: Facing,
    ): EnemyCube {
        var facing = enemy.facing
        if (faceTarget) {
            facing = targetSide
        } else if (enemy.onGround) {
            val blocked = WorldPhysics.isWallAhead(enemy.position, enemy.size, facing.sign, state.level) ||
                WorldPhysics.isLedgeAhead(enemy.position, enemy.size, facing.sign, state.level)
            if (blocked) facing = facing.flipped()
        }

        val wouldFall = WorldPhysics.isLedgeAhead(enemy.position, enemy.size, facing.sign, state.level)
        val wouldHitWall = WorldPhysics.isWallAhead(enemy.position, enemy.size, facing.sign, state.level)
        val target = if (wouldFall || wouldHitWall) 0f else facing.sign * enemy.archetype.moveSpeed
        return enemy.copy(
            facing = facing,
            velocity = enemy.velocity.withX(approach(enemy.velocity.x, target, 600f * dt)),
        )
    }

    /**
     * Patrols until it sees the player, then commits.
     *
     * A charger at full tilt is the one enemy the player cannot simply out-walk, which is what makes
     * its shielded front worth going around rather than shooting through. It still will not run off
     * a ledge — a charge that ends in a pit is not a threat, it is a gift.
     */
    private fun charge(
        enemy: EnemyCube,
        state: GameState,
        dt: Float,
        committed: Boolean,
        targetSide: Facing,
    ): EnemyCube {
        if (!committed) return patrol(enemy, state, dt, faceTarget = false, targetSide = targetSide)

        // Momentum, not indecision: a charger runs the way it is pointed and needs a beat to come
        // about, which is the window its shielded front cannot cover.
        val turn = traverse(enemy, targetSide)
        val heading = turn.facing
        val wouldFall = WorldPhysics.isLedgeAhead(enemy.position, enemy.size, heading.sign, state.level)
        val wouldHitWall = WorldPhysics.isWallAhead(enemy.position, enemy.size, heading.sign, state.level)
        val speed = if (wouldFall || wouldHitWall) 0f else heading.sign * enemy.archetype.moveSpeed
        return enemy.copy(
            facing = heading,
            stateTimer = turn.timer,
            velocity = enemy.velocity.withX(approach(enemy.velocity.x, speed, 900f * dt)),
        )
    }

    /** Bounces toward the player. With [EnemyPower.HIGH_JUMP] the bounce clears whole tiers. */
    private fun hop(
        enemy: EnemyCube,
        targetSide: Facing,
        dt: Float,
        cursor: SimulationCursor,
        particles: MutableList<Particle>,
    ): EnemyCube {
        // A high jumper steers hard in the air too, otherwise its long hops are trivially dodged.
        val airControl = when {
            enemy.onGround -> 700f
            enemy.archetype.has(EnemyPower.HIGH_JUMP) -> 420f
            else -> 180f
        }
        val target = targetSide.sign * enemy.archetype.moveSpeed
        var velocity = enemy.velocity.withX(approach(enemy.velocity.x, target, airControl * dt))
        var stateTimer = enemy.stateTimer

        if (enemy.onGround && stateTimer <= 0f) {
            velocity = velocity.withY(enemy.archetype.jumpVelocity)
            stateTimer = cursor.range(0.55f, 0.95f)
            particles += ParticleEffects.groundPuff(
                cursor = cursor,
                origin = Vec2(enemy.position.x + enemy.size / 2f, enemy.position.y),
                spreadWidth = enemy.size,
                count = 3,
                strength = 0.6f,
            )
        }

        return enemy.copy(facing = targetSide, velocity = velocity, stateTimer = stateTimer)
    }

    /**
     * Swims through the air toward the player, holding station a little above their head.
     *
     * The offset is what stops a hoverer from settling inside the player and turning into a contact
     * weapon; it wants to be somewhere the player has to jump to answer.
     */
    private fun hover(
        enemy: EnemyCube,
        playerCenter: Vec2,
        targetSide: Facing,
        dt: Float,
    ): EnemyCube {
        val station = Vec2(playerCenter.x, playerCenter.y + HOVER_STANDOFF)
        val toStation = station - enemy.center
        val speed = enemy.archetype.moveSpeed
        val desired = if (toStation.length <= 1f) Vec2.ZERO else toStation.normalized() * speed
        return enemy.copy(
            facing = targetSide,
            velocity = Vec2(
                approach(enemy.velocity.x, desired.x, 320f * dt),
                approach(enemy.velocity.y, desired.y, 320f * dt),
            ),
        )
    }

    /** Shared gravity + collision pass. */
    private fun integrate(enemy: EnemyCube, state: GameState, dt: Float): EnemyCube {
        val velocity = if (enemy.archetype.affectedByGravity) {
            WorldPhysics.applyGravity(enemy.velocity, dt)
        } else {
            enemy.velocity
        }
        val move = WorldPhysics.move(
            position = enemy.position,
            size = enemy.size,
            velocity = velocity,
            dt = dt,
            level = state.level,
        )
        return enemy.copy(
            position = move.position,
            velocity = move.velocity,
            onGround = move.onGround,
        )
    }

    private companion object {
        /** How far above the player a hovering cube tries to sit. */
        const val HOVER_STANDOFF = 34f
    }
}
