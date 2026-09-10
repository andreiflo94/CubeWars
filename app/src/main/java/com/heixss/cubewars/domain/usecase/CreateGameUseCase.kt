package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.model.CameraState
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.GameStatus
import com.heixss.cubewars.domain.model.PlayerCube
import com.heixss.cubewars.domain.model.Rng
import com.heixss.cubewars.domain.model.WavePhase
import com.heixss.cubewars.domain.model.WaveState
import com.heixss.cubewars.domain.repository.LevelRepository

/**
 * Builds a fresh world.
 *
 * This is the only constructor of [GameState] outside of tests, so "what does a new run look like"
 * has a single answer. Pass a fixed [seed] to get a byte-identical run every time.
 */
class CreateGameUseCase(
    private val levelRepository: LevelRepository,
) {

    operator fun invoke(
        seed: Long,
        highScore: Int = 0,
        status: GameStatus = GameStatus.READY,
        levelId: Int = 0,
    ): GameState {
        val level = levelRepository.loadLevel(levelId)
        val player = PlayerCube(position = level.playerSpawn)

        return GameState(
            status = status,
            level = level,
            player = player,
            camera = CameraState(center = level.cameraStart),
            wave = WaveState(
                index = 0,
                phase = WavePhase.INTERMISSION,
                // A beat of quiet before the first wave, so the player can find their feet.
                phaseTimer = 1.8f,
            ),
            highScore = highScore,
            rng = Rng(seed),
        )
    }
}
