package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.LevelDifficulty
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.physics.WorldPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldPhysicsTest {

    private val step = 1f / 60f

    private fun levelOf(vararg blocks: PlatformBlock) = Level(
        id = 0,
        name = "test",
        width = 400f,
        height = 300f,
        blocks = blocks.toList(),
        playerSpawn = Vec2.ZERO,
        spawnPoints = emptyList(),
        waveQuota = 1,
        difficulty = LevelDifficulty(),
        exit = Aabb(380f, 32f, 32f, 48f),
    )

    private val floor = PlatformBlock(Aabb(0f, 0f, 400f, 32f), BlockKind.SOLID)
    private val ledge = PlatformBlock(Aabb(100f, 120f, 96f, 8f), BlockKind.ONE_WAY)
    private val wall = PlatformBlock(Aabb(200f, 32f, 16f, 64f), BlockKind.SOLID)

    @Test
    fun `a falling body is stopped on top of solid ground`() {
        val result = WorldPhysics.move(
            position = Vec2(50f, 34f),
            size = 14f,
            velocity = Vec2(0f, -300f),
            dt = step,
            level = levelOf(floor),
        )

        assertEquals(32f, result.position.y, 0.001f)
        assertEquals(0f, result.velocity.y, 0.001f)
        assertTrue(result.onGround)
    }

    @Test
    fun `a body moving into a wall stops flush against it`() {
        val result = WorldPhysics.move(
            position = Vec2(180f, 32f),
            size = 14f,
            velocity = Vec2(2000f, 0f),
            dt = step,
            level = levelOf(floor, wall),
        )

        assertEquals(wall.bounds.left - 14f, result.position.x, 0.001f)
        assertEquals(0f, result.velocity.x, 0.001f)
    }

    @Test
    fun `a one-way ledge catches a body falling onto it`() {
        val result = WorldPhysics.move(
            position = Vec2(130f, 129f),
            size = 14f,
            velocity = Vec2(0f, -200f),
            dt = step,
            level = levelOf(floor, ledge),
        )

        assertEquals(ledge.bounds.top, result.position.y, 0.001f)
        assertTrue(result.onGround)
    }

    @Test
    fun `a one-way ledge lets a body jump up through it`() {
        val result = WorldPhysics.move(
            position = Vec2(130f, 112f),
            size = 14f,
            velocity = Vec2(0f, 300f),
            dt = step,
            level = levelOf(floor, ledge),
        )

        assertTrue("expected to pass through", result.position.y > 112f)
        assertEquals(300f, result.velocity.y, 0.001f)
        assertFalse(result.onGround)
    }

    @Test
    fun `a one-way ledge does not block sideways movement`() {
        val result = WorldPhysics.move(
            position = Vec2(90f, 122f),
            size = 14f,
            velocity = Vec2(600f, 0f),
            dt = step,
            level = levelOf(floor, ledge),
        )

        assertTrue("expected free horizontal travel", result.position.x > 95f)
    }

    @Test
    fun `a ledge probe sees the drop at the end of a platform`() {
        val level = levelOf(floor, ledge)

        val overSolidGround = WorldPhysics.isLedgeAhead(Vec2(120f, 128f), 14f, direction = 1f, level = level)
        val atTheEdge = WorldPhysics.isLedgeAhead(Vec2(186f, 128f), 14f, direction = 1f, level = level)

        assertFalse(overSolidGround)
        assertTrue(atTheEdge)
    }

    @Test
    fun `a wall probe sees an obstacle before walking into it`() {
        val level = levelOf(floor, wall)

        assertTrue(WorldPhysics.isWallAhead(Vec2(186f, 32f), 14f, direction = 1f, level = level))
        assertFalse(WorldPhysics.isWallAhead(Vec2(120f, 32f), 14f, direction = 1f, level = level))
    }

    @Test
    fun `gravity is clamped to a terminal velocity`() {
        var velocity = Vec2.ZERO
        repeat(600) { velocity = WorldPhysics.applyGravity(velocity, step) }

        assertEquals(
            com.heixss.cubewars.domain.config.GameConfig.MAX_FALL_SPEED,
            velocity.y,
            0.001f,
        )
    }
}
