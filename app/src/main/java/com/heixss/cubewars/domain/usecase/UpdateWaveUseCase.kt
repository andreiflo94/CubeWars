package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.effect.ParticleEffects
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.EnemyCube
import com.heixss.cubewars.domain.model.Facing
import com.heixss.cubewars.domain.model.GameEvent
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.LevelDifficulty
import com.heixss.cubewars.domain.model.ParticleTint
import com.heixss.cubewars.domain.model.SimulationCursor
import com.heixss.cubewars.domain.model.SpawnPoint
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.domain.model.cursor
import com.heixss.cubewars.domain.util.countdown
import kotlin.math.abs

/**
 * Runs the wave loop: intermission, trickle enemies in, wait for the arena to clear, repeat until
 * the arena's quota is met — at which point the level is complete and the exit opens.
 *
 * Difficulty comes from two sources that meet here. The run-long ramp keys off the wave index, which
 * never resets; the per-arena step keys off [Level.difficulty], which is data on the level. Both
 * push the same three dials — how many cubes show up, which archetypes are unlocked, and how fast
 * they arrive — so the curve stays readable in one place.
 */
class UpdateWaveUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState = when (state.wave.phase) {
        WavePhase.INTERMISSION -> tickIntermission(state, dt)
        WavePhase.SPAWNING -> tickSpawning(state, dt)
        WavePhase.CLEARING -> tickClearing(state)
        // Nothing left to direct: the gate is open and the player owns the arena.
        WavePhase.COMPLETE -> state
    }

    private fun tickIntermission(state: GameState, dt: Float): GameState {
        val phaseTimer = countdown(state.wave.phaseTimer, dt)
        if (phaseTimer > 0f) {
            return state.copy(wave = state.wave.copy(phaseTimer = phaseTimer))
        }
        val wave = state.wave.copy(
            phase = WavePhase.SPAWNING,
            phaseTimer = 0f,
            remainingToSpawn = enemyCountFor(state.wave.index, state.level.difficulty),
            spawnTimer = 0.25f,
            killsThisWave = 0,
        )
        return state.copy(
            wave = wave,
            frameEvents = state.frameEvents + GameEvent.WaveStarted(wave.displayNumber),
        )
    }

    private fun tickSpawning(state: GameState, dt: Float): GameState {
        val difficulty = state.level.difficulty
        val spawnInterval = GameConfig.WAVE_SPAWN_INTERVAL * difficulty.spawnIntervalScale
        val spawnTimer = countdown(state.wave.spawnTimer, dt)
        val arenaHasRoom = state.enemies.size < GameConfig.WAVE_MAX_ALIVE + difficulty.extraAlive

        if (spawnTimer > 0f || !arenaHasRoom) {
            return state.copy(wave = state.wave.copy(spawnTimer = spawnTimer))
        }

        val cursor = state.cursor()
        val archetype = rollArchetype(state.wave.index + difficulty.unlockBonus, cursor)
        val spawnPoint = chooseSpawnPoint(state, archetype, cursor)
            ?: return state.copy(wave = state.wave.copy(spawnTimer = spawnInterval))

        val enemy = EnemyCube(
            id = cursor.id(),
            archetype = archetype,
            position = spawnPoint.position,
            facing = if (spawnPoint.position.x > state.player.position.x) Facing.LEFT else Facing.RIGHT,
            fireCooldown = archetype.fireInterval * cursor.range(0.5f, 1.2f),
        )

        val remaining = state.wave.remainingToSpawn - 1
        val wave = state.wave.copy(
            remainingToSpawn = remaining,
            spawnTimer = spawnInterval,
            phase = if (remaining <= 0) WavePhase.CLEARING else WavePhase.SPAWNING,
        )

        return cursor.commit(
            state.copy(
                enemies = state.enemies + enemy,
                wave = wave,
                particles = state.particles + ParticleEffects.burst(
                    cursor = cursor,
                    origin = enemy.center,
                    count = 10,
                    tint = ParticleTint.ENEMY,
                    minSpeed = 30f,
                    maxSpeed = 90f,
                    minLife = 0.2f,
                    maxLife = 0.45f,
                ),
            ),
        )
    }

    private fun tickClearing(state: GameState): GameState {
        if (state.enemies.isNotEmpty()) return state
        val cleared = state.wave.displayNumber
        val clearedInLevel = state.wave.clearedInLevel + 1
        val levelDone = clearedInLevel >= state.level.waveQuota

        val events = buildList {
            add(GameEvent.WaveCleared(cleared))
            if (levelDone) add(GameEvent.LevelCleared(state.level.name, state.levelNumber))
        }

        return state.copy(
            wave = state.wave.copy(
                index = state.wave.index + 1,
                clearedInLevel = clearedInLevel,
                // A finished arena stays finished: COMPLETE never spawns again, so the only way on
                // is through the gate.
                phase = if (levelDone) WavePhase.COMPLETE else WavePhase.INTERMISSION,
                phaseTimer = if (levelDone) 0f else GameConfig.WAVE_INTERMISSION,
            ),
            // Surviving a wave is worth something on its own, and it scales with how deep you are.
            score = state.score + cleared * 50,
            frameEvents = state.frameEvents + events,
        )
    }

    /** Headcount for the next wave: the run-long ramp, plus whatever this arena adds on top. */
    private fun enemyCountFor(waveIndex: Int, difficulty: LevelDifficulty): Int {
        val ramped = GameConfig.WAVE_BASE_ENEMY_COUNT + waveIndex * GameConfig.WAVE_ENEMY_GROWTH
        return (ramped + difficulty.extraEnemies)
            .coerceAtMost(GameConfig.WAVE_MAX_ENEMY_COUNT + difficulty.extraEnemies)
    }

    /** Weighted roll across every archetype unlocked at [waveIndex]. */
    private fun rollArchetype(waveIndex: Int, cursor: SimulationCursor): EnemyArchetype {
        val unlocked = EnemyArchetype.entries.filter { it.unlockWave <= waveIndex }
        val totalWeight = unlocked.sumOf { it.spawnWeight }
        var roll = cursor.int(totalWeight)
        for (archetype in unlocked) {
            roll -= archetype.spawnWeight
            if (roll < 0) return archetype
        }
        return unlocked.last()
    }

    /**
     * Picks somewhere fair to drop an enemy: far enough from the player not to be an ambush, and —
     * for the big archetypes — on the floor rather than perched on a ledge narrower than they are.
     * Falls back to the furthest available point when the arena is cramped.
     */
    private fun chooseSpawnPoint(
        state: GameState,
        archetype: EnemyArchetype,
        cursor: SimulationCursor,
    ): SpawnPoint? {
        val points = state.level.spawnPoints
        if (points.isEmpty()) return null

        val playerX = state.player.bounds.centerX
        val suitable = points
            .filter { archetype.size < GameConfig.TILE || it.onGround }
            .ifEmpty { points }
        val fair = suitable.filter {
            abs(it.position.x - playerX) >= GameConfig.WAVE_MIN_SPAWN_DISTANCE
        }
        val candidates = fair.ifEmpty {
            listOf(suitable.maxBy { abs(it.position.x - playerX) })
        }
        return candidates[cursor.int(candidates.size)]
    }
}
