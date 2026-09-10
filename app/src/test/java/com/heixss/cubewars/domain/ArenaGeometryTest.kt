package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.advanceGame
import com.heixss.cubewars.domain.SimulationTestFixtures.applyIntent
import com.heixss.cubewars.domain.SimulationTestFixtures.levels
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.SimulationTestFixtures.step
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.domain.physics.WorldPhysics
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ties every arena's geometry to what the player can actually do.
 *
 * Level layout and jump tuning live in different files and drift apart silently: a block gains a
 * tile, or the jump loses a few units of velocity, and suddenly there is cover in the middle of the
 * floor that looks climbable and simply is not — or, now that arenas have an exit at the far end, a
 * pit that cannot be crossed and a gate that can never be reached.
 *
 * Both measurements below come from running the real simulation rather than from re-deriving the
 * kinematics, so retuning gravity or the jump re-measures them for free.
 */
class ArenaGeometryTest {

    private val floorY = GameConfig.TILE * 2f

    private val allLevels: List<Level> = List(levels.levelCount) { levels.loadLevel(it) }

    /** How far a full, held-down jump from the floor actually lifts the player. */
    private fun fullJumpRise(): Float {
        val base = running().step(steps = 5)
        val grounded = base.copy(
            player = base.player.copy(position = Vec2(100f, floorY), onGround = true),
        )

        var state = applyIntent(grounded, GameIntent.HoldJump(true))
        var peak = floorY
        repeat(120) {
            state = advanceGame(state, STEP)
            peak = maxOf(peak, state.player.position.y)
        }
        return peak - floorY
    }

    /** How far a jump taken at full running speed carries the player before they land again. */
    private fun runningJumpReach(): Float {
        // Measured on an empty plain: real arenas have cover to bump into, which would shorten it.
        val flat = flatLevel()
        val base = running().step(steps = 5)
        var state = base.copy(
            level = flat,
            player = base.player.copy(position = Vec2(80f, floorY), velocity = Vec2.ZERO, onGround = true),
        )

        state = applyIntent(state, GameIntent.Move(1f))
        repeat(90) { state = advanceGame(state, STEP) }

        val takeOffX = state.player.position.x
        state = applyIntent(state, GameIntent.HoldJump(true))
        var airborne = false
        repeat(180) {
            state = advanceGame(state, STEP)
            if (!state.player.onGround) airborne = true
            if (airborne && state.player.onGround) return state.player.position.x - takeOffX
        }
        return state.player.position.x - takeOffX
    }

    private fun flatLevel(): Level = Level(
        id = 0,
        name = "FLAT",
        width = 4000f,
        height = GameConfig.VIEWPORT_HEIGHT,
        blocks = listOf(PlatformBlock(Aabb(0f, 0f, 4000f, floorY), BlockKind.SOLID)),
        playerSpawn = Vec2(80f, floorY),
        // No spawn points, so the wave director cannot drop a cube into the measurement.
        spawnPoints = emptyList(),
        waveQuota = 1,
        difficulty = running().level.difficulty,
        exit = Aabb(3900f, floorY, 32f, 48f),
    )

    /** The gaps in a level's floor, in world units, left to right. */
    private fun groundGaps(level: Level): List<Pair<Float, Float>> {
        val runs = level.blocks
            .filter { it.kind == BlockKind.SOLID && it.bounds.bottom <= 0.01f && it.bounds.left >= 0f }
            .filter { it.bounds.left < level.width }
            .sortedBy { it.bounds.left }
        return runs.zipWithNext { left, right -> left.bounds.right to right.bounds.left }
            .filter { (from, to) -> to - from > 0.01f }
    }

    @Test
    fun `every piece of floor cover is climbable`() {
        val rise = fullJumpRise()

        val unclimbable = allLevels.flatMap { level ->
            level.blocks
                .filter { it.kind == BlockKind.SOLID }
                .filter { it.bounds.bottom >= floorY - 0.01f }
                .filter { it.bounds.left >= 0f && it.bounds.left < level.width }
                .filter { it.bounds.top - floorY > rise }
                .map { level.name to it.bounds.left / GameConfig.TILE }
        }

        assertTrue(
            "a full jump rises $rise, so nothing standing on the floor may be taller than that. " +
                "A solid the player cannot get over is a wall across the only route to the gate: $unclimbable",
            unclimbable.isEmpty(),
        )
    }

