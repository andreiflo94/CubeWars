package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.applyIntent
import com.heixss.cubewars.domain.SimulationTestFixtures.enemyAt
import com.heixss.cubewars.domain.SimulationTestFixtures.levels
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.SimulationTestFixtures.step
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.WavePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run's spine: fight the arena's quota of waves, take the gate, arrive somewhere harder.
 *
 * These are the rules a player would notice immediately if they broke — a gate that opens early, a
 * gate that never opens, or an arena that keeps spawning after it is supposed to be finished.
 */
class LevelProgressionTest {

    /** Skips the fighting: empties the arena with the wave already spent, then lets the rules run. */
    private fun GameState.clearWave(): GameState = copy(
        enemies = emptyList(),
        wave = wave.copy(phase = WavePhase.CLEARING, remainingToSpawn = 0),
    ).step()

    private fun GameState.clearLevel(): GameState {
        var state = this
        repeat(state.level.waveQuota) { state = state.clearWave() }
        return state
    }

    /** Drops the player into the gate's mouth. */
    private fun GameState.standInExit(): GameState = copy(
        player = player.copy(
            position = Vec2(level.exit.centerX - player.size / 2f, level.exit.bottom + 2f),
            velocity = Vec2.ZERO,
        ),
    )

    @Test
    fun `the exit stays shut until the arena's quota of waves is cleared`() {
        var state = running()
        repeat(state.level.waveQuota - 1) { state = state.clearWave() }

        assertFalse("the gate opened a wave early", state.wave.isLevelComplete)

        // Standing in a shut gate is just standing.
        val loitering = state.standInExit().step()
        assertEquals(state.level.id, loitering.level.id)
        assertEquals(1, loitering.levelNumber)
    }

    @Test
    fun `clearing the quota opens the exit and stops the spawner for good`() {
        val cleared = running().clearLevel()

        assertTrue(cleared.wave.isLevelComplete)
        assertTrue(cleared.frameEvents.any { it is GameEvent.LevelCleared })

        // Ten seconds of standing around: a finished arena stays finished.
        val idled = cleared.step(steps = 600)
        assertTrue("a completed arena went back to spawning", idled.enemies.isEmpty())
        assertTrue(idled.wave.isLevelComplete)
    }

    @Test
    fun `a cube knocked down a pit still lets the wave finish`() {
        val base = running()
        val lastCubeFalling = base.copy(
            // The wave is spent and its last cube is on its way past the bottom of the world.
            enemies = listOf(enemyAt(300f, GameConfig.KILL_PLANE_Y - 5f)),
            wave = base.wave.copy(phase = WavePhase.CLEARING, remainingToSpawn = 0),
        )

        // One step to take it off the board, one for the director to notice the arena is empty.
        val settled = lastCubeFalling.step(steps = 2)

        assertTrue("a cube fell out of the world and jammed the wave", settled.enemies.isEmpty())
        assertEquals(1, settled.wave.clearedInLevel)
    }

    @Test
    fun `walking into an open gate opens the next arena`() {
        val cleared = running().clearLevel()
        val score = cleared.score

        val advanced = cleared.standInExit().step()

        assertEquals(levels.loadLevel(1).name, advanced.level.name)
        assertEquals(2, advanced.levelNumber)
        assertEquals(advanced.level.playerSpawn, advanced.player.position)
        assertEquals(WavePhase.INTERMISSION, advanced.wave.phase)
        assertEquals(0, advanced.wave.clearedInLevel)
        assertTrue("the gate should be worth something", advanced.score > score)
        assertTrue(advanced.frameEvents.any { it is GameEvent.LevelEntered })
        assertTrue("the old arena's cubes came through the gate", advanced.enemies.isEmpty())
    }

    @Test
    fun `the wave index carries across the gate, so the ramp never resets`() {
        val cleared = running().clearLevel()
        val waveIndex = cleared.wave.index

        val advanced = cleared.standInExit().step()

        assertEquals(waveIndex, advanced.wave.index)
        assertTrue("later arenas must not be easier", advanced.level.difficulty.extraEnemies >= 0)
    }

    @Test
    fun `each gate gives back a point of health, but never more than the player started with`() {
        val hurt = running().let { it.copy(player = it.player.copy(health = 2)) }.clearLevel()

        val advanced = hurt.standInExit().step()
        assertEquals(3, advanced.player.health)

        val healthy = running().clearLevel().standInExit().step()
        assertEquals(healthy.player.maxHealth, healthy.player.health)
    }

    @Test
    fun `the arenas rotate rather than run out`() {
        var state = running()
        repeat(levels.levelCount) { state = state.clearLevel().standInExit().step() }

        // Back to the first arena, but a long way into the run.
        assertEquals(0, state.level.id)
        assertEquals(levels.levelCount + 1, state.levelNumber)
        assertTrue("the wave ramp should have kept climbing", state.wave.index >= levels.levelCount)
    }

    @Test
    fun `a retry starts the rotation again`() {
        val deep = running().clearLevel().standInExit().step()
        assertNotEquals(0, deep.level.id)

        val retried = applyIntent(deep.copy(status = GameStatus.GAME_OVER), GameIntent.Start)

        assertEquals(0, retried.level.id)
        assertEquals(1, retried.levelNumber)
        assertEquals(0, retried.wave.index)
    }
}
