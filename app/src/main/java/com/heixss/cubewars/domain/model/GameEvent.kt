package com.heixss.cubewars.domain.model

/**
 * Something notable that happened during one simulation step.
 *
 * Events are part of the state rather than a side channel: [GameState.frameEvents] holds only the
 * events produced by the step that built that state, so the single state object stays the whole
 * truth. The presentation layer reads them for haptics and one-shot flourishes, and any it misses
 * are simply cosmetic.
 */
sealed interface GameEvent {

    data object PlayerFired : GameEvent

    /** The cube launched again in mid-air. */
    data object PlayerAirJumped : GameEvent

    data class EnemyFired(val archetype: EnemyArchetype) : GameEvent

    data class EnemyDamaged(val at: Vec2) : GameEvent

    /** A shot found the one thing this archetype cannot take. */
    data class EnemyWeakSpotHit(
        val archetype: EnemyArchetype,
        val weakness: EnemyWeakness,
        val at: Vec2,
    ) : GameEvent

    /** A shot was turned aside by plating or a shield instead of landing properly. */
    data class EnemyDeflected(val at: Vec2) : GameEvent

    data class EnemyDestroyed(val archetype: EnemyArchetype, val at: Vec2, val award: Int) : GameEvent

    data class PlayerDamaged(val remainingHealth: Int) : GameEvent

    data object PlayerDestroyed : GameEvent

    data class WaveStarted(val displayNumber: Int) : GameEvent

    data class WaveCleared(val displayNumber: Int) : GameEvent

    /** The arena's last wave went down and the exit swung open. */
    data class LevelCleared(val name: String, val levelNumber: Int) : GameEvent

    /** The player walked through the gate and a new arena opened. */
    data class LevelEntered(val name: String, val levelNumber: Int) : GameEvent
}
