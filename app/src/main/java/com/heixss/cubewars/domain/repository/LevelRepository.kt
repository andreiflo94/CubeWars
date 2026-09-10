package com.heixss.cubewars.domain.repository

import com.heixss.cubewars.domain.model.Level

/**
 * Source of level geometry.
 *
 * The domain declares what it needs; the data layer decides whether that comes from a hand-authored
 * blueprint, a file, or a generator. Swapping the implementation cannot change a single rule.
 */
interface LevelRepository {

    /** How many distinct arenas exist. */
    val levelCount: Int

    /**
     * Loads one arena. Ids wrap, so asking for the level after the last one starts the rotation
     * again — a run has no ceiling, it just gets harder.
     */
    fun loadLevel(id: Int = 0): Level
}
