package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.enemyAt
import com.heixss.cubewars.domain.SimulationTestFixtures.enemyBulletAt
import com.heixss.cubewars.domain.SimulationTestFixtures.playerBulletAt
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.usecase.ResolveCombatUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatTest {

    private val resolveCombat = ResolveCombatUseCase()

    @Test
    fun `a bullet that reaches an enemy takes health off it`() {
        val enemy = enemyAt(200f, 32f, EnemyArchetype.BRUTE)
        val state = running().copy(
            enemies = listOf(enemy),
            projectiles = listOf(playerBulletAt(enemy.center.x, enemy.center.y)),
        )

        val resolved = resolveCombat(state, STEP)

        assertEquals(1, resolved.enemies.size)
        assertEquals(enemy.health - 1, resolved.enemies.first().health)
        assertTrue("the bullet should be spent", resolved.projectiles.isEmpty())
        assertTrue(resolved.frameEvents.any { it is GameEvent.EnemyDamaged })
    }

    @Test
    fun `the killing blow removes the enemy and pays out its score`() {
        val enemy = enemyAt(200f, 32f, EnemyArchetype.GRUNT, health = 1)
        val state = running().copy(
            enemies = listOf(enemy),
            projectiles = listOf(playerBulletAt(enemy.center.x, enemy.center.y)),
        )

        val resolved = resolveCombat(state, STEP)

        assertTrue(resolved.enemies.isEmpty())
        assertEquals(EnemyArchetype.GRUNT.scoreValue, resolved.score)
        assertEquals(1, resolved.wave.totalKills)
        assertTrue(resolved.frameEvents.any { it is GameEvent.EnemyDestroyed })
        assertTrue("a kill should kick the camera", resolved.camera.shake > 0f)
    }

    @Test
    fun `one bullet cannot damage two enemies`() {
        val overlapping = listOf(
            enemyAt(200f, 32f, health = 5, id = 1L),
            enemyAt(202f, 32f, health = 5, id = 2L),
        )
        val state = running().copy(
            enemies = overlapping,
            projectiles = listOf(playerBulletAt(206f, 39f)),
        )

        val resolved = resolveCombat(state, STEP)

        val damaged = resolved.enemies.count { it.health < 5 }
        assertEquals(1, damaged)
    }

    @Test
    fun `a spawning enemy is not a valid target`() {
        val materialising = enemyAt(200f, 32f).copy(animationTime = 0f)
        val state = running().copy(
            enemies = listOf(materialising),
            projectiles = listOf(playerBulletAt(materialising.center.x, materialising.center.y)),
        )

        val resolved = resolveCombat(state, STEP)

        assertEquals(materialising.health, resolved.enemies.first().health)
        assertEquals(1, resolved.projectiles.size)
    }

    @Test
    fun `an enemy bullet hurts the player and knocks them away from it`() {
        val base = running()
        val player = base.player
        val state = base.copy(
            projectiles = listOf(enemyBulletAt(player.bounds.centerX + 2f, player.bounds.centerY)),
        )

        val resolved = resolveCombat(state, STEP)

        assertEquals(player.health - 1, resolved.player.health)
        assertTrue(resolved.player.isInvulnerable)
        assertTrue("knockback should push left, away from the bullet", resolved.player.velocity.x < 0f)
        assertTrue(resolved.frameEvents.any { it is GameEvent.PlayerDamaged })
    }

    @Test
    fun `an invulnerable player shrugs off a second hit`() {
        val base = running()
        val shielded = base.copy(
            player = base.player.copy(invulnerabilityTimer = GameConfig.PLAYER_INVULNERABILITY_TIME),
            projectiles = listOf(enemyBulletAt(base.player.bounds.centerX, base.player.bounds.centerY)),
        )

        val resolved = resolveCombat(shielded, STEP)

        assertEquals(base.player.health, resolved.player.health)
    }

    @Test
    fun `touching an enemy hurts as much as that archetype hits for`() {
        val base = running()
        val brute = enemyAt(base.player.position.x, base.player.position.y, EnemyArchetype.BRUTE)
        val state = base.copy(enemies = listOf(brute))

        val resolved = resolveCombat(state, STEP)

        assertEquals(base.player.health - EnemyArchetype.BRUTE.contactDamage, resolved.player.health)
    }

    @Test
    fun `running out of health ends the run exactly once`() {
        val base = running()
        val onDeathsDoor = base.copy(
            player = base.player.copy(health = 1),
            projectiles = listOf(enemyBulletAt(base.player.bounds.centerX, base.player.bounds.centerY)),
        )

        val resolved = resolveCombat(onDeathsDoor, STEP)

        assertEquals(GameStatus.GAME_OVER, resolved.status)
        assertEquals(1, resolved.frameEvents.count { it == GameEvent.PlayerDestroyed })

        // Resolving again must not re-announce the death.
        val again = resolveCombat(resolved.copy(frameEvents = emptyList()), STEP)
        assertEquals(0, again.frameEvents.count { it == GameEvent.PlayerDestroyed })
    }

    @Test
    fun `a cube that falls out of the world is taken off the board and paid out`() {
        val enemy = enemyAt(200f, GameConfig.KILL_PLANE_Y - 1f)
        val state = running().copy(enemies = listOf(enemy))

        val resolved = resolveCombat(state, STEP)

        assertTrue("a cube below the kill plane must not survive", resolved.enemies.isEmpty())
        assertEquals(state.score + enemy.archetype.scoreValue, resolved.score)
        assertEquals(1, resolved.wave.killsThisWave)
        assertTrue(resolved.frameEvents.any { it is GameEvent.EnemyDestroyed })
    }

    @Test
    fun `combat leaves the id counter and generator further along than it found them`() {
        val enemy = enemyAt(200f, 32f, health = 1)
        val state = running().copy(
            enemies = listOf(enemy),
            projectiles = listOf(playerBulletAt(enemy.center.x, enemy.center.y)),
        )

        val resolved = resolveCombat(state, STEP)

        assertTrue(resolved.nextEntityId > state.nextEntityId)
        assertNotEquals(state.rng, resolved.rng)
    }
}
