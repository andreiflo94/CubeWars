package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.enemyAt
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.EnemyWeakness
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.usecase.ResolveCombatUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each archetype has one thing it cannot take, and finding it has to be worth the trouble.
 *
 * These pin down the half of the enemy design that the player is supposed to *discover*: that the
 * answer to a turret is to get behind it, to a sniper is to close the distance, to a brute is to
 * shoot upward — and that none of those cost anything if the shot lands anywhere else.
 */
class WeakSpotTest {

    private val resolveCombat = ResolveCombatUseCase()

    /** A player round placed exactly where the test wants it, travelling however it likes. */
    private fun shot(
        at: Vec2,
        velocity: Vec2 = Vec2(GameConfig.PLAYER_BULLET_SPEED, 0f),
        travelled: Float = 100f,
        airborne: Boolean = false,
        damage: Int = GameConfig.PLAYER_BULLET_DAMAGE,
    ): Projectile = Projectile(
        id = 700L,
        owner = ProjectileOwner.PLAYER,
        position = at,
        velocity = velocity,
        size = GameConfig.PLAYER_BULLET_SIZE,
        damage = damage,
        remainingLife = GameConfig.BULLET_LIFETIME,
        gravityScale = GameConfig.PLAYER_BULLET_GRAVITY_SCALE,
        firedAirborne = airborne,
        // Range weaknesses are judged on how far the round has come, so place the muzzle behind it.
        origin = at - velocity.normalized() * travelled,
    )

    /** Health lost by [enemy] when [projectile] lands on it. */
    private fun damageDone(enemy: EnemyCube, projectile: Projectile): Int {
        val resolved = resolveCombat(
            running().copy(enemies = listOf(enemy), projectiles = listOf(projectile)),
            STEP,
        )
        val survivor = resolved.enemies.firstOrNull()
        return if (survivor == null) enemy.health else enemy.health - survivor.health
    }

    @Test
    fun `every archetype has a weakness, and no two share a colour of behaviour by accident`() {
        // A cube with nothing to find is a cube there is only one way to fight.
        assertTrue(
            "every hostile archetype needs a weak spot",
            EnemyArchetype.entries.none { it.weakness == EnemyWeakness.NONE },
        )
        // And every one of them has to bring something of its own to the fight.
        val distinctive = EnemyArchetype.entries.count { it.powers.isNotEmpty() }
        assertTrue("expected most archetypes to have a power: $distinctive", distinctive >= 6)
    }

    @Test
    fun `a shot into the head of a grunt hits for triple`() {
        val grunt = enemyAt(200f, 32f, EnemyArchetype.GRUNT, health = 9)
        val head = Vec2(grunt.center.x, grunt.bounds.top - 1f)
        val body = Vec2(grunt.center.x, grunt.center.y)

        assertEquals(GameConfig.PLAYER_BULLET_DAMAGE, damageDone(grunt, shot(body)))
        assertEquals(
            GameConfig.PLAYER_BULLET_DAMAGE * GameConfig.WEAKNESS_DAMAGE_MULTIPLIER,
            damageDone(grunt, shot(head)),
        )
    }

    @Test
    fun `a brute is soft underneath and solid everywhere else`() {
        val brute = enemyAt(200f, 32f, EnemyArchetype.BRUTE, health = 12)
        val belly = Vec2(brute.center.x, brute.bounds.bottom + 1f)

        assertEquals(1, damageDone(brute, shot(Vec2(brute.center.x, brute.center.y), damage = 1)))
        assertEquals(
            GameConfig.WEAKNESS_DAMAGE_MULTIPLIER,
            damageDone(brute, shot(belly, damage = 1)),
        )
    }

    @Test
    fun `a turret has to be flanked, and turning to face closes the opening`() {
        val facingRight = enemyAt(200f, 32f, EnemyArchetype.TURRET, health = 12)
            .copy(facing = Facing.RIGHT)
        val fromBehind = Vec2(facingRight.center.x - 4f, facingRight.center.y)
        val fromInFront = Vec2(facingRight.center.x + 4f, facingRight.center.y)

        assertEquals(
            GameConfig.WEAKNESS_DAMAGE_MULTIPLIER,
            damageDone(facingRight, shot(fromBehind, damage = 1)),
        )
        assertEquals(1, damageDone(facingRight, shot(fromInFront, damage = 1)))

        // The same shot, once the turret has come round, is worth nothing extra.
        val facingLeft = facingRight.copy(facing = Facing.LEFT)
        assertEquals(1, damageDone(facingLeft, shot(fromBehind, damage = 1)))
    }

    @Test
    fun `a sniper is helpless up close and untouchable at its own range`() {
        val sniper = enemyAt(200f, 32f, EnemyArchetype.SNIPER, health = 12)
        val here = sniper.center

        val closeIn = damageDone(sniper, shot(here, travelled = GameConfig.POINT_BLANK_RANGE - 10f))
        val fromAfar = damageDone(sniper, shot(here, travelled = GameConfig.POINT_BLANK_RANGE + 40f))

        assertEquals(GameConfig.PLAYER_BULLET_DAMAGE * GameConfig.WEAKNESS_DAMAGE_MULTIPLIER, closeIn)
        assertEquals(GameConfig.PLAYER_BULLET_DAMAGE, fromAfar)
    }

