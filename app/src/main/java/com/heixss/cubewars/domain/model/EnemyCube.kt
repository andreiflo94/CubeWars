package com.heixss.cubewars.domain.model

/**
 * How a hostile cube gets around. One style per archetype, and `UpdateEnemiesUseCase` has exactly
 * one branch per style — so a new enemy is usually a new row of numbers and no new code at all.
 */
enum class MovementStyle {
    /** Walks its platform, turns at walls and ledges, advances once it has eyes on the player. */
    PATROL,

    /** Bolted down. Turns to face, never leaves. */
    ROOTED,

    /** Bounces toward the player in repeated hops. */
    HOP,

    /** Ignores gravity and swims through the air toward the player's height. */
    HOVER,

    /** Patrols until it sees the player, then commits to a flat-out sprint. */
    CHARGE,
}

/**
 * What one archetype does better than the rest.
 *
 * Powers are read by the rules, never switched on by type: `UpdateEnemiesUseCase` asks "does this
 * cube lead its target?" rather than "is this cube a sniper?". Handing an existing archetype a new
 * trick is therefore a one-line change to its power set.
 */
enum class EnemyPower {
    /** Launches far higher than a normal hop, so ledges are no protection. */
    HIGH_JUMP,

    /** Leads the player's movement instead of shooting where they were. */
    MARKSMAN,

    /** Lobs its shot on a high arc, which lets it drop rounds over cover and onto ledges. */
    ARCING_SHOT,

    /** Nearly flat, nearly driftless rounds, and no aim jitter worth the name. */
    STEADY_AIM,

    /**
     * A shield across its facing. Rounds that arrive flat from the front are turned aside entirely —
     * but the shield has no lid, so anything falling steeply into it lands as normal. Shooting over
     * a shield is the reason bullets were given gravity in the first place.
     */
    FRONTAL_SHIELD,

    /** Shrugs off the knockback a hit normally imparts. */
    UNFLINCHING,
}

/**
 * The one thing an archetype cannot take.
 *
 * Every hostile cube has exactly one, and finding it multiplies the damage of the shot that found
 * it. Three of them are positional — where on the cube the bullet landed — and the rest are about
 * how the shot was taken, which is what stops the answer to every enemy from being "stand still and
 * hold fire".
 */
enum class EnemyWeakness(
    /** Shown on the title card, and what the weak-spot marker in the world is drawn from. */
    val label: String,
) {
    /** Nothing in particular. Reserved for anything that should just be shot. */
    NONE("SOLID"),

    /** The top band of the cube. */
    HEADSHOT("HEAD"),

    /** The bottom band — shoot upward into it. */
    UNDERBELLY("BELLY"),

    /** The side it is not facing. */
    BACKSTAB("BACK"),

    /** Rounds that arrive already falling, which is what a lobbed or long shot does. */
    PLUNGING_FIRE("PLUNGE"),

    /** Shot from close enough to touch. */
    POINT_BLANK("CLOSE"),

    /** Shot from further off than it can answer. */
    LONG_SHOT("RANGE"),

    /** Shot by a player who is off the ground at the moment of firing. */
    AIRBORNE("AIRSHOT"),
}

/**
 * The kinds of hostile cube, and every number that distinguishes them.
 *
 * Adding a new enemy means adding an entry here and a colour in `Palette` — the spawner, combat,
 * scoring, steering and the renderer all read these properties rather than switching on type. Only a
 * genuinely new [MovementStyle] costs a branch anywhere.
 */
