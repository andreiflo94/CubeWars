package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.combat.WeakSpots
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.effect.ParticleEffects
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.Particle
import com.heixss.cubewars.domain.model.ParticleTint
import com.heixss.cubewars.domain.model.PlayerCube
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.SimulationCursor
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.cursor
import kotlin.math.sign

/**
 * The only place where anything loses health.
 *
 * Concentrating every damage source here means scoring, death, knockback, invulnerability, weak
 * spots and screen shake all have exactly one implementation, and no other stage has to remember to
 * keep them consistent.
 *
 * Every hostile archetype has one [EnemyWeakness], and this is where a hit is measured against it.
 * The check is deliberately a function of the *shot* rather than of the shooter — where on the cube
 * it landed, how far it had flown, which way it was falling, whether the player's feet were on the
 * ground when it left the barrel — so the answer to a crowd is a change in how you fight rather than
 * a change in what you hold down.
 */
class ResolveCombatUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        val cursor = state.cursor()
        val events = mutableListOf<GameEvent>()
        val particles = mutableListOf<Particle>()

        var enemies = state.enemies
        var projectiles = state.projectiles
        var player = state.player
        var score = state.score
        var kills = 0
        var shake = state.camera.shake

        // --- Player bullets versus hostile cubes -----------------------------------------------
        val playerBullets = projectiles.filter { it.owner == ProjectileOwner.PLAYER }
        if (playerBullets.isNotEmpty() && enemies.isNotEmpty()) {
            val spentBulletIds = HashSet<Long>()
            val survivingEnemies = ArrayList<EnemyCube>(enemies.size)

            for (enemy in enemies) {
                var current = enemy
                var struck = false
                var foundWeakSpot = false
                if (!current.isMaterialising) {
                    for (bullet in playerBullets) {
                        if (bullet.id in spentBulletIds) continue
                        if (!bullet.bounds.overlaps(current.bounds)) continue

                        spentBulletIds += bullet.id
                        val weakSpot = WeakSpots.isWeakSpot(current, bullet)
                        val damage = damageFor(current, bullet, weakSpot)

                        if (damage <= 0) {
                            // Turned aside by the shield. The round is still spent — the player has
                            // to answer the shield, not out-shoot it.
                            particles += ParticleEffects.impact(cursor, bullet.position, bullet.velocity.normalized())
                            events += GameEvent.EnemyDeflected(bullet.position)
                            continue
                        }

                        struck = true
                        foundWeakSpot = foundWeakSpot || weakSpot
                        current = current.copy(
                            health = current.health - damage,
                            hitFlashTimer = GameConfig.HIT_FLASH_TIME,
                            weakSpotFlashTimer = if (weakSpot) {
                                GameConfig.WEAK_SPOT_FLASH_TIME
                            } else {
                                current.weakSpotFlashTimer
                            },
                            // Heavy cubes plant their feet; a hit staggers everything else.
                            velocity = if (current.archetype.has(EnemyPower.UNFLINCHING)) {
                                current.velocity
                            } else {
                                current.velocity.withX(current.velocity.x + sign(bullet.velocity.x) * 42f)
                            },
                        )
                        if (current.health <= 0) break
                    }
                }

                // Finding the weak spot is worth something whether or not it was the killing blow;
                // paying it out here rather than on the kill is what makes it read as a reward for
                // the shot the player took rather than for the cube they happened to finish.
                if (foundWeakSpot) {
                    score += weaknessBonus(current)
                    events += GameEvent.EnemyWeakSpotHit(
                        archetype = current.archetype,
                        weakness = current.weakness,
                        at = current.center,
                    )
                }

                if (current.health <= 0) {
                    kills++
                    score += current.archetype.scoreValue
                    shake = (shake + 4.5f).coerceAtMost(GameConfig.CAMERA_MAX_SHAKE)
                    particles += destructionBurst(cursor, current)
                    events += GameEvent.EnemyDestroyed(
                        archetype = current.archetype,
                        at = current.center,
                        award = current.archetype.scoreValue,
                    )
                } else {
                    if (struck) {
                        particles += ParticleEffects.burst(
                            cursor = cursor,
                            origin = current.center,
                            count = if (foundWeakSpot) 11 else 5,
                            tint = if (foundWeakSpot) ParticleTint.WEAK_SPOT else ParticleTint.IMPACT,
                            minSpeed = 40f,
                            maxSpeed = if (foundWeakSpot) 190f else 120f,
                        )
                        events += GameEvent.EnemyDamaged(current.center)
                        shake = (shake + if (foundWeakSpot) 2.8f else 1.2f)
                            .coerceAtMost(GameConfig.CAMERA_MAX_SHAKE)
                    }
                    survivingEnemies += current
                }
            }

            enemies = survivingEnemies
            if (spentBulletIds.isNotEmpty()) {
                projectiles = projectiles.filterNot { it.id in spentBulletIds }
            }
        }

        // --- Cubes that fell out of the world ---------------------------------------------------
        // The pits kill hostile cubes too, and nothing else takes them off the board: a cube shot
        // off a ledge would otherwise fall forever, and the wave director waits for an empty arena
        // before it will end a wave — one stray cube and the level can never be completed.
        // No debris: it would be spawned far below the kill plane where nobody can see it.
        if (enemies.any { it.position.y < GameConfig.KILL_PLANE_Y }) {
            val (fallen, standing) = enemies.partition { it.position.y < GameConfig.KILL_PLANE_Y }
            enemies = standing
            for (enemy in fallen) {
                kills++
                score += enemy.archetype.scoreValue
                events += GameEvent.EnemyDestroyed(
                    archetype = enemy.archetype,
                    at = enemy.center,
                    award = enemy.archetype.scoreValue,
                )
            }
        }

        // --- Hostile bullets and bodies versus the player ---------------------------------------
        if (player.isAlive) {
            val incoming = projectiles.firstOrNull { hostile ->
                hostile.owner == ProjectileOwner.ENEMY && hostile.bounds.overlaps(player.bounds)
            }
            val toucher = enemies.firstOrNull { enemy ->
                !enemy.isMaterialising && enemy.bounds.overlaps(player.bounds)
            }

            val damage = when {
                incoming != null -> incoming.damage
                toucher != null -> toucher.archetype.contactDamage
                else -> 0
            }
            val source: Vec2? = incoming?.position ?: toucher?.center

            if (incoming != null) {
                projectiles = projectiles.filterNot { it.id == incoming.id }
            }

            if (damage > 0 && !player.isInvulnerable && source != null) {
                player = applyPlayerDamage(player, damage, source)
                particles += ParticleEffects.burst(
                    cursor = cursor,
                    origin = player.bounds.center,
                    count = 8,
                    tint = ParticleTint.PLAYER,
                    minSpeed = 60f,
                    maxSpeed = 165f,
                )
                shake = (shake + 5.5f).coerceAtMost(GameConfig.CAMERA_MAX_SHAKE)
                events += GameEvent.PlayerDamaged(player.health)
            }
        }

        // --- Death ------------------------------------------------------------------------------
        var status = state.status
        if (player.health <= 0 && status == GameStatus.RUNNING) {
            status = GameStatus.GAME_OVER
            events += GameEvent.PlayerDestroyed
            shake = GameConfig.CAMERA_MAX_SHAKE
            particles += ParticleEffects.burst(
                cursor = cursor,
                origin = player.bounds.center,
                count = 26,
                tint = ParticleTint.PLAYER,
                minSpeed = 70f,
                maxSpeed = 260f,
                minSize = 2f,
                maxSize = 5f,
                minLife = 0.5f,
                maxLife = 1.3f,
                upwardBias = 60f,
            )
        }

        return cursor.commit(
            state.copy(
                status = status,
                player = player,
                enemies = enemies,
                projectiles = projectiles,
                particles = state.particles + particles,
                camera = state.camera.copy(shake = shake),
                score = score,
                wave = state.wave.copy(
                    killsThisWave = state.wave.killsThisWave + kills,
                    totalKills = state.wave.totalKills + kills,
                ),
                frameEvents = state.frameEvents + events,
            ),
        )
    }

    /**
     * What one round actually takes off a cube.
     *
     * A weak-spot hit multiplies and ignores plating entirely — that is the whole point of it. Short
     * of that, a shield turns a flat frontal shot aside completely, which is only ever worth
     * complaining about until the player notices the shield has no lid.
     */
    private fun damageFor(enemy: EnemyCube, bullet: Projectile, weakSpot: Boolean): Int = when {
        weakSpot -> bullet.damage * GameConfig.WEAKNESS_DAMAGE_MULTIPLIER
        WeakSpots.isDeflected(enemy, bullet) -> 0
        else -> bullet.damage
    }

    private fun weaknessBonus(enemy: EnemyCube): Int =
        (enemy.archetype.scoreValue * GameConfig.WEAKNESS_SCORE_BONUS).toInt()

    private fun applyPlayerDamage(player: PlayerCube, damage: Int, source: Vec2): PlayerCube {
        val awayFromSource = if (player.bounds.centerX < source.x) -1f else 1f
        return player.copy(
            health = (player.health - damage).coerceAtLeast(0),
            velocity = Vec2(
                awayFromSource * GameConfig.PLAYER_KNOCKBACK,
                player.velocity.y.coerceAtLeast(120f),
            ),
            invulnerabilityTimer = GameConfig.PLAYER_INVULNERABILITY_TIME,
            hitFlashTimer = GameConfig.HIT_FLASH_TIME,
            squash = Vec2(1.3f, 0.7f),
        )
    }

    private fun destructionBurst(cursor: SimulationCursor, enemy: EnemyCube): List<Particle> =
        ParticleEffects.burst(
            cursor = cursor,
            origin = enemy.center,
            count = (enemy.size / 1.6f).toInt().coerceIn(8, 20),
            tint = ParticleTint.ENEMY,
            minSpeed = 55f,
            maxSpeed = 210f,
            minSize = 2f,
            maxSize = enemy.size / 4f,
            minLife = 0.35f,
            maxLife = 0.95f,
            upwardBias = 40f,
        )
}