    @Test
    fun `the tiers can be reached by climbing the floor cover`() {
        val rise = fullJumpRise()

        for (level in allLevels) {
            val cover = level.blocks
                .filter { it.kind == BlockKind.SOLID && it.bounds.bottom >= floorY - 0.01f }
                .filter { it.bounds.top - floorY <= rise }
            assertTrue("${level.name} needs climbable cover", cover.isNotEmpty())

            // The lowest ledge sits above a standing jump, so the cover is the only way up.
            val lowestLedge = level.blocks.filter { it.kind == BlockKind.ONE_WAY }.minOf { it.bounds.top }
            val highestCover = cover.maxOf { it.bounds.top }
            assertTrue(
                "${level.name}: the lowest ledge at $lowestLedge is unreachable from cover " +
                    "topping out at $highestCover",
                lowestLedge - highestCover <= rise,
            )
        }
    }

    /**
     * The complaint that started all this: the top platforms could not be reached.
     *
     * A tier is only worth building if the tier below it is a jump away. This walks the whole stack —
     * floor, then cover, then each tier of ledges in turn — and insists every step up is one the
     * player can actually make, using the rise measured from the real simulation rather than a number
     * anybody typed in.
     */
    @Test
    fun `every tier is one jump above the one below it, all the way to the top`() {
        val rise = fullJumpRise()

        for (level in allLevels) {
            val surfaces = buildList {
                add(floorY)
                level.blocks
                    .filter { it.kind == BlockKind.SOLID && it.bounds.bottom >= floorY - 0.01f }
                    .filter { it.bounds.left >= 0f && it.bounds.left < level.width }
                    .forEach { add(it.bounds.top) }
                level.blocks.filter { it.kind == BlockKind.ONE_WAY }.forEach { add(it.bounds.top) }
            }.distinct().sorted()

            val unreachable = surfaces.zipWithNext { below, above -> below to above }
                .filter { (below, above) -> above - below > rise }

            assertTrue(
                "${level.name}: a full jump rises $rise, so no surface may sit further than that " +
                    "above the nearest one below it. These steps cannot be made: $unreachable",
                unreachable.isEmpty(),
            )
        }
    }

    @Test
    fun `every pit can be jumped`() {
        val reach = runningJumpReach()
        // A quarter of a tile of slack: landing on the very corner pixel is not a crossing.
        val crossable = reach - GameConfig.TILE / 4f

        val chasms = allLevels.flatMap { level ->
            groundGaps(level)
                .filter { (from, to) -> to - from > crossable }
                .map { (from, to) -> "${level.name} @ ${from / GameConfig.TILE}..${to / GameConfig.TILE} tiles" }
        }

        assertTrue(
            "a running jump carries $reach units, so no pit may be wider than $crossable. " +
                "These cannot be crossed: $chasms",
            chasms.isEmpty(),
        )
    }

    @Test
    fun `no floor cover stands close enough to a pit to launch the player into it`() {
        // Running off a two-tile block carries the player about thirty units before they land, so
        // cover any nearer than this to a hole is a ramp pointing straight down it.
        val clearance = GameConfig.TILE * 3f

        val ramps = allLevels.flatMap { level ->
            val cover = level.blocks
                .filter { it.kind == BlockKind.SOLID && it.bounds.bottom >= floorY - 0.01f }
                .filter { it.bounds.left >= 0f && it.bounds.left < level.width }

            cover.mapNotNull { block ->
                val pitAhead = groundGaps(level).firstOrNull { (from, _) -> from >= block.bounds.right }
                val supported = level.blocks.any {
                    it.kind == BlockKind.SOLID &&
                        it.bounds.bottom <= 0.01f &&
                        it.bounds.left <= block.bounds.left &&
                        it.bounds.right >= block.bounds.right
                }
                when {
                    !supported -> "${level.name}: cover at ${block.bounds.left / GameConfig.TILE} hangs over a pit"
                    pitAhead != null && pitAhead.first - block.bounds.right < clearance ->
                        "${level.name}: cover at ${block.bounds.left / GameConfig.TILE} is " +
                            "${(pitAhead.first - block.bounds.right) / GameConfig.TILE} tiles from a pit"
                    else -> null
                }
            }
        }

        assertTrue("$ramps", ramps.isEmpty())
    }

