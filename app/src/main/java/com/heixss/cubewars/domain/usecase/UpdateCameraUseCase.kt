package com.heixss.cubewars.domain.usecase

import com.heixss.cubewars.domain.config.GameConfig
import com.heixss.cubewars.domain.model.GameState
import com.heixss.cubewars.domain.model.Vec2
import com.heixss.cubewars.domain.util.countdown
import com.heixss.cubewars.domain.util.smoothTowards

/**
 * Frames the action.
 *
 * The camera is state, not renderer bookkeeping — including the shake amplitude, which combat
 * bumps up and this stage bleeds off. That keeps "how hard did that hit feel" a property of the
 * simulation rather than of whichever thread happened to draw the frame.
 */
class UpdateCameraUseCase : SimulationStage {

    override fun invoke(state: GameState, dt: Float): GameState {
        val halfWidth = GameConfig.VIEWPORT_WIDTH / 2f
        val halfHeight = GameConfig.VIEWPORT_HEIGHT / 2f
        val player = state.player

        val lookAhead = player.facing.sign * GameConfig.CAMERA_LOOK_AHEAD
        val targetX = clampToLevel(player.bounds.centerX + lookAhead, halfWidth, state.level.width)
        val targetY = clampToLevel(player.bounds.centerY + 10f, halfHeight, state.level.height)

        val center = Vec2(
            smoothTowards(state.camera.center.x, targetX, GameConfig.CAMERA_SMOOTHING, dt),
            smoothTowards(state.camera.center.y, targetY, GameConfig.CAMERA_SMOOTHING, dt),
        )

        return state.copy(
            camera = state.camera.copy(
                center = center,
                shake = countdown(state.camera.shake, GameConfig.CAMERA_SHAKE_DECAY * dt),
                shakeSeed = state.camera.shakeSeed + dt,
            ),
        )
    }

    /** Keeps the view inside the level, or dead-centres it when the level is smaller than the view. */
    private fun clampToLevel(target: Float, halfExtent: Float, levelExtent: Float): Float =
        if (levelExtent <= halfExtent * 2f) {
            levelExtent / 2f
        } else {
            target.coerceIn(halfExtent, levelExtent - halfExtent)
        }
}
