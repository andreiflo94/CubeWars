package com.heixss.cubewars.data.level

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.SpawnPoint
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.repository.LevelRepository

/**
 * Turns the arena blueprints into playable geometry.
 *
 * The layouts themselves are no longer written here: `tools/generate_levels.py` designs them against
 * the same measurements the player is bound by — how high a jump rises, how far a running one
 * carries — and writes the result into [GeneratedLevels]. Regenerating the rotation is one command,
 * and the arenas it produces are checked by the same tests that guarded the hand-authored ones.
 *
 * What stays here is everything that must be true of *any* arena regardless of who drew it: the
 * invisible walls at the edges, the spawn points implied by every surface, and the exit gate at the
 * far end of the last run of floor. Deriving those rather than authoring them is why a generator can
 * be trusted with the rest.
 */
class ArenaLevelRepository internal constructor(
    private val blueprints: List<LevelBlueprint>,
) : LevelRepository {

    /** The rotation the game actually ships. */
    constructor() : this(GeneratedLevels.blueprints)

    override val levelCount: Int get() = blueprints.size

    override fun loadLevel(id: Int): Level {
        val index = ((id % blueprints.size) + blueprints.size) % blueprints.size
        return compile(index, blueprints[index])
    }

    private fun compile(id: Int, blueprint: LevelBlueprint): Level {
        val width = blueprint.widthTiles * GameConfig.TILE
        // The arena is exactly one screen tall, which pins the camera vertically and keeps the
        // action reading as a side-on duel rather than a scrolling climb.
        val height = GameConfig.VIEWPORT_HEIGHT
        val canvas = Canvas(width, height)

        blueprint.ground.forEach { canvas.ground(it.from, it.to) }
        blueprint.solids.forEach { canvas.solid(it.x, it.y, it.width, it.height) }
        blueprint.ledges.forEach { canvas.ledge(it.x, it.y, it.width) }

        return Level(
            id = id,
            name = blueprint.name,
            width = width,
            height = height,
            blocks = canvas.blocks,
            playerSpawn = canvas.playerSpawn,
            spawnPoints = canvas.spawnPoints,
            waveQuota = blueprint.waveQuota,
            difficulty = blueprint.difficulty,
            theme = blueprint.theme,
            exit = canvas.exit(),
        )
    }

    /** Accumulates geometry in tile coordinates and the spawn points implied by it. */
    private class Canvas(width: Float, height: Float) {
        val blocks = mutableListOf<PlatformBlock>()
        val spawnPoints = mutableListOf<SpawnPoint>()
        val playerSpawn = Vec2(2 * GameConfig.TILE, GROUND_HEIGHT_TILES * GameConfig.TILE)

        /** The right-hand end of the furthest run of floor, in tiles. */
        private var lastGroundEnd = 0

        init {
            // Invisible walls stop anything walking out of the arena sideways.
            blocks += PlatformBlock(Aabb(-GameConfig.TILE, 0f, GameConfig.TILE, height), BlockKind.SOLID)
            blocks += PlatformBlock(Aabb(width, 0f, GameConfig.TILE, height), BlockKind.SOLID)
        }

        /** A run of floor between two tile columns, with room to spawn along it. */
        fun ground(from: Int, to: Int) {
            solid(x = from, y = 0, width = to - from, height = GROUND_HEIGHT_TILES)
            lastGroundEnd = maxOf(lastGroundEnd, to)
            val surface = GROUND_HEIGHT_TILES * GameConfig.TILE
            var column = from + 2
            while (column < to - 1) {
                spawnPoints += SpawnPoint(Vec2(column * GameConfig.TILE, surface), onGround = true)
                column += SPAWN_SPACING_TILES
            }
        }

        /** A block that is solid from every side. */
        fun solid(x: Int, y: Int, width: Int, height: Int) {
            blocks += PlatformBlock(
                Aabb(
                    x * GameConfig.TILE,
                    y * GameConfig.TILE,
                    width * GameConfig.TILE,
                    height * GameConfig.TILE,
                ),
                BlockKind.SOLID,
            )
        }

        /** A thin one-way ledge you can jump up through, with a spawn point on top of it. */
        fun ledge(x: Int, y: Int, width: Int) {
            val left = x * GameConfig.TILE
            val bottom = y * GameConfig.TILE
            val span = width * GameConfig.TILE
            blocks += PlatformBlock(Aabb(left, bottom, span, LEDGE_THICKNESS), BlockKind.ONE_WAY)
            spawnPoints += SpawnPoint(
                position = Vec2(left + span / 2f - GameConfig.TILE / 2f, bottom + LEDGE_THICKNESS),
                onGround = false,
            )
        }

        /** The gate, standing on the floor at the far end of the last run of ground. */
        fun exit(): Aabb = Aabb(
            x = (lastGroundEnd - EXIT_INSET_TILES) * GameConfig.TILE,
            y = GROUND_HEIGHT_TILES * GameConfig.TILE,
            width = EXIT_WIDTH_TILES * GameConfig.TILE,
            height = EXIT_HEIGHT_TILES * GameConfig.TILE,
        )
    }

    companion object {
        const val GROUND_HEIGHT_TILES = 2
        const val SPAWN_SPACING_TILES = 7
        const val LEDGE_THICKNESS = 8f
        const val EXIT_WIDTH_TILES = 2
        const val EXIT_HEIGHT_TILES = 3

        /** How far in from the end of the floor the gate stands. */
        const val EXIT_INSET_TILES = 4
    }
}
