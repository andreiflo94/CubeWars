package com.heixss.cubewars.domain.engine

import com.heixss.cubewars.domain.config.GameConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Emits the deltas that drive the simulation. */
fun interface GameClock {

    /** A cold flow of step durations in seconds. Collecting it starts a new timeline. */
    fun ticks(): Flow<Float>
}

/**
 * A fixed-timestep clock.
 *
 * Every emission is exactly [step] seconds, no matter how the host device is behaving. Physics that
 * always sees the same delta cannot produce different jump heights on a 60Hz and a 120Hz screen,
 * and it makes the simulation reproducible.
 *
 * Real time is absorbed by an accumulator: if a frame is slow the clock emits several steps back to
 * back to catch up, but never more than [maxCatchUpSteps] of them — past that it declares
 * bankruptcy and drops the debt rather than entering a death spiral where catching up costs more
 * time than it recovers.
 */
class FixedStepGameClock(
    private val step: Float = GameConfig.FIXED_TIME_STEP,
    private val maxCatchUpSteps: Int = 5,
    private val nanoTime: () -> Long = System::nanoTime,
) : GameClock {

    override fun ticks(): Flow<Float> = flow {
        var previous = nanoTime()
        var accumulator = 0.0
        val stepSeconds = step.toDouble()

        while (currentCoroutineContext().isActive) {
            val now = nanoTime()
            accumulator += (now - previous) / NANOS_PER_SECOND
            previous = now

            var emitted = 0
            while (accumulator >= stepSeconds && emitted < maxCatchUpSteps) {
                emit(step)
                accumulator -= stepSeconds
                emitted++
            }
            if (accumulator > stepSeconds * maxCatchUpSteps) accumulator = 0.0

            val untilNextStep = ((stepSeconds - accumulator) * 1000.0).toLong()
            delay(untilNextStep.coerceIn(1L, 16L))
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
