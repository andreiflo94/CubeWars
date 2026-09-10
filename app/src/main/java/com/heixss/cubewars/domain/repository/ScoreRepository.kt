package com.heixss.cubewars.domain.repository

import kotlinx.coroutines.flow.Flow

/** Persistence for the only thing worth keeping between runs. */
interface ScoreRepository {

    /** Emits the current best score, and again every time it improves. */
    fun observeHighScore(): Flow<Int>

    /** Records [score]; ignored if it does not beat the stored best. */
    suspend fun submitScore(score: Int)
}
