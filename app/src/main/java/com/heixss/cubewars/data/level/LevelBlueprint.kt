package com.heixss.cubewars.data.level

import com.heixss.cubewars.domain.model.LevelDifficulty
import com.heixss.cubewars.domain.model.LevelTheme

/**
 * One arena described entirely in tile coordinates.
 *
 * This is the seam between the level *generator* and the game. `tools/generate_levels.py` lays out
 * arenas, checks them against what the player can physically do, and writes them out as a list of
 * these; [ArenaLevelRepository] is the only thing that reads them, and it turns each one into a
 * `Level` of world-unit geometry.
 *
 * Deliberately plain data with no behaviour: everything derived from a layout — the spawn points on
 * every surface, the containment walls, the exit gate — is derived once, in the repository, so a
 * generated arena can never disagree with a hand-written one about how those work.
 */
internal data class LevelBlueprint(
    val name: String,
    val theme: LevelTheme,
    val widthTiles: Int,
    val waveQuota: Int,
    val difficulty: LevelDifficulty,
    /** Runs of floor, left to right. The gaps between them are the pits. */
    val ground: List<GroundRun>,
    /** Cover standing on the floor: solid from every side. */
    val solids: List<SolidSpec>,
    /** Thin one-way platforms you jump up through. */
    val ledges: List<LedgeSpec>,
)

/** A run of floor spanning tile columns `[from, to)`. */
internal data class GroundRun(val from: Int, val to: Int)

/** A solid block with its bottom-left corner at tile `(x, y)`. */
internal data class SolidSpec(val x: Int, val y: Int, val width: Int, val height: Int)

/** A one-way ledge whose surface sits at tile row `y`. */
internal data class LedgeSpec(val x: Int, val y: Int, val width: Int)
