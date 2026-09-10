package com.heixss.cubewars.data.score

import android.content.Context
import android.content.SharedPreferences
import com.heixss.cubewars.domain.repository.ScoreRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Stores the best score in shared preferences and republishes it as a flow.
 *
 * The listener bridge means a score written from anywhere reaches the engine without anyone having
 * to remember to push it — the engine simply folds whatever the flow emits into the state.
 */
class PreferencesScoreRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScoreRepository {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun observeHighScore(): Flow<Int> = callbackFlow {
        send(preferences.getInt(KEY_HIGH_SCORE, 0))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
            if (key == KEY_HIGH_SCORE) trySend(store.getInt(KEY_HIGH_SCORE, 0))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().conflate().flowOn(io)

    override suspend fun submitScore(score: Int) = withContext(io) {
        if (score > preferences.getInt(KEY_HIGH_SCORE, 0)) {
            preferences.edit().putInt(KEY_HIGH_SCORE, score).apply()
        }
    }

    private companion object {
        const val FILE_NAME = "cube_wars_scores"
        const val KEY_HIGH_SCORE = "high_score"
    }
}