    @Test
    fun `a mortar is answered from outside its own range`() {
        val mortar = enemyAt(200f, 32f, EnemyArchetype.MORTAR, health = 12)

        val counterBattery = damageDone(mortar, shot(mortar.center, travelled = GameConfig.LONG_SHOT_RANGE + 20f))
        val closeUp = damageDone(mortar, shot(mortar.center, travelled = 40f))

        assertTrue("a long shot should hurt a mortar more than a close one", counterBattery > closeUp)
    }

    @Test
    fun `a hopper is shot out of the air by a falling round`() {
        val hopper = enemyAt(200f, 60f, EnemyArchetype.HOPPER, health = 12)
        val plunging = shot(hopper.center, velocity = Vec2(120f, -GameConfig.PLUNGING_FIRE_SPEED - 40f))
        val level = shot(hopper.center, velocity = Vec2(220f, 0f))

        assertTrue(damageDone(hopper, plunging) > damageDone(hopper, level))
    }

    @Test
    fun `a drone has to be met in the air`() {
        val drone = enemyAt(200f, 90f, EnemyArchetype.DRONE, health = 12)

        val fromTheGround = damageDone(drone, shot(drone.center, airborne = false))
        val fromMidJump = damageDone(drone, shot(drone.center, airborne = true))

        assertEquals(GameConfig.PLAYER_BULLET_DAMAGE, fromTheGround)
        assertEquals(GameConfig.PLAYER_BULLET_DAMAGE * GameConfig.WEAKNESS_DAMAGE_MULTIPLIER, fromMidJump)
    }

    @Test
    fun `a warden's shield stops a flat shot from the front but not one dropped over it`() {
        val warden = enemyAt(200f, 32f, EnemyArchetype.WARDEN, health = 12)
            .copy(facing = Facing.LEFT)
        assertTrue(warden.archetype.has(EnemyPower.FRONTAL_SHIELD))
        val infront = Vec2(warden.center.x - 4f, warden.center.y)

        val flat = shot(infront, velocity = Vec2(300f, 0f))
        assertEquals("the shield should turn a flat shot aside entirely", 0, damageDone(warden, flat))

        val lobbed = shot(infront, velocity = Vec2(120f, -GameConfig.PLUNGING_FIRE_SPEED - 60f))
        assertTrue("a shot dropped over the shield should land", damageDone(warden, lobbed) > 0)
    }

    @Test
    fun `a deflected round is spent and announced, so it never reads as a miss`() {
        val warden = enemyAt(200f, 32f, EnemyArchetype.WARDEN, health = 12).copy(facing = Facing.LEFT)
        val flat = shot(Vec2(warden.center.x - 4f, warden.center.y), velocity = Vec2(300f, 0f))

        val resolved = resolveCombat(
            running().copy(enemies = listOf(warden), projectiles = listOf(flat)),
            STEP,
        )

        assertTrue(resolved.projectiles.isEmpty())
        assertTrue(resolved.frameEvents.any { it is GameEvent.EnemyDeflected })
        assertEquals(warden.health, resolved.enemies.first().health)
    }

    @Test
    fun `finding a weak spot pays out and is announced, whether or not it was the killing blow`() {
        val grunt = enemyAt(200f, 32f, EnemyArchetype.GRUNT, health = 9)
        val head = shot(Vec2(grunt.center.x, grunt.bounds.top - 1f))
        val base = running()

        val resolved = resolveCombat(base.copy(enemies = listOf(grunt), projectiles = listOf(head)), STEP)

        assertTrue("the cube survived, so this is the weak-spot bonus alone", resolved.enemies.isNotEmpty())
        assertNotEquals(base.score, resolved.score)
        val announced = resolved.frameEvents.filterIsInstance<GameEvent.EnemyWeakSpotHit>().single()
        assertEquals(EnemyWeakness.HEADSHOT, announced.weakness)
        assertTrue(resolved.enemies.first().weakSpotFlashTimer > 0f)
    }

    @Test
    fun `a heavy cube is not staggered by a hit, and a light one is`() {
        val brute = enemyAt(200f, 32f, EnemyArchetype.BRUTE, health = 12)
        val grunt = enemyAt(200f, 32f, EnemyArchetype.GRUNT, health = 12)
        assertTrue(brute.archetype.has(EnemyPower.UNFLINCHING))

        fun knockback(enemy: EnemyCube): Float {
            val resolved = resolveCombat(
                running().copy(
                    enemies = listOf(enemy),
                    projectiles = listOf(shot(Vec2(enemy.center.x, enemy.center.y))),
                ),
                STEP,
            )
            return resolved.enemies.first().velocity.x
        }

        assertEquals(0f, knockback(brute), 0.001f)
        assertTrue("a grunt should be shoved back", knockback(grunt) > 0f)
    }
}
