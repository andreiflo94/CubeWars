package com.heixss.cubewars.domain

import com.heixss.cubewars.domain.SimulationTestFixtures.STEP
import com.heixss.cubewars.domain.SimulationTestFixtures.levels
import com.heixss.cubewars.domain.SimulationTestFixtures.running
import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.BlockKind
import com.heixss.cubewars.domain.model.EnemyArchetype
import com.heixss.cubewars.domain.model.InputState
import com.heixss.cubewars.domain.model.Level
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.domain.usecase.UpdateWaveUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated rotation, checked as a set rather than one arena at a time.
 *
 * `ArenaGeometryTest` asks whether each arena is playable. This asks whether ten of them are worth
 * playing in a row: that they are ten different *places*, that the run has a shape, and that the
 * whole roster of enemies actually turns up somewhere in it.
 */
class LevelRotationTest {

    private val rotation: List<Level> = List(levels.levelCount) { levels.loadLevel(it) }

    @Test
    fun `there are ten arenas, and no two are the same place`() {
        assertEquals(10, levels.levelCount)
        assertEquals("two arenas share a name", rotation.size, rotation.map { it.name }.distinct().size)
        assertEquals("two arenas share a look", rotation.size, rotation.map { it.theme }.distinct().size)
    }

    @Test
    fun `no two arenas share a layout`() {
        val shapes = rotation.map { level ->
            level.blocks.map { Triple(it.kind, it.bounds.left, it.bounds.bottom) }.toSet()
        }
        assertEquals("two arenas are the same map with a different name", shapes.size, shapes.distinct().size)
    }

    @Test
    fun `the rotation is a curve, not a flat line`() {
        val first = rotation.first()
        val last = rotation.last()

        assertTrue("the last arena should be a good deal longer", last.width > first.width * 1.4f)
        assertTrue("and should demand more waves", last.waveQuota > first.waveQuota)
        assertTrue(
            "and should send more cubes, faster",
            last.difficulty.extraEnemies > first.difficulty.extraEnemies &&
                last.difficulty.spawnIntervalScale < first.difficulty.spawnIntervalScale,
        )
    }

    @Test
    fun `every arena is built from all three kinds of surface`() {
        for (level in rotation) {
            val floor = level.blocks.count { it.kind == BlockKind.SOLID && it.bounds.bottom <= 0.01f }
            val cover = level.blocks.count {
                it.kind == BlockKind.SOLID && it.bounds.bottom >= GameConfig.TILE * 2f - 0.01f
            }
            val ledges = level.blocks.count { it.kind == BlockKind.ONE_WAY }

            assertTrue("${level.name} has no floor", floor > 0)
            assertTrue("${level.name} has nothing to climb", cover > 0)
            assertTrue("${level.name} has too few ledges to fight on: $ledges", ledges >= 6)
            assertTrue("${level.name} has nowhere to spawn a cube", level.spawnPoints.size >= 8)
        }
    }

    @Test
    fun `a deep wave draws on every archetype`() {
        // The director is driven on its own, with the arena emptied after every spawn: what is under
        // test is the composition roll, and a full simulation would simply fill the arena to its cap
        // and stop asking for cubes long before all eight had come up.
        val director = UpdateWaveUseCase()
        var state = running(seed = 4_242L).let {
            it.copy(
                // Deep enough into the ramp that everything has unlocked.
                wave = it.wave.copy(index = 12, phase = WavePhase.SPAWNING, remainingToSpawn = 999),
                input = InputState(),
            )
        }

        val seen = mutableSetOf<EnemyArchetype>()
        repeat(400) {
            state = director(state, STEP)
            state.enemies.forEach { seen += it.archetype }
            state = state.copy(
                enemies = emptyList(),
                wave = state.wave.copy(
                    phase = WavePhase.SPAWNING,
                    remainingToSpawn = 999,
                    spawnTimer = 0f,
                ),
            )
        }

        val missing = EnemyArchetype.entries - seen
        assertTrue("four hundred spawns never produced: $missing", missing.isEmpty())
    }

    @Test
    fun `only what the wave index has unlocked shows up`() {
        val director = UpdateWaveUseCase()
        var state = running(seed = 77L).let {
            it.copy(wave = it.wave.copy(index = 0, phase = WavePhase.SPAWNING, remainingToSpawn = 999))
        }

        val seen = mutableSetOf<EnemyArchetype>()
        repeat(120) {
            state = director(state, STEP)
            state.enemies.forEach { seen += it.archetype }
            state = state.copy(
                enemies = emptyList(),
                wave = state.wave.copy(
                    phase = WavePhase.SPAWNING,
                    remainingToSpawn = 999,
                    spawnTimer = 0f,
                ),
            )
        }

        val premature = seen.filter { it.unlockWave > state.level.difficulty.unlockBonus }
        assertTrue("the first wave of the first arena sent $premature", premature.isEmpty())
    }
}
