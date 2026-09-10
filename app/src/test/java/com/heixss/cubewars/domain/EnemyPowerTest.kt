package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.advanceGame
import com.heixss.cubewars.domain.SimulationTestFixtures.enemyAt
import com.heixss.cubewars.domain.SimulationTestFixtures.flatArena
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.SimulationTestFixtures.step
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.EnemyPower
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.InputState
import com.heixss.cubewars.domain.model.MovementStyle
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What each archetype actually does differently, measured by running it.
 *
 * The numbers on an archetype are only a claim; these are the tests that make them a promise. They
 * are all run on an empty plain with the wave director idle, so what is being measured is the cube's
 * own behaviour and not whatever the arena happened to put in its way.
 */
class EnemyPowerTest {

    /** A quiet plain with one cube on it, and the player standing where the test puts them. */
    private fun duel(enemy: EnemyCube, playerAt: Vec2 = Vec2(200f, SimulationTestFixtures.FLOOR_Y)): GameState {
        val base = running()
        return base.copy(
            level = flatArena(),
            player = base.player.copy(position = playerAt, velocity = Vec2.ZERO, onGround = true),
            enemies = listOf(enemy),
            input = InputState(),
        )
    }

    private fun grounded(archetype: EnemyArchetype, x: Float, y: Float = SimulationTestFixtures.FLOOR_Y) =
        enemyAt(x, y, archetype).copy(onGround = true, fireCooldown = 0f)

    @Test
    fun `a hopper launches far higher than anything else that jumps`() {
        val hopper = grounded(EnemyArchetype.HOPPER, 260f)
        assertTrue(hopper.archetype.has(EnemyPower.HIGH_JUMP))

        var state = duel(hopper)
        var peak = hopper.position.y
        repeat(180) {
            state = advanceGame(state, STEP)
            state.enemies.firstOrNull()?.let { peak = maxOf(peak, it.position.y) }
        }

        val rise = peak - SimulationTestFixtures.FLOOR_Y
        // The old hop cleared about a tier; this one clears the player's own jump.
        assertTrue("a high jumper only rose $rise", rise > GameConfig.TILE * 4f)
    }

    @Test
    fun `a drone ignores the floor and comes to the player's height`() {
        val drone = enemyAt(340f, 40f, EnemyArchetype.DRONE)
        assertEquals(MovementStyle.HOVER, drone.archetype.movement)

        // The player is up on a ledge; a walking cube could never answer that.
        var state = duel(drone, playerAt = Vec2(200f, SimulationTestFixtures.FLOOR_Y))
        repeat(240) { state = advanceGame(state, STEP) }

        val settled = state.enemies.first()
        assertTrue("a hovering cube should never be standing", !settled.onGround)
        assertTrue(
            "it should have closed on the player, not fallen: ${settled.position}",
            abs(settled.center.x - state.player.bounds.centerX) < 40f,
        )
        assertTrue("and it should be holding station above them", settled.center.y > state.player.bounds.centerY)
    }

    @Test
    fun `a marksman aims where the player is going, not where they are`() {
        val sniper = grounded(EnemyArchetype.SNIPER, 380f)
        assertTrue(sniper.archetype.has(EnemyPower.MARKSMAN))

        fun firstShot(playerVelocity: Vec2): Vec2 {
            var state = duel(sniper).let {
                it.copy(player = it.player.copy(velocity = playerVelocity))
            }
            repeat(200) {
                state = advanceGame(state, STEP)
                // The player is held at a constant velocity so the lead has something to bite on.
                state = state.copy(player = state.player.copy(velocity = playerVelocity))
                val round = state.projectiles.firstOrNull { it.owner == ProjectileOwner.ENEMY }
                if (round != null) return round.velocity
            }
            throw AssertionError("the sniper never took a shot")
        }

        val atAStandingTarget = firstShot(Vec2.ZERO)
        val atOneRunningAway = firstShot(Vec2(-GameConfig.PLAYER_MAX_SPEED, 0f))

        assertNotEquals(
            "the lead should have changed the aim",
            atAStandingTarget.normalized(),
            atOneRunningAway.normalized(),
        )
    }

    @Test
    fun `a turret cannot spin on the spot, which is what makes its back reachable`() {
        val turret = grounded(EnemyArchetype.TURRET, 200f).copy(facing = Facing.RIGHT)
        assertTrue("a cube with a back worth stabbing needs a traverse", turret.archetype.traverseTime > 0f)

        // The player appears on its blind side.
        var state = duel(turret, playerAt = Vec2(120f, SimulationTestFixtures.FLOOR_Y))

        state = state.step()
        assertEquals("it should not have turned instantly", Facing.RIGHT, state.enemies.first().facing)

        val delay = (turret.archetype.traverseTime / STEP).toInt()
        val halfway = state.step(steps = delay / 2)
        assertEquals("still exposed", Facing.RIGHT, halfway.enemies.first().facing)

        val comeAround = halfway.step(steps = delay)
        assertEquals(Facing.LEFT, comeAround.enemies.first().facing)
    }

    @Test
    fun `a warden charges at a speed the player cannot simply walk away from`() {
        val warden = grounded(EnemyArchetype.WARDEN, 340f)
        assertEquals(MovementStyle.CHARGE, warden.archetype.movement)

        var state = duel(warden)
        repeat(90) { state = advanceGame(state, STEP) }

        val closing = state.enemies.first()
        assertTrue(
            "a charger should be at a real speed by now: ${closing.velocity.x}",
            abs(closing.velocity.x) > GameConfig.PLAYER_MAX_SPEED * 0.8f,
        )
        assertTrue("and heading for the player", closing.velocity.x < 0f)
    }

    @Test
    fun `a cube only fires down the barrel it is pointing`() {
        // Facing away, with the player well inside its range: it has to come round first.
        val turret = grounded(EnemyArchetype.TURRET, 200f).copy(facing = Facing.RIGHT)
        val state = duel(turret, playerAt = Vec2(120f, SimulationTestFixtures.FLOOR_Y)).step(steps = 3)

        assertTrue(
            "a turret should not be able to shoot over its own shoulder",
            state.projectiles.none { it.owner == ProjectileOwner.ENEMY },
        )
    }

    @Test
    fun `every shooter actually lands rounds near the player it is aiming at`() {
        for (archetype in EnemyArchetype.entries.filter { it.bulletsPerShot > 0 }) {
            val shooter = grounded(archetype, 200f + archetype.sightRange * 0.5f)
            var state = duel(shooter)

            var closest = Float.MAX_VALUE
            repeat(600) {
                state = advanceGame(state, STEP)
                // Held still: this measures the gun, not the player's ability to dodge.
                state = state.copy(player = state.player.copy(velocity = Vec2.ZERO))
                for (round in state.projectiles.filter { it.owner == ProjectileOwner.ENEMY }) {
                    closest = minOf(closest, (round.position - state.player.bounds.center).length)
                }
            }

            assertTrue(
                "${archetype.name} never got a round within reach of a stationary target " +
                    "(closest was $closest)",
                closest < GameConfig.PLAYER_SIZE,
            )
        }
    }
}
