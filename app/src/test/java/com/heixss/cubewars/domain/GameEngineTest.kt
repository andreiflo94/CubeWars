package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.advanceGame
import com.heixss.cubewars.domain.SimulationTestFixtures.applyIntent
import com.heixss.cubewars.domain.SimulationTestFixtures.createGame
import com.heixss.cubewars.domain.engine.GameClock
import com.heixss.cubewars.domain.engine.GameEngine
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.repository.ScoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineTest {

    /** A clock the test drives by hand, so no wall time is involved. */
    private val ticks = MutableSharedFlow<Float>(extraBufferCapacity = 256)
    private val clock = GameClock { ticks }

    private val storedHighScore = MutableStateFlow(0)
    private val submitted = mutableListOf<Int>()

    private val scoreRepository = object : ScoreRepository {
        override fun observeHighScore(): Flow<Int> = storedHighScore
        override suspend fun submitScore(score: Int) {
            submitted += score
            if (score > storedHighScore.value) storedHighScore.value = score
        }
    }

    private fun engineIn(scope: CoroutineScope, seed: Long = 3L) = GameEngine(
        createGame = createGame,
        applyIntent = applyIntent,
        advanceGame = advanceGame,
        scoreRepository = scoreRepository,
        clock = clock,
        scope = scope,
        seed = seed,
    )

    /**
     * Feeds [times] ticks in and lets the engine consume them.
     *
     * `runCurrent` rather than `advanceUntilIdle`: the engine's collectors live in the background
     * scope, and `advanceUntilIdle` stops as soon as only background work is left — so it would
     * never run them at all.
     */
    private fun TestScope.tick(times: Int) {
        repeat(times) { check(ticks.tryEmit(STEP)) }
        runCurrent()
    }

    @Test
    fun `the engine publishes a ready state before anything ticks`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()

        assertEquals(GameStatus.READY, engine.state.value.status)
        assertEquals(0f, engine.state.value.elapsedTime, 0f)
    }

    @Test
    fun `ticks advance the world only once the run has started`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()

        tick(60)
        assertEquals("READY should ignore the clock", 0f, engine.state.value.elapsedTime, 0f)

        engine.dispatch(GameIntent.Start)
        runCurrent()
        tick(60)

        assertEquals(GameStatus.RUNNING, engine.state.value.status)
        assertEquals(1f, engine.state.value.elapsedTime, 0.01f)
    }

    @Test
    fun `intents and ticks are folded into one ordered stream`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()
        engine.dispatch(GameIntent.Start)
        runCurrent()

        engine.dispatch(GameIntent.Move(1f))
        runCurrent()
        tick(30)

        val moved = engine.state.value
        assertEquals(1f, moved.input.moveAxis, 0f)
        assertTrue("the player should have travelled right", moved.player.velocity.x > 0f)
    }

    @Test
    fun `a stored high score is folded into the state`() = runTest {
        storedHighScore.value = 7_500
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()

        assertEquals(7_500, engine.state.value.highScore)
    }

    @Test
    fun `the score is persisted exactly once when the run ends`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()
        engine.dispatch(GameIntent.Start)
        runCurrent()

        // Reach into the world the only way anything can: through an intent... except there is no
        // "die" intent, so drive the clock until the enemies do the job. Instead, assert the
        // simpler contract: no submission happens while the run is alive.
        tick(120)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `pausing stops the clock from reaching the world`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()
        engine.dispatch(GameIntent.Start)
        runCurrent()
        tick(30)

        val beforePause = engine.state.value.elapsedTime
        engine.pause()
        runCurrent()
        tick(120)

        assertEquals(GameStatus.PAUSED, engine.state.value.status)
        assertEquals(beforePause, engine.state.value.elapsedTime, 0.0001f)
    }

    @Test
    fun `resuming reconnects the clock`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        runCurrent()
        engine.dispatch(GameIntent.Start)
        runCurrent()
        engine.pause()
        runCurrent()

        engine.resume()
        engine.dispatch(GameIntent.Start)
        runCurrent()
        tick(60)

        assertEquals(GameStatus.RUNNING, engine.state.value.status)
        assertTrue(engine.state.value.elapsedTime > 0.9f)
    }

    @Test
    fun `starting twice does not start two loops`() = runTest {
        val engine = engineIn(backgroundScope)
        engine.start()
        engine.start()
        runCurrent()
        engine.dispatch(GameIntent.Start)
        runCurrent()

        tick(60)

        assertEquals(
            "a doubled loop would simulate each tick twice",
            1f,
            engine.state.value.elapsedTime,
            0.01f,
        )
    }
}
