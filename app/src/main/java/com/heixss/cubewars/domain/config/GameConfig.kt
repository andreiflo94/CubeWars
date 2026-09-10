package com.heixss.cubewars.domain.config

/**
 * Every tuning knob of the simulation, in world units.
 *
 * One world unit is one virtual pixel; the renderer scales a 480x270 virtual screen to whatever the
 * device actually has. Keeping the numbers here (and out of the use cases) means the feel of the
 * game can be retuned without touching a single rule.
 */
object GameConfig {

    /** Virtual screen the world is authored against. */
    const val VIEWPORT_WIDTH = 480f
    const val VIEWPORT_HEIGHT = 270f

    /** Edge length of one level tile. */
    const val TILE = 16f

    // --- World -------------------------------------------------------------------------------
    const val GRAVITY = -1000f
    const val MAX_FALL_SPEED = -480f

    /** Falling below this y kills whatever fell. */
    const val KILL_PLANE_Y = -40f

    // --- Player ------------------------------------------------------------------------------
    const val PLAYER_SIZE = 14f
    const val PLAYER_MAX_HEALTH = 5
    const val PLAYER_MAX_SPEED = 122f
    const val PLAYER_GROUND_ACCELERATION = 1100f
    const val PLAYER_AIR_ACCELERATION = 620f
    const val PLAYER_GROUND_FRICTION = 1400f
    const val PLAYER_AIR_FRICTION = 260f

    /**
     * Enough to rise 69 units — comfortably over the 48-unit step between one tier of ledges and the
     * next, which the old 305 was not. A jump that cannot reach the next platform up is a jump the
     * level geometry silently forbids, and `ArenaGeometryTest` measures this rather than trusting it.
     */
    const val PLAYER_JUMP_VELOCITY = 372f

    /** How many times the cube may launch again while already airborne. */
    const val PLAYER_AIR_JUMPS = 1

    /**
     * The second jump is deliberately weaker than the first: it is a course correction and a way onto
     * the top tier, not a replacement for reading the platforms.
     */
    const val PLAYER_AIR_JUMP_VELOCITY = 300f

    /** Grace period after walking off a ledge during which a jump still fires. */
    const val COYOTE_TIME = 0.10f

    /** How long a jump press is remembered while the player is still airborne. */
    const val JUMP_BUFFER_TIME = 0.12f

    /** Upward velocity retained when the jump button is released early. */
    const val JUMP_CUT_MULTIPLIER = 0.42f

    const val PLAYER_FIRE_INTERVAL = 0.17f

    /**
     * How much sideways lean a free aim needs before it turns the cube. Below this the aim is near
     * vertical and flipping the sprite would just make it twitch.
     */
    const val AIM_FACING_THRESHOLD = 0.2f

    const val PLAYER_INVULNERABILITY_TIME = 1.1f
    const val PLAYER_KNOCKBACK = 130f

    // --- Projectiles -------------------------------------------------------------------------
    const val PLAYER_BULLET_SPEED = 360f
    const val PLAYER_BULLET_SIZE = 5f
    const val PLAYER_BULLET_DAMAGE = 1
    const val BULLET_LIFETIME = 2.0f

    /**
     * Bullets are cubes, and cubes fall. Every projectile carries its own [Projectile.gravityScale]
     * so a sniper's round can stay flat while a mortar shell arcs over cover; this is what the
     * player's gun uses.
     *
     * The figure is set by the shot the player takes most: a flat one, from a cube standing on the
     * floor, whose muzzle is only five units clear of it. At 0.08 that shot carries about eight
     * tiles before it buries itself — just past [WAVE_MIN_SPAWN_DISTANCE], so a cube that has only
     * just arrived can still be answered without aiming — while a shot across the arena drops most
     * of a body height and has to be laid above its target. Any harder and the level shot stops
     * reaching anything the wave director spawns; any softer and the drop is decoration. The renderer
     * draws the predicted arc, so this is information rather than a surprise.
     */
    const val PLAYER_BULLET_GRAVITY_SCALE = 0.08f

    // --- Weak spots --------------------------------------------------------------------------
    /** What a shot into an archetype's weakness is multiplied by. */
    const val WEAKNESS_DAMAGE_MULTIPLIER = 3

    /** Fraction of a cube's height that counts as its head, and as its underbelly. */
    const val WEAK_SPOT_BAND = 0.34f

    /** Inside this distance a shot counts as point blank; beyond it, as a long shot. */
    const val POINT_BLANK_RANGE = 58f
    const val LONG_SHOT_RANGE = 190f

    /** A bullet falling at least this fast counts as plunging fire. */
    const val PLUNGING_FIRE_SPEED = 40f

    /** Extra score for finding a weak spot, as a fraction of the cube's own value. */
    const val WEAKNESS_SCORE_BONUS = 0.5f

    // --- Waves -------------------------------------------------------------------------------
    const val WAVE_BASE_ENEMY_COUNT = 3
    const val WAVE_ENEMY_GROWTH = 2
    const val WAVE_MAX_ENEMY_COUNT = 18
    const val WAVE_MAX_ALIVE = 7
    const val WAVE_SPAWN_INTERVAL = 0.85f
    const val WAVE_INTERMISSION = 2.6f

    // --- Levels ------------------------------------------------------------------------------
    /** Quiet beat after stepping through a gate, before the new arena starts sending cubes. */
    const val LEVEL_INTRO_TIME = 3.2f

    /** Score for clearing an arena, multiplied by how many have been cleared. */
    const val LEVEL_CLEAR_BONUS = 500

    /** Enemies never spawn closer than this to the player. */
    const val WAVE_MIN_SPAWN_DISTANCE = 110f

    // --- Camera ------------------------------------------------------------------------------
    const val CAMERA_SMOOTHING = 7.5f
    const val CAMERA_LOOK_AHEAD = 34f
    const val CAMERA_SHAKE_DECAY = 4.2f
    const val CAMERA_MAX_SHAKE = 9f

    // --- Feedback ----------------------------------------------------------------------------
    const val HIT_FLASH_TIME = 0.12f

    /** How long a cube keeps glowing after its weak spot was found. */
    const val WEAK_SPOT_FLASH_TIME = 0.3f
    const val SQUASH_RECOVERY = 9f
    const val PARTICLE_GRAVITY_SCALE = 0.55f
    const val MAX_PARTICLES = 220

    /** Simulation step. The clock feeds exactly this delta to every use case. */
    const val FIXED_TIME_STEP = 1f / 60f
}
