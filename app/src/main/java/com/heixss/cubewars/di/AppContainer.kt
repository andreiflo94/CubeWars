package com.heixss.cubewars.di

import android.content.Context
import com.heixss.cubewars.data.level.ArenaLevelRepository
import com.heixss.cubewars.data.score.PreferencesScoreRepository
import com.heixss.cubewars.domain.engine.FixedStepGameClock
import com.heixss.cubewars.domain.engine.GameClock
import com.heixss.cubewars.domain.engine.GameEngine
import com.heixss.cubewars.domain.repository.LevelRepository
import com.heixss.cubewars.domain.repository.ScoreRepository
import com.heixss.cubewars.domain.usecase.AdvanceGameUseCase
import com.heixss.cubewars.domain.usecase.ApplyIntentUseCase
import com.heixss.cubewars.domain.usecase.CreateGameUseCase
import com.heixss.cubewars.presentation.feedback.HapticFeedback
import kotlinx.coroutines.CoroutineScope

/**
 * The composition root.
 *
 * This is the one place in the app where a concrete implementation is named. Everything else
 * depends on the interface it was handed, which is what lets the entire domain be exercised in
 * plain JVM tests with no Android and no libGDX on the classpath.
 */
class AppContainer(
    context: Context,
    scope: CoroutineScope,
    seed: Long = System.nanoTime(),
) {

    private val levelRepository: LevelRepository = ArenaLevelRepository()

    private val scoreRepository: ScoreRepository = PreferencesScoreRepository(context)

    private val createGame = CreateGameUseCase(levelRepository)

    private val applyIntent = ApplyIntentUseCase(createGame)

    private val advanceGame = AdvanceGameUseCase.default(levelRepository)

    private val clock: GameClock = FixedStepGameClock()

    val haptics = HapticFeedback(context)

    val engine = GameEngine(
        createGame = createGame,
        applyIntent = applyIntent,
        advanceGame = advanceGame,
        scoreRepository = scoreRepository,
        clock = clock,
        scope = scope,
        seed = seed,
    )
}