enum class EnemyArchetype(
    val size: Float,
    val maxHealth: Int,
    val moveSpeed: Float,
    val movement: MovementStyle,
    val powers: Set<EnemyPower>,
    val weakness: EnemyWeakness,
    val fireInterval: Float,
    val bulletSpeed: Float,
    val bulletDamage: Int,
    val bulletsPerShot: Int,
    /** Total angular spread of a burst, in radians. */
    val spread: Float,
    /** How hard this cube's rounds fall. Zero is a laser; one is a thrown rock. */
    val bulletGravityScale: Float,
    val contactDamage: Int,
    val scoreValue: Int,
    /** How far away this enemy will still take a shot at the player. */
    val sightRange: Float,
    /** How far above or below itself it will still take the shot. */
    val firingArc: Float,
    val affectedByGravity: Boolean,
    /** Upward launch speed for [MovementStyle.HOP]. */
    val jumpVelocity: Float = 0f,
    /**
     * How long the player has to stand behind this cube before it comes round to face them. Zero
     * means it turns on the spot — anything with a back worth stabbing wants a real number here.
     */
    val traverseTime: Float = 0f,
    /** Weight in the wave composition roll; higher means more common. */
    val spawnWeight: Int,
    /** Wave index from which this archetype may appear. */
    val unlockWave: Int,
) {
    /** Patrols its platform and takes potshots. The bread and butter, and the only honest fight. */
    GRUNT(
        size = 14f,
        maxHealth = 2,
        moveSpeed = 42f,
        movement = MovementStyle.PATROL,
        powers = emptySet(),
        weakness = EnemyWeakness.HEADSHOT,
        fireInterval = 1.55f,
        bulletSpeed = 200f,
        bulletDamage = 1,
        bulletsPerShot = 1,
        spread = 0f,
        bulletGravityScale = 0.1f,
        contactDamage = 1,
        scoreValue = 100,
        sightRange = 210f,
        firingArc = 52f,
        affectedByGravity = true,
        spawnWeight = 5,
        unlockWave = 0,
    ),

    /** Never shoots. Launches half the height of the arena and comes down on your head. */
    HOPPER(
        size = 11f,
        maxHealth = 1,
        moveSpeed = 86f,
        movement = MovementStyle.HOP,
        powers = setOf(EnemyPower.HIGH_JUMP),
        weakness = EnemyWeakness.PLUNGING_FIRE,
        fireInterval = Float.MAX_VALUE,
        bulletSpeed = 0f,
        bulletDamage = 0,
        bulletsPerShot = 0,
        spread = 0f,
        bulletGravityScale = 0f,
        contactDamage = 1,
        scoreValue = 150,
        sightRange = 320f,
        firingArc = 0f,
        affectedByGravity = true,
        jumpVelocity = 390f,
        spawnWeight = 4,
        unlockWave = 1,
    ),

    /** Rooted in place, but its rounds fly flat, fast and far. Get behind it. */
    TURRET(
        size = 18f,
        maxHealth = 4,
        moveSpeed = 0f,
        movement = MovementStyle.ROOTED,
        powers = setOf(EnemyPower.STEADY_AIM, EnemyPower.UNFLINCHING),
        weakness = EnemyWeakness.BACKSTAB,
        fireInterval = 1.05f,
        bulletSpeed = 230f,
        bulletDamage = 1,
        bulletsPerShot = 1,
        spread = 0f,
        bulletGravityScale = 0.05f,
        contactDamage = 1,
        scoreValue = 200,
        sightRange = 320f,
        firingArc = 64f,
        affectedByGravity = true,
        traverseTime = 0.85f,
        spawnWeight = 3,
        unlockWave = 2,
    ),

    /** Slow, plated, and spits a three-way spread. The armour stops at its feet. */
    BRUTE(
        size = 22f,
        maxHealth = 6,
        moveSpeed = 30f,
        movement = MovementStyle.PATROL,
        // The fan of three is [bulletsPerShot] and [spread], not a power: a power is something the
        // rules have to ask about, and nothing has to ask how many rounds are in a burst.
        powers = setOf(EnemyPower.UNFLINCHING),
        weakness = EnemyWeakness.UNDERBELLY,
        fireInterval = 2.1f,
        bulletSpeed = 210f,
        bulletDamage = 2,
        bulletsPerShot = 3,
        spread = 0.55f,
        bulletGravityScale = 0.12f,
        contactDamage = 2,
        scoreValue = 320,
        sightRange = 260f,
        firingArc = 60f,
        affectedByGravity = true,
        spawnWeight = 2,
        unlockWave = 3,
    ),

    /** Outranges everything and shoots where you are going to be. Useless in your face. */
    SNIPER(
        size = 13f,
        maxHealth = 2,
        moveSpeed = 26f,
        movement = MovementStyle.PATROL,
        powers = setOf(EnemyPower.MARKSMAN, EnemyPower.STEADY_AIM),
        weakness = EnemyWeakness.POINT_BLANK,
        fireInterval = 2.4f,
        bulletSpeed = 320f,
        bulletDamage = 2,
        bulletsPerShot = 1,
        spread = 0f,
        bulletGravityScale = 0.02f,
        contactDamage = 1,
        scoreValue = 260,
        sightRange = 430f,
        firingArc = 110f,
        affectedByGravity = true,
        spawnWeight = 3,
        unlockWave = 2,
    ),

    /** Flies. Ignores the floor entirely and settles at whatever height you are not. */
    DRONE(
        size = 12f,
        maxHealth = 2,
        moveSpeed = 64f,
        movement = MovementStyle.HOVER,
        powers = emptySet(),
        weakness = EnemyWeakness.AIRBORNE,
        fireInterval = 1.35f,
        bulletSpeed = 240f,
        bulletDamage = 1,
        bulletsPerShot = 1,
        spread = 0f,
        bulletGravityScale = 0.14f,
        contactDamage = 1,
        scoreValue = 220,
        sightRange = 300f,
        firingArc = 200f,
        affectedByGravity = false,
        spawnWeight = 3,
        unlockWave = 4,
    ),

    /** Lobs a shell over the cover you are hiding behind. Answer it from outside its own range. */
    MORTAR(
        size = 20f,
        maxHealth = 3,
        moveSpeed = 0f,
        movement = MovementStyle.ROOTED,
        powers = setOf(EnemyPower.ARCING_SHOT),
        weakness = EnemyWeakness.LONG_SHOT,
        fireInterval = 2.7f,
        bulletSpeed = 450f,
        bulletDamage = 2,
        // One heavy shell rather than a pattern. A steep arc magnifies a spread out of all
        // proportion — a tenth of a radian at the barrel is half a screen at the top of the arc —
        // so a mortar that fanned its fire would simply never land anything.
        bulletsPerShot = 1,
        spread = 0f,
        bulletGravityScale = 0.7f,
        contactDamage = 1,
        scoreValue = 300,
        sightRange = 210f,
        firingArc = 190f,
        affectedByGravity = true,
        traverseTime = 0.7f,
        spawnWeight = 2,
        unlockWave = 5,
    ),

    /** A shield wall on legs that charges once it sees you. Everything it has is on the front. */
    WARDEN(
        size = 24f,
        maxHealth = 6,
        moveSpeed = 118f,
        movement = MovementStyle.CHARGE,
        powers = setOf(EnemyPower.FRONTAL_SHIELD, EnemyPower.UNFLINCHING),
        weakness = EnemyWeakness.BACKSTAB,
        fireInterval = Float.MAX_VALUE,
        bulletSpeed = 0f,
        bulletDamage = 0,
        bulletsPerShot = 0,
        spread = 0f,
        bulletGravityScale = 0f,
        contactDamage = 2,
        scoreValue = 400,
        sightRange = 300f,
        firingArc = 0f,
        affectedByGravity = true,
        traverseTime = 0.75f,
        spawnWeight = 2,
        unlockWave = 6,
    ),
    ;

    fun has(power: EnemyPower): Boolean = power in powers
}

