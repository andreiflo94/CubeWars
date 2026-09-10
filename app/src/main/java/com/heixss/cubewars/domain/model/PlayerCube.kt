package com.heixss.cubewars.domain.model

import com.heixss.cubewars.domain.config.GameConfig

/** The cube the player drives. */
data class PlayerCube(
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val size: Float = GameConfig.PLAYER_SIZE,
    val facing: Facing = Facing.RIGHT,
    /** Unit vector the gun points along — free-aimed, so it is not tied to [facing]. */
    val aim: Vec2 = Vec2(Facing.RIGHT.sign, 0f),
    val health: Int = GameConfig.PLAYER_MAX_HEALTH,
    val maxHealth: Int = GameConfig.PLAYER_MAX_HEALTH,
    val onGround: Boolean = false,
    val coyoteTimer: Float = 0f,
    val jumpBufferTimer: Float = 0f,
    /**
     * Mid-air launches still in hand. Refilled the instant the cube touches anything solid, so it is
     * a property of being airborne rather than a resource to manage.
     */
    val airJumpsRemaining: Int = GameConfig.PLAYER_AIR_JUMPS,
    val fireCooldown: Float = 0f,
    val invulnerabilityTimer: Float = 0f,
    val hitFlashTimer: Float = 0f,
    /** Non-uniform scale used for landing/jump juice: `x` is width factor, `y` is height factor. */
    val squash: Vec2 = Vec2(1f, 1f),
    /** Seconds the player has been alive; drives idle bobbing in the renderer. */
    val animationTime: Float = 0f,
) {
    val bounds: Aabb get() = Aabb.square(position, size)

    val isAlive: Boolean get() = health > 0

    val isInvulnerable: Boolean get() = invulnerabilityTimer > 0f

    /** World point projectiles leave from: the cube's centre pushed out along [aim]. */
    val muzzle: Vec2
        get() = Vec2(position.x + size / 2f, position.y + size * 0.55f) + aim * (size * 0.6f)

    /**
     * Cuts a rising jump short, which is how the button gives a variable jump height.
     *
     * Only a jump already on its way up is affected: a cube that is falling, standing still, or
     * moving slower than the cut speed is returned untouched.
     */
    fun clipRisingJump(): PlayerCube {
        val cutVelocity = GameConfig.PLAYER_JUMP_VELOCITY * GameConfig.JUMP_CUT_MULTIPLIER
        return if (velocity.y > cutVelocity) copy(velocity = velocity.withY(cutVelocity)) else this
    }
}
