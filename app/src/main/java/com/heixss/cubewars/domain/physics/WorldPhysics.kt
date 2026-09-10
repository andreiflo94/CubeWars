package com.heixss.cubewars.domain.physics

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.Vec2

/** Outcome of sweeping a body through the level for one step. */
data class MoveResult(
    val position: Vec2,
    val velocity: Vec2,
    val onGround: Boolean,
)

/**
 * Tile-platformer collision: axis-separated, discrete, and deliberately simple.
 *
 * Horizontal movement is resolved first, then vertical, each against the level's blocks. At the
 * speeds this game uses (a bullet covers ~5 units per step, bodies far less) discrete resolution
 * never tunnels, and it produces the crisp "stop dead against the wall" feel a platformer wants.
 */
object WorldPhysics {

    /**
     * Moves a square body of [size] from [position] with [velocity] for [dt] seconds, stopping it
     * against level geometry.
     *
     * @param dropThrough when true the body ignores one-way platforms, which is how dropping down
     *   through a ledge works.
     */
    fun move(
        position: Vec2,
        size: Float,
        velocity: Vec2,
        dt: Float,
        level: Level,
        dropThrough: Boolean = false,
    ): MoveResult {
        var x = position.x
        var y = position.y
        var vx = velocity.x
        var vy = velocity.y
        var onGround = false

        // --- Horizontal ---------------------------------------------------------------------
        x += vx * dt
        if (vx != 0f) {
            for (block in level.blocks) {
                if (block.kind != BlockKind.SOLID) continue
                val body = Aabb(x, y, size, size)
                if (!body.overlaps(block.bounds)) continue
                x = if (vx > 0f) block.bounds.left - size else block.bounds.right
                vx = 0f
            }
        }

        // --- Vertical -----------------------------------------------------------------------
        val previousBottom = y
        y += vy * dt
        for (block in level.blocks) {
            if (!isVerticallySolid(block, vy, previousBottom, dropThrough)) continue
            val body = Aabb(x, y, size, size)
            if (!body.overlaps(block.bounds)) continue
            if (vy <= 0f) {
                y = block.bounds.top
                onGround = true
            } else {
                y = block.bounds.bottom - size
            }
            vy = 0f
        }

        return MoveResult(Vec2(x, y), Vec2(vx, vy), onGround)
    }

    /**
     * A one-way platform only stops a body that is falling and whose feet started at or above the
     * platform surface — otherwise you would clip onto it while jumping up through it.
     */
    private fun isVerticallySolid(
        block: PlatformBlock,
        verticalVelocity: Float,
        previousBottom: Float,
        dropThrough: Boolean,
    ): Boolean = when (block.kind) {
        BlockKind.SOLID -> true
        BlockKind.ONE_WAY ->
            !dropThrough && verticalVelocity <= 0f && previousBottom >= block.bounds.top - 0.5f
    }

    /** Applies gravity for one step, clamped to terminal velocity. */
    fun applyGravity(velocity: Vec2, dt: Float, scale: Float = 1f): Vec2 =
        velocity.withY((velocity.y + GameConfig.GRAVITY * scale * dt).coerceAtLeast(GameConfig.MAX_FALL_SPEED))

    /** True when the ground under [position] runs out within [probeDistance] ahead. */
    fun isLedgeAhead(position: Vec2, size: Float, direction: Float, level: Level, probeDistance: Float = 4f): Boolean {
        val probeX = if (direction > 0f) position.x + size + probeDistance else position.x - probeDistance
        val probe = Aabb(probeX, position.y - 2f, 1f, 2f)
        return level.blocks.none { it.bounds.overlaps(probe) }
    }

    /** True when a wall blocks movement in [direction]. */
    fun isWallAhead(position: Vec2, size: Float, direction: Float, level: Level, probeDistance: Float = 2f): Boolean {
        val probeX = if (direction > 0f) position.x + size else position.x - probeDistance
        val probe = Aabb(probeX, position.y + 1f, probeDistance, size - 2f)
        return level.solidBlocks.any { it.bounds.overlaps(probe) }
    }

    /** True when the point sits inside solid geometry — used to despawn bullets that hit a wall. */
    fun collidesWithSolid(body: Aabb, level: Level): Boolean =
        level.solidBlocks.any { it.bounds.overlaps(body) }
}
