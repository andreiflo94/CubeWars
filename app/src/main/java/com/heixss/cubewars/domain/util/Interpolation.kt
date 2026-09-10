package com.heixss.cubewars.domain.util

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Moves [current] toward [target] by at most [maxDelta], never overshooting. */
fun approach(current: Float, target: Float, maxDelta: Float): Float =
    if (current < target) min(current + maxDelta, target) else max(current - maxDelta, target)

/**
 * Frame-rate independent exponential smoothing.
 *
 * A plain `lerp(current, target, k * dt)` changes character whenever the step size changes; this
 * form converges at the same real-world rate for any [dt].
 */
fun smoothTowards(current: Float, target: Float, sharpness: Float, dt: Float): Float =
    current + (target - current) * (1f - exp(-sharpness * dt))

/** Counts [timer] down toward zero without going negative. */
fun countdown(timer: Float, dt: Float): Float = (timer - dt).coerceAtLeast(0f)
