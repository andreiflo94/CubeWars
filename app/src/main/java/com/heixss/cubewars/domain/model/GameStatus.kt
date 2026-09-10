package com.heixss.cubewars.domain.model

/** The single dimension that decides whether the simulation ticks. */
enum class GameStatus {
    /** Title card is up; waiting for the player to start. */
    READY,

    /** Simulation is running. */
    RUNNING,

    /** Simulation frozen by the player. */
    PAUSED,

    /** The player cube has been destroyed. Score is final. */
    GAME_OVER;

    val isSimulating: Boolean get() = this == RUNNING
}
