package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.advanceGame
import com.heixss.cubewars.domain.SimulationTestFixtures.applyIntent
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.SimulationTestFixtures.step
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.input.GameIntent
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMovementTest {

    /** Surface of the arena floor: the ground is two tiles deep. */
    private val FLOOR_Y = GameConfig.TILE * 2f

    @Test
    fun `a player dropped into the arena falls until it comes to rest on something`() {
        val base = running()
        val airborne = base.copy(
            player = base.player.copy(position = Vec2(550f, 200f), onGround = false),
        )

        val landed = airborne.step(steps = 60)

        assertTrue("expected the player to be standing", landed.player.onGround)
        assertEquals(0f, landed.player.velocity.y, 0.001f)
        // Whatever it came to rest on, its feet must be flush with that surface rather than sunk
        // into it or hovering above it.
        val restingOnASurface = base.level.blocks.any { block ->
            kotlin.math.abs(block.bounds.top - landed.player.position.y) < 0.01f
        }
        assertTrue("player settled at y=${landed.player.position.y}", restingOnASurface)
    }

    @Test
    fun `holding right accelerates the player rightwards and turns it to face that way`() {
        val settled = running().step(steps = 5)

        val moving = applyIntent(settled, GameIntent.Move(1f)).step(steps = 20)

        assertTrue(moving.player.velocity.x > 0f)
        assertEquals(Facing.RIGHT, moving.player.facing)
        assertTrue(moving.player.position.x > settled.player.position.x)
    }

    @Test
    fun `horizontal speed is capped no matter how long the button is held`() {
        var state = applyIntent(running(), GameIntent.Move(1f))
        state = state.step(steps = 240)

        assertTrue(state.player.velocity.x <= GameConfig.PLAYER_MAX_SPEED + 0.01f)
    }

    @Test
    fun `pressing jump while grounded launches the player`() {
        val grounded = running().step(steps = 5)
        assertTrue(grounded.player.onGround)

        val jumped = applyIntent(grounded, GameIntent.HoldJump(true)).step()

        assertTrue("expected upward velocity", jumped.player.velocity.y > 0f)
    }

    @Test
    fun `coyote time lets a jump land just after walking off a ledge`() {
        val base = running()
        val justLeftTheLedge = base.copy(
            player = base.player.copy(
                position = Vec2(300f, 120f),
                onGround = false,
                coyoteTimer = GameConfig.COYOTE_TIME / 2f,
            ),
        )

        val jumped = applyIntent(justLeftTheLedge, GameIntent.HoldJump(true)).step()

        assertTrue(jumped.player.velocity.y > 0f)
    }

    @Test
    fun `a jump is refused once coyote time and the air jump are both gone`() {
        val base = running()
        val longGone = base.copy(
            player = base.player.copy(
                position = Vec2(300f, 120f),
                onGround = false,
                coyoteTimer = 0f,
                airJumpsRemaining = 0,
            ),
        )

        val stepped = applyIntent(longGone, GameIntent.HoldJump(true)).step()

        assertTrue("expected the player to still be falling", stepped.player.velocity.y < 0f)
    }

    @Test
    fun `a press in mid-air spends the second jump, and the next press does nothing`() {
        // High over an empty plain, so the cube cannot land and quietly earn its jump back.
        val plain = SimulationTestFixtures.onFlatGround()
        val falling = plain.copy(
            player = plain.player.copy(
                position = Vec2(300f, 220f),
                velocity = Vec2(0f, -120f),
                onGround = false,
                coyoteTimer = 0f,
            ),
        )

        val second = applyIntent(falling, GameIntent.HoldJump(true)).step()
        assertTrue("the air jump should have fired", second.player.velocity.y > 0f)
        assertEquals(0, second.player.airJumpsRemaining)

        // Still airborne, with nothing left in hand: a third press is simply ignored, and the cube
        // carries on losing height at exactly the rate gravity says it should.
        val falling2 = second.step(steps = 40)
        assertTrue("expected the cube to be falling again", falling2.player.velocity.y < 0f)
        assertFalse(falling2.player.onGround)

        val third = applyIntent(falling2, GameIntent.HoldJump(true)).step()
        assertTrue("there was no third jump to spend", third.player.velocity.y < falling2.player.velocity.y)
        assertEquals(0, third.player.airJumpsRemaining)
    }

    @Test
    fun `landing hands the second jump back`() {
        val airborne = applyIntent(onFlatGround(), GameIntent.HoldJump(true)).step(steps = 4)
        val spent = applyIntent(airborne, GameIntent.HoldJump(true)).step()
        assertEquals(0, spent.player.airJumpsRemaining)

        val landed = spent.step(steps = 120)

        assertTrue("expected the player back on the ground", landed.player.onGround)
        assertEquals(GameConfig.PLAYER_AIR_JUMPS, landed.player.airJumpsRemaining)
    }

    @Test
    fun `the two jumps together clear a tier the first one alone cannot`() {
        val single = peakHeight(applyIntent(onFlatGround(), GameIntent.HoldJump(true)))

        var doubled = applyIntent(onFlatGround(), GameIntent.HoldJump(true)).step(steps = 22)
        doubled = applyIntent(doubled, GameIntent.HoldJump(false))
        doubled = applyIntent(doubled, GameIntent.HoldJump(true))

        assertTrue(
            "a second jump should get the player meaningfully higher than one: " +
                "$single then ${peakHeight(doubled)}",
            peakHeight(doubled) > single + GameConfig.TILE,
        )
    }

    @Test
    fun `a buffered jump fires on the frame the player touches down`() {
        val base = running()
        val aboutToLand = base.copy(
            player = base.player.copy(
                position = Vec2(64f, GameConfig.TILE * 2f + 1f),
                velocity = Vec2(0f, -80f),
                onGround = false,
                // Pressed a moment too early, while still in the air.
                jumpBufferTimer = GameConfig.JUMP_BUFFER_TIME,
            ),
        )

        val stepped = applyIntent(aboutToLand, GameIntent.HoldJump(true)).step(steps = 2)

        assertTrue("the early press should not have been thrown away", stepped.player.velocity.y > 0f)
    }

    @Test
    fun `releasing jump early clips the arc short`() {
        val grounded = running().step(steps = 5)
        val held = applyIntent(grounded, GameIntent.HoldJump(true)).step(steps = 3)
        val released = applyIntent(held, GameIntent.HoldJump(false)).step()

        assertTrue(released.player.velocity.y < held.player.velocity.y)
        assertTrue(
            released.player.velocity.y <=
                GameConfig.PLAYER_JUMP_VELOCITY * GameConfig.JUMP_CUT_MULTIPLIER + 0.01f,
        )
    }

    /** The highest the player's feet get from [start] onward, in world units. */
    private fun peakHeight(start: GameState, steps: Int = 120): Float {
        var state = start
        var peak = state.player.position.y
        repeat(steps) {
            state = advanceGame(state, STEP)
            peak = maxOf(peak, state.player.position.y)
        }
        return peak
    }

    /** A cube standing on an empty plain, clear of any obstacle. */
    private fun onFlatGround(): GameState = SimulationTestFixtures.onFlatGround()

    @Test
    fun `a jump buffered in the air launches at full height even if the button was let go`() {
        // Pressing a moment before landing and lifting off again before touchdown is what happens
        // every time a player mashes jump on approach. The buffered jump has not started when the
        // release arrives, so the release must not be allowed to shrink it.
        val grounded = onFlatGround()
        val falling = grounded.copy(
            player = grounded.player.copy(
                position = grounded.player.position + Vec2(0f, 6f),
                velocity = Vec2(0f, -80f),
                onGround = false,
                // Long past the ledge: this jump can only come out of the buffer, on touchdown.
                coyoteTimer = 0f,
            ),
        )

        // With the air jump already spent, the buffer is the only thing that can produce this jump —
        // which is exactly the case this test exists to protect.
        val exhausted = falling.copy(player = falling.player.copy(airJumpsRemaining = 0))
        var mashed = applyIntent(exhausted, GameIntent.HoldJump(true))
        mashed = applyIntent(mashed, GameIntent.HoldJump(false))

        val held = applyIntent(grounded, GameIntent.HoldJump(true))
        assertEquals(
            "a buffered jump should reach the same height as a held one",
            peakHeight(held),
            peakHeight(mashed),
            0.01f,
        )
    }

    @Test
    fun `a full jump clears the two-tile blocks the arena puts in the way`() {
        val rise = peakHeight(applyIntent(onFlatGround(), GameIntent.HoldJump(true))) - FLOOR_Y

        assertTrue("a full jump rose only $rise", rise > GameConfig.TILE * 2f)
    }

    @Test
    fun `tapping jump still gives a lower hop than holding it`() {
        val held = applyIntent(onFlatGround(), GameIntent.HoldJump(true))

        var tapped = applyIntent(onFlatGround(), GameIntent.HoldJump(true)).step(steps = 5)
        tapped = applyIntent(tapped, GameIntent.HoldJump(false))

        assertTrue(
            "the tap should not reach the full height",
            peakHeight(tapped) < peakHeight(held) - 1f,
        )
    }

    @Test
    fun `falling out of the world costs a life and returns the player to the spawn`() {
        val base = running()
        val doomed = base.copy(
            player = base.player.copy(
                position = Vec2(300f, GameConfig.KILL_PLANE_Y - 1f),
                velocity = Vec2(0f, -100f),
            ),
        )

        val respawned = doomed.step()

        assertEquals(base.player.health - 1, respawned.player.health)
        assertEquals(base.level.playerSpawn, respawned.player.position)
        assertTrue(respawned.player.isInvulnerable)
    }

    @Test
    fun `holding fire produces bullets at the configured cadence and no faster`() {
        val armed = applyIntent(running(), GameIntent.HoldFire(true))

        val afterOneStep = armed.step()
        assertEquals(1, afterOneStep.projectiles.size)

        // Well within the cooldown: still exactly one bullet in flight.
        val stillCoolingDown = afterOneStep.step(
            steps = (GameConfig.PLAYER_FIRE_INTERVAL / STEP).toInt() - 2,
        )
        assertEquals(1, stillCoolingDown.projectiles.size)

        val afterCooldown = stillCoolingDown.step(steps = 3)
        assertEquals(2, afterCooldown.projectiles.size)
    }

    @Test
    fun `bullets leave in the direction the player is facing`() {
        var state = applyIntent(running(), GameIntent.Move(-1f))
        state = state.step(steps = 10)
        state = applyIntent(state, GameIntent.HoldFire(true)).step()

        val bullet = state.projectiles.first()
        assertTrue("expected a leftward bullet", bullet.velocity.x < 0f)
        assertFalse(state.player.facing == Facing.RIGHT)
    }

    /** How much one step of flight takes off a player bullet's climb. */
    private val bulletDropPerStep = -GameConfig.GRAVITY * GameConfig.PLAYER_BULLET_GRAVITY_SCALE * STEP

    @Test
    fun `a free aim sends the bullet along the aimed direction, not the facing`() {
        var state = applyIntent(running(), GameIntent.Aim(Vec2(-3f, 3f)))
        state = applyIntent(state, GameIntent.HoldFire(true)).step()

        val bullet = state.projectiles.first()
        // The aim was up and to the left at 45 degrees, so the two components leave the barrel
        // matching in magnitude — and by the time this is read, exactly one step of gravity has
        // already been taken off the vertical one. Nothing touches the horizontal.
        val launchSpeed = GameConfig.PLAYER_BULLET_SPEED / kotlin.math.sqrt(2f)
        assertEquals(-launchSpeed, bullet.velocity.x, 0.01f)
        assertEquals(launchSpeed - bulletDropPerStep, bullet.velocity.y, 0.01f)
        assertTrue("expected a still-rising bullet", bullet.velocity.y > 0f)
    }

    @Test
    fun `bullets fall, and keep falling`() {
        var state = applyIntent(running(), GameIntent.Aim(Vec2(1f, 0f)))
        state = applyIntent(state, GameIntent.HoldFire(true)).step()

        val muzzleHeight = state.projectiles.first().position.y
        var previousDrop = 0f
        repeat(8) {
            state = state.step()
            val bullet = state.projectiles.first()
            val drop = muzzleHeight - bullet.position.y
            assertTrue("a level shot should be losing height, not gaining it", drop > previousDrop)
            previousDrop = drop
        }
        // The horizontal component is untouched by gravity, so the shot still carries.
        assertEquals(GameConfig.PLAYER_BULLET_SPEED, state.projectiles.first().velocity.x, 0.01f)
    }

    @Test
    fun `aiming sideways turns the cube even while it walks the other way`() {
        var state = applyIntent(running(), GameIntent.Move(1f))
        state = applyIntent(state, GameIntent.Aim(Vec2(-1f, 0.2f))).step()

        assertEquals(Facing.LEFT, state.player.facing)
    }

    @Test
    fun `a near-vertical aim leaves the cube facing the way it was`() {
        var state = applyIntent(running(), GameIntent.Move(1f)).step(steps = 4)
        val facingBefore = state.player.facing
        state = applyIntent(state, GameIntent.Move(0f))
        state = applyIntent(state, GameIntent.Aim(Vec2(-0.1f, 1f))).step()

        assertEquals(facingBefore, state.player.facing)
    }

    @Test
    fun `dropping the aim returns the gun to the facing`() {
        var state = applyIntent(running(), GameIntent.Aim(Vec2(0f, 1f))).step()
        state = applyIntent(state, GameIntent.Aim(null))
        state = applyIntent(state, GameIntent.HoldFire(true)).step()

        val bullet = state.projectiles.first()
        // Level out of the barrel: everything it has lost vertically is the one step of gravity it
        // has already flown through.
        assertEquals(-bulletDropPerStep, bullet.velocity.y, 0.001f)
        assertTrue("expected a rightward bullet", bullet.velocity.x > 0f)
    }
}
