package com.heixss.cubewars.domain.model

/** Phase of the endless wave loop. */
enum class WavePhase {
    /** Counting down to the next wave; nothing is spawning. */
    INTERMISSION,

    /** Enemies are still trickling in. */
    SPAWNING,

    /** Everyone is out; kill the stragglers to advance. */
    CLEARING,

    /** The arena's quota is met. Nothing more will spawn; the exit is open. */
    COMPLETE,
}

/** Progress through the wave loop. */
data class WaveState(
    val index: Int = 0,
    val phase: WavePhase = WavePhase.INTERMISSION,
    val remainingToSpawn: Int = 0,
    val spawnTimer: Float = 0f,
    val phaseTimer: Float = 0f,
    val killsThisWave: Int = 0,
    val totalKills: Int = 0,
    /** Waves cleared in the current arena; reset every time a new one opens. */
    val clearedInLevel: Int = 0,
) {
    /** Waves are 1-indexed for the player, 0-indexed for the rules. */
    val displayNumber: Int get() = index + 1

    /** Which wave of this arena is being fought, 1-indexed. */
    val numberInLevel: Int get() = clearedInLevel + 1

    val isLevelComplete: Boolean get() = phase == WavePhase.COMPLETE
}