/** One hostile cube. */
data class EnemyCube(
    val id: Long,
    val archetype: EnemyArchetype,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val facing: Facing = Facing.LEFT,
    val health: Int = archetype.maxHealth,
    val onGround: Boolean = false,
    val fireCooldown: Float = archetype.fireInterval,
    val hitFlashTimer: Float = 0f,
    /**
     * Generic per-archetype timer: the charge between hops for a hopper, the traverse for anything
     * heavy enough that turning round costs it a beat.
     *
     * It starts full rather than empty so a cube that spawns facing the wrong way has to earn its
     * turn like any other — otherwise every heavy cube gets one free instant pivot the moment it
     * materialises, and the blind spot its weakness depends on never opens.
     */
    val stateTimer: Float = archetype.traverseTime,
    /** Counts down while a weak-spot hit is still worth showing. */
    val weakSpotFlashTimer: Float = 0f,
    /** Seconds since spawn; drives the spawn-in animation and idle bobbing. */
    val animationTime: Float = 0f,
) {
    val size: Float get() = archetype.size

    val maxHealth: Int get() = archetype.maxHealth

    val bounds: Aabb get() = Aabb.square(position, size)

    val center: Vec2 get() = Vec2(position.x + size / 2f, position.y + size / 2f)

    val isAlive: Boolean get() = health > 0

    val weakness: EnemyWeakness get() = archetype.weakness

    /** Enemies fade in over their first moments so they never pop in on top of the player. */
    val isMaterialising: Boolean get() = animationTime < SPAWN_TIME

    val spawnProgress: Float get() = (animationTime / SPAWN_TIME).coerceIn(0f, 1f)

    companion object {
        const val SPAWN_TIME = 0.45f
    }
}
