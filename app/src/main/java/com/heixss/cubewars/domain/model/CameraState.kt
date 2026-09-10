package com.heixss.cubewars.domain.model

/**
 * Where the virtual camera is looking. It lives in the state rather than in the renderer so that a
 * replay of the state stream reproduces the exact same framing.
 */
data class CameraState(
    val center: Vec2,
    /** Current shake amplitude in world units; decays every step. */
    val shake: Float = 0f,
    /** Advances with time so the renderer can derive a stable shake offset from the state alone. */
    val shakeSeed: Float = 0f,
)
