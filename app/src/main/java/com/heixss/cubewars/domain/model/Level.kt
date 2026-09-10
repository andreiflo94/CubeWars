package com.heixss.cubewars.domain.model

import com.heixss.cubewars.domain.config.GameConfig

/** How a block behaves, not how it looks. */
enum class BlockKind {
    /** Solid on all six sides. */
    SOLID,

    /** Solid only from above — you jump up through it and can drop down off it. */
    ONE_WAY,
}

/** A single chunk of level geometry. */
data class PlatformBlock(
    val bounds: Aabb,
    val kind: BlockKind,
)

/** A spot the wave spawner may drop an enemy on. */
data class SpawnPoint(
    val position: Vec2,
    /** Spawners prefer points the player can plausibly reach; ground points are safest. */
    val onGround: Boolean = true,
)

/**
 * How much harder one arena plays than the baseline.
 *
 * The wave director keys off the wave index for the run-long ramp; this is the per-arena dial on top
 * of it, so "level 4 is nastier than level 2" is a property of the level data rather than a special
 * case buried in a rule.
 */
data class LevelDifficulty(
    /** Added to every wave's headcount, and to the cap it is allowed to reach. */
    val extraEnemies: Int = 0,
    /** Scales the gap between spawns. Below 1 the cubes arrive faster. */
    val spawnIntervalScale: Float = 1f,
    /** Added to how many cubes may be alive at once. */
    val extraAlive: Int = 0,
    /** Treats waves as this many further along when deciding which archetypes are unlocked. */
    val unlockBonus: Int = 0,
)

/** Static level geometry. Immutable for the lifetime of a run. */
data class Level(
    val id: Int,
    val name: String,
    val width: Float,
    val height: Float,
    val blocks: List<PlatformBlock>,
    val playerSpawn: Vec2,
    val spawnPoints: List<SpawnPoint>,
    /** Waves to clear here before the exit opens. */
    val waveQuota: Int,
    val difficulty: LevelDifficulty,
    /** What this arena is dressed in. Cosmetic, and deliberately not consulted by any rule. */
    val theme: LevelTheme = LevelTheme.FOUNDRY,
    /** The gate at the far end. Stepping into it once it is open ends the level. */
    val exit: Aabb,
) {
    val solidBlocks: List<PlatformBlock> = blocks.filter { it.kind == BlockKind.SOLID }

    /**
     * Where the camera sits the moment the arena opens: on the spawn, pulled inside the level's
     * edges — or dead-centred when the level is smaller than the view.
     */
    val cameraStart: Vec2
        get() = Vec2(
            clampToView(playerSpawn.x, GameConfig.VIEWPORT_WIDTH, width),
            clampToView(height / 2f, GameConfig.VIEWPORT_HEIGHT, height),
        )

    private fun clampToView(target: Float, viewExtent: Float, levelExtent: Float): Float {
        val half = viewExtent / 2f
        return if (levelExtent <= viewExtent) levelExtent / 2f else target.coerceIn(half, levelExtent - half)
    }
}
