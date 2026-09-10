package com.heixss.cubewars.presentation.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.heixss.cubewars.domain.model.GameEvent

/**
 * Turns [GameEvent]s into taps you can feel.
 *
 * Firing is deliberately silent here — at six shots a second a buzz per bullet is unpleasant — so
 * only the moments that matter get a pulse.
 */
class HapticFeedback(context: Context) {

    private val vibrator: Vibrator? = resolveVibrator(context)

    fun play(events: List<GameEvent>) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        // One pulse per frame at the strength of the loudest thing that happened.
        val strongest = events.maxOfOrNull(::pulseFor) ?: return
        if (strongest.durationMillis <= 0L) return

        vibrator.vibrate(
            VibrationEffect.createOneShot(strongest.durationMillis, strongest.amplitude),
        )
    }

    private data class Pulse(val durationMillis: Long, val amplitude: Int) : Comparable<Pulse> {
        override fun compareTo(other: Pulse): Int = durationMillis.compareTo(other.durationMillis)
    }

    private fun pulseFor(event: GameEvent): Pulse = when (event) {
        is GameEvent.PlayerDestroyed -> Pulse(140L, 255)
        // Clearing an arena is the best thing that happens in a run; it gets the second-longest tap.
        is GameEvent.LevelCleared -> Pulse(90L, 210)
        is GameEvent.LevelEntered -> Pulse(55L, 160)
        is GameEvent.PlayerDamaged -> Pulse(45L, 200)
        is GameEvent.WaveStarted -> Pulse(28L, 90)
        is GameEvent.WaveCleared -> Pulse(22L, 110)
        // Finding a weak spot is the one thing in a firefight worth feeling, and it is rare enough
        // that a tap for it never turns into a buzz.
        is GameEvent.EnemyWeakSpotHit -> Pulse(26L, 190)
        is GameEvent.EnemyDestroyed -> Pulse(18L, 120)
        // A shot turned aside by a shield has to be felt, or the player just reads it as a miss.
        is GameEvent.EnemyDeflected -> Pulse(14L, 150)
        GameEvent.PlayerAirJumped -> Pulse(12L, 70)
        is GameEvent.EnemyDamaged,
        is GameEvent.EnemyFired,
        GameEvent.PlayerFired,
        -> Pulse(0L, 0)
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
