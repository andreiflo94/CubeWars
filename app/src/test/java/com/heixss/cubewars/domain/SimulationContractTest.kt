package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.advanceGame
import com.heixss.cubewars.domain.SimulationTestFixtures.applyIntent
import com.heixss.cubewars.domain.SimulationTestFixtures.createGame
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.SimulationTestFixtures.step
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.InputState
import com.heixss.cubewars.domain.model.WavePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises the single-state design makes, pinned down as tests.
 *
 * If any of these break, the architecture has stopped paying for itself.
 */
class SimulationContractTest {

    private fun playFor(seconds: Float, seed: Long, input: InputState): GameState {
        var state = running(seed).copy(input = input)
        repeat((seconds / STEP).toInt()) { state = advanceGame(state, STEP) }
        return state
    }

    @Test
    fun `the same seed and the same inputs produce byte-identical states`() {
        val input = InputState(moveAxis = 1f, fireHeld = true, jumpHeld = true)

        val first = playFor(seconds = 12f, seed = 20_260_814L, input = input)
        val second = playFor(seconds = 12f, seed = 20_260_814L, input = input)

        assertEquals(first, second)
    }

    @Test
    fun `a different seed produces a different run`() {
        val input = InputState(moveAxis = 1f, fireHeld = true)

        val first = playFor(seconds = 12f, seed = 1L, input = input)
        val second = playFor(seconds = 12f, seed = 2L, input = input)

        assertNotEquals(first, second)
    }

    @Test
    fun `the title card and the pause screen freeze the world completely`() {
        val paused = running().step(steps = 30).copy(status = GameStatus.PAUSED)

        val later = paused.step(steps = 120)

        assertEquals(paused.copy(frameEvents = emptyList()), later)
    }

    @Test
    fun `after a loss the debris keeps settling but the score is final`() {
        val dying = running().step(steps = 90).copy(status = GameStatus.GAME_OVER)

        val later = dying.step(steps = 60)

        assertEquals(dying.score, later.score)
        assertEquals(dying.enemies, later.enemies)
        assertTrue("the camera should still be easing back", later.elapsedTime > dying.elapsedTime)
    }

    @Test
    fun `frame events describe only the step that produced them`() {
        var state = running()
        var sawEvents = false
        repeat(400) {
            val next = advanceGame(state, STEP)
            if (state.frameEvents.isNotEmpty() && next.frameEvents.isEmpty()) sawEvents = true
            state = next
        }
        assertTrue("events should not accumulate across steps", sawEvents || state.frameEvents.size < 8)
    }

    @Test
    fun `waves start, fill the arena, and are numbered from one`() {
        var state = running()
        assertEquals(WavePhase.INTERMISSION, state.wave.phase)
        assertEquals(1, state.wave.displayNumber)

        state = state.step(steps = (4f / STEP).toInt())

        assertNotEquals(WavePhase.INTERMISSION, state.wave.phase)
        assertTrue("expected enemies on the field", state.enemies.isNotEmpty())
    }

    @Test
    fun `the arena never holds more enemies than the cap allows`() {
        var state = running()
        var peak = 0
        repeat((90f / STEP).toInt()) {
            state = advanceGame(state, STEP)
            peak = maxOf(peak, state.enemies.size)
        }
        assertTrue("peak was $peak", peak <= com.heixss.cubewars.domain.config.GameConfig.WAVE_MAX_ALIVE)
    }

    @Test
    fun `the particle field stays bounded during a long firefight`() {
        var state = running().copy(input = InputState(moveAxis = 1f, fireHeld = true))
        var peak = 0
        repeat((120f / STEP).toInt()) {
            state = advanceGame(state, STEP)
            peak = maxOf(peak, state.particles.size)
        }
        assertTrue("peak was $peak", peak <= com.heixss.cubewars.domain.config.GameConfig.MAX_PARTICLES)
    }

    @Test
    fun `entity ids are never reused`() {
        var state = running().copy(input = InputState(moveAxis = 1f, fireHeld = true))
        val seen = HashSet<Long>()
        repeat((30f / STEP).toInt()) {
            state = advanceGame(state, STEP)
            state.projectiles.forEach { seen += it.id }
            state.enemies.forEach { seen += it.id }
        }
        assertTrue(seen.all { it < state.nextEntityId })
    }

    @Test
    fun `starting from the title card begins the run without rebuilding the world`() {
        val ready = createGame(seed = 5L)
        assertEquals(GameStatus.READY, ready.status)

        val started = applyIntent(ready, GameIntent.Start)

        assertEquals(GameStatus.RUNNING, started.status)
        assertEquals(ready.level, started.level)
        assertEquals(ready.player.position, started.player.position)
    }

    @Test
    fun `retrying after a loss keeps the best score and hands out a fresh world`() {
        val lost = running(seed = 9L)
            .step(steps = 200)
            .copy(status = GameStatus.GAME_OVER, score = 4_200, highScore = 1_000)

        val retry = applyIntent(lost, GameIntent.Start)

        assertEquals(GameStatus.RUNNING, retry.status)
        assertEquals(4_200, retry.highScore)
        assertEquals(0, retry.score)
        assertEquals(retry.player.maxHealth, retry.player.health)
        assertTrue(retry.enemies.isEmpty())
        assertNotEquals("a retry should not replay the same fight", lost.rng, retry.rng)
    }

    @Test
    fun `pausing drops every held control so nothing is stuck down on resume`() {
        val busy = running().copy(input = InputState(moveAxis = 1f, fireHeld = true, jumpHeld = true))

        val paused = applyIntent(busy, GameIntent.TogglePause)

        assertEquals(GameStatus.PAUSED, paused.status)
        assertEquals(InputState(), paused.input)
    }
}
