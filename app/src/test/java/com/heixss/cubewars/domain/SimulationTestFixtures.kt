package com.heixss.cubewars.domain

import com.heixss.cubewars.data.level.ArenaLevelRepository
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.Aabb
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.LevelDifficulty
import com.heixss.cubewars.domain.model.PlatformBlock
import com.heixss.cubewars.domain.model.Projectile
import com.heixss.cubewars.domain.model.ProjectileOwner
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.usecase.AdvanceGameUseCase
import com.heixss.cubewars.domain.usecase.ApplyIntentUseCase
import com.heixss.cubewars.domain.usecase.CreateGameUseCase

/**
 * Shared setup for the domain tests.
 *
 * There is no mocking framework here and no Robolectric — the entire simulation is plain Kotlin, so
 * a test just calls it. That is the practical payoff of keeping the domain free of the engine.
 */
object SimulationTestFixtures {

    const val STEP = GameConfig.FIXED_TIME_STEP

    val levels = ArenaLevelRepository()
    val createGame = CreateGameUseCase(levels)
    val applyIntent = ApplyIntentUseCase(createGame)
    val advanceGame = AdvanceGameUseCase.default(levels)

    /** A running game, ready to be stepped. */
    fun running(seed: Long = 1L): GameState =
        createGame(seed = seed, status = GameStatus.RUNNING)

    /**
     * A featureless plain, for measuring what the player can do.
     *
     * Anything that asks "how high is a jump" or "how far does a shot carry" has to be asked
     * somewhere with nothing in the way. Asking it inside a real arena silently measures the arena
     * instead: a block of cover the level generator happened to put down becomes the answer, and the
     * test starts failing every time the rotation is regenerated.
     */
    fun flatArena(): Level = Level(
        id = 0,
        name = "THE PLAIN",
        width = 4000f,
        height = GameConfig.VIEWPORT_HEIGHT,
        blocks = listOf(PlatformBlock(Aabb(0f, 0f, 4000f, FLOOR_Y), BlockKind.SOLID)),
        playerSpawn = Vec2(80f, FLOOR_Y),
        // No spawn points, so the wave director cannot drop a cube into the measurement.
        spawnPoints = emptyList(),
        waveQuota = 1,
        difficulty = LevelDifficulty(),
        exit = Aabb(3900f, FLOOR_Y, 32f, 48f),
    )

    /** Surface of the arena floor: the ground is two tiles deep everywhere. */
    const val FLOOR_Y = GameConfig.TILE * 2f

    /** A running game on the plain, with the player standing on it. */
    fun onFlatGround(seed: Long = 1L, x: Float = 100f): GameState {
        val base = running(seed)
        return base.copy(
            level = flatArena(),
            player = base.player.copy(position = Vec2(x, FLOOR_Y), velocity = Vec2.ZERO, onGround = true),
        )
    }

    /** Steps [state] forward [steps] fixed ticks. */
    fun GameState.step(steps: Int = 1): GameState {
        var current = this
        repeat(steps) { current = advanceGame(current, STEP) }
        return current
    }

    /** An enemy that has finished its spawn-in animation and is therefore a valid target. */
    fun enemyAt(
        x: Float,
        y: Float,
        archetype: EnemyArchetype = EnemyArchetype.GRUNT,
        health: Int = archetype.maxHealth,
        id: Long = 900L,
    ): EnemyCube = EnemyCube(
        id = id,
        archetype = archetype,
        position = Vec2(x, y),
        health = health,
        animationTime = EnemyCube.SPAWN_TIME + 1f,
    )

    fun playerBulletAt(x: Float, y: Float, damage: Int = 1, id: Long = 800L): Projectile = Projectile(
        id = id,
        owner = ProjectileOwner.PLAYER,
        position = Vec2(x, y),
        velocity = Vec2(GameConfig.PLAYER_BULLET_SPEED, 0f),
        size = GameConfig.PLAYER_BULLET_SIZE,
        damage = damage,
        remainingLife = GameConfig.BULLET_LIFETIME,
    )

    fun enemyBulletAt(x: Float, y: Float, damage: Int = 1, id: Long = 801L): Projectile = Projectile(
        id = id,
        owner = ProjectileOwner.ENEMY,
        position = Vec2(x, y),
        velocity = Vec2(-160f, 0f),
        size = 5f,
        damage = damage,
        remainingLife = GameConfig.BULLET_LIFETIME,
    )
}