    @Test
    fun `every arena's gate stands on the floor at the far end, in the clear`() {
        for (level in allLevels) {
            val exit = level.exit
            assertTrue("${level.name}: the gate hangs in the air", exit.bottom == floorY)
            assertTrue("${level.name}: the gate is outside the arena", exit.right <= level.width)
            assertTrue(
                "${level.name}: the gate should be at the far end, not at ${exit.left}",
                exit.left > level.width * 0.9f,
            )

            val ground = level.blocks.firstOrNull {
                it.kind == BlockKind.SOLID &&
                    it.bounds.bottom <= 0.01f &&
                    it.bounds.left <= exit.left &&
                    it.bounds.right >= exit.right
            }
            assertTrue("${level.name}: the gate has no floor under it", ground != null)

            val blocked = level.blocks.any { it.kind == BlockKind.SOLID && it.bounds.overlaps(exit) }
            assertTrue("${level.name}: something solid is standing in the gate", !blocked)
        }
    }

    @Test
    fun `the arenas get longer and meaner as the rotation runs`() {
        val widths = allLevels.map { it.width }
        assertTrue("arenas should not shrink as the run goes on: $widths", widths.sorted() == widths)

        val pressure = allLevels.map { it.difficulty.extraEnemies }
        assertTrue("later arenas must not send fewer cubes: $pressure", pressure.sorted() == pressure)

        val intervals = allLevels.map { it.difficulty.spawnIntervalScale }
        assertTrue("later arenas must not spawn slower: $intervals", intervals.sortedDescending() == intervals)

        assertTrue("every arena needs at least one wave", allLevels.all { it.waveQuota >= 1 })
        assertTrue("every arena needs somewhere to spawn", allLevels.all { it.spawnPoints.isNotEmpty() })
    }

    /**
     * Drives the player right through an empty arena, jumping whenever the ground runs out, and
     * reports where they ended up.
     *
     * This is the crudest possible pilot — it only ever looks four units ahead of its own feet, so
     * it takes every pit at the last possible moment. If it can get across, a player who can see the
     * pit coming certainly can.
     *
     * It jumps for two reasons only: a wall in the way, or a hole in the floor it is standing on.
     * Stepping off cover it walks, the way a player does, rather than launching itself.
     */
    private fun walkToTheGate(level: Level): GameState {
        val base = running()
        var state = base.copy(
            level = level,
            player = base.player.copy(position = level.playerSpawn, velocity = Vec2.ZERO),
            // An arena whose waves are already won, and empty: this is about the geometry and the
            // gate, not about surviving the crossing.
            wave = base.wave.copy(phase = WavePhase.COMPLETE, clearedInLevel = level.waveQuota),
        )
        state = applyIntent(state, GameIntent.Move(1f))

        var holdJumpFor = 0
        repeat(4000) {
            // The gate is the finish line: taking it is what moves the run on a level.
            if (state.levelNumber > base.levelNumber) return state
            val player = state.player
            if (!player.isAlive) return state

            if (holdJumpFor > 0) {
                holdJumpFor--
                // Held past the apex, so the pilot always gets a full-height jump.
                if (holdJumpFor == 0) state = applyIntent(state, GameIntent.HoldJump(false))
            } else if (player.onGround && needsAJump(player.position, player.size, level)) {
                state = applyIntent(state, GameIntent.HoldJump(true))
                holdJumpFor = 24
            }

            state = advanceGame(state, STEP)
        }
        return state
    }

    private fun needsAJump(position: Vec2, size: Float, level: Level): Boolean {
        if (WorldPhysics.isWallAhead(position, size, 1f, level)) return true
        // Only a hole in the floor is worth jumping. Walking off the top of cover is just walking.
        val standingOnTheFloor = position.y <= floorY + 0.01f
        return standingOnTheFloor && WorldPhysics.isLedgeAhead(position, size, 1f, level)
    }

    @Test
    fun `the gate can be walked to from the spawn in every arena`() {
        for (level in allLevels) {
            assertTrue(
                "${level.name}: the player starts to the right of its own gate",
                level.playerSpawn.x < level.exit.left,
            )

            val ended = walkToTheGate(level)
            assertTrue(
                "${level.name}: a player who just holds right ends up at ${ended.player.position} " +
                    "instead of stepping through the gate at ${level.exit.left}..${level.exit.right}. " +
                    "The arena cannot be finished.",
                ended.levelNumber > 1,
            )
        }
    }
}
