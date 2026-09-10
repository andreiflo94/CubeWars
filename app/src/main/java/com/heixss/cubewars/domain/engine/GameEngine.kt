package com.heixss.cubewars.domain.engine

import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.repository.ScoreRepository
import com.heixss.cubewars.domain.usecase.AdvanceGameUseCase
import com.heixss.cubewars.domain.usecase.ApplyIntentUseCase
import com.heixss.cubewars.domain.usecase.CreateGameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Owns the one and only [GameState] and is the only thing allowed to write to it.
 *
 * ### Why a single writer
 *
 * Three independent sources want to change the world: the clock, the player's fingers, and the
 * high-score store. Rather than let three coroutines race on the same `MutableStateFlow`, all three
 * are [merge]d into one flow of [Signal]s and folded sequentially by a single collector. The result
 * is a total order over every change to the game — no locks, no compare-and-swap retries, and no
 * chance of an intent landing halfway through a simulation step.
 *
 * ### Why the clock is gated
 *
 * The tick source is wrapped in [flatMapLatest] over [isActive] so that backgrounding the app tears
 * the clock down entirely. Resuming builds a fresh one, which starts a fresh timeline — otherwise
 * the first tick after a five-minute pause would try to simulate five minutes of catch-up.
 */
class GameEngine(
    private val createGame: CreateGameUseCase,
    private val applyIntent: ApplyIntentUseCase,
    private val advanceGame: AdvanceGameUseCase,
    private val scoreRepository: ScoreRepository,
    private val clock: GameClock,
    private val scope: CoroutineScope,
    seed: Long,
) {

    private val _state = MutableStateFlow(createGame(seed))

    /** The single source of truth. Collect it to render, observe it to react. */
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val intents = MutableSharedFlow<GameIntent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val isActive = MutableStateFlow(false)

    private var loop: Job? = null

    /** Anything that can change the world, normalised into one type so it can be folded. */
    private sealed interface Signal {
        @JvmInline value class Tick(val dt: Float) : Signal

        @JvmInline value class Player(val intent: GameIntent) : Signal

        @JvmInline value class HighScoreLoaded(val value: Int) : Signal
    }

    /** Starts the simulation loop. Idempotent. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (loop != null) return

        val ticks = isActive
            .flatMapLatest { active -> if (active) clock.ticks() else emptyFlow() }
            .map { Signal.Tick(it) }

        loop = scope.launch {
            merge(
                ticks,
                intents.map { Signal.Player(it) },
                scoreRepository.observeHighScore().map { Signal.HighScoreLoaded(it) },
            ).collect { signal -> _state.value = reduce(_state.value, signal) }
        }

        // Persisting the score is a genuine side effect, so it observes the state rather than
        // living inside the fold.
        scope.launch {
            state.map { it.status }
                .distinctUntilChanged()
                .filter { it == GameStatus.GAME_OVER }
                .collect { scoreRepository.submitScore(_state.value.score) }
        }

        isActive.value = true
    }

    private fun reduce(current: GameState, signal: Signal): GameState = when (signal) {
        is Signal.Tick -> advanceGame(current, signal.dt)
        is Signal.Player -> applyIntent(current, signal.intent)
        is Signal.HighScoreLoaded -> current.copy(highScore = maxOf(current.highScore, signal.value))
    }

    /** Queues a player intent. Safe to call from any thread, including the render thread. */
    fun dispatch(intent: GameIntent) {
        intents.tryEmit(intent)
    }

    /** Suspends ticking (app backgrounded). Player intents are still accepted. */
    fun pause() {
        isActive.value = false
        dispatch(GameIntent.ReleaseAll)
        if (_state.value.status == GameStatus.RUNNING) dispatch(GameIntent.TogglePause)
    }

    /** Resumes ticking with a fresh timeline. */
    fun resume() {
        isActive.value = true
    }
}
