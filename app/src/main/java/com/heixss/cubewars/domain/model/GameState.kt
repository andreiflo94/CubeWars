package com.heixss.cubewars.domain.model

/**
 * **The** state of the game. There is exactly one of these in flight at any moment, published as a
 * `StateFlow<GameState>` by `GameEngine`.
 *
 * Everything the renderer needs to draw a frame is reachable from here, and nothing outside here is
 * consulted while drawing. The consequences worth knowing:
 *
 * * The simulation is a pure function `(GameState, dt) -> GameState`. No use case reads a clock, a
 *   random generator or a device — the clock is the `dt` argument and the generator is [rng].
 * * The whole game can be paused, snapshotted, serialised or replayed by handling this one object.
 * * The renderer is stateless with respect to gameplay; it is a projection of this value.
 */
data class GameState(
    val status: GameStatus,
    val level: Level,
    val player: PlayerCube,
    val enemies: List<EnemyCube> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val camera: CameraState,
    val wave: WaveState = WaveState(),
    /**
     * How many arenas this run has entered, counting the current one. Levels cycle once the last one
     * is beaten, so this — not [Level.id] — is what the player is told they have reached.
     */
    val levelNumber: Int = 1,
    val input: InputState = InputState(),
    val score: Int = 0,
    val highScore: Int = 0,
    /** Seconds of simulated time since the run began. */
    val elapsedTime: Float = 0f,
    /** Deterministic generator; advanced by whichever use case draws from it. */
    val rng: Rng,
    /** Monotonic id source for spawned entities. */
    val nextEntityId: Long = 1L,
    /** Events produced by the step that built this state. Empty on every state that idled. */
    val frameEvents: List<GameEvent> = emptyList(),
) {
    val isNewHighScore: Boolean get() = score > 0 && score >= highScore
}

/**
 * A single-use cursor over the two pieces of simulation bookkeeping that must advance monotonically:
 * the entity id counter and the random generator.
 *
 * Threading both through a long chain of `copy` calls is noisy and easy to get wrong, so a use case
 * opens a cursor, mutates it locally, and [commit]s once. The enclosing use case stays a pure
 * function — the mutation never escapes its stack frame.
 */
class SimulationCursor internal constructor(
    private var nextId: Long,
    private var rng: Rng,
) {
    /** The next unused entity id. */
    fun id(): Long = nextId++

    /** A value in `[0, 1)`. */
    fun unit(): Float {
        rng = rng.next()
        return rng.unit
    }

    /** A value in `[min, max)`. */
    fun range(min: Float, max: Float): Float = min + unit() * (max - min)

    /** A value in `[-magnitude, magnitude)`. */
    fun signed(magnitude: Float): Float = range(-magnitude, magnitude)

    /** A value in `[0, bound)`. */
    fun int(bound: Int): Int = if (bound <= 0) 0 else (unit() * bound).toInt().coerceAtMost(bound - 1)

    /** True with probability [probability]. */
    fun chance(probability: Float): Boolean = unit() < probability

    /** Folds the advanced counters back into [state]. */
    fun commit(state: GameState): GameState = state.copy(nextEntityId = nextId, rng = rng)
}

/** Opens a cursor positioned at this state's id counter and generator. */
fun GameState.cursor(): SimulationCursor = SimulationCursor(nextEntityId, rng)
