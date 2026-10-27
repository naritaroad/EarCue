package app.earcue.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Each action has its own rhythm so the presenter can tell what happened without looking:
 * next = one tap, previous = two taps, restart = three quick taps, stop = one long, warning = three strong pulses.
 * Alarm usage keeps them working when the phone is on silent or touch feedback is off.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun next() = play(longArrayOf(0, 30), intArrayOf(0, 200))
    fun prev() = play(longArrayOf(0, 24, 80, 24), intArrayOf(0, 190, 0, 190))
    fun stop() = play(longArrayOf(0, 110), intArrayOf(0, 150))
    fun tick() = play(longArrayOf(0, 12), intArrayOf(0, 120))
    fun restart() = play(longArrayOf(0, 24, 60, 24, 60, 24), intArrayOf(0, 200, 0, 200, 0, 200))
    fun edge() = play(longArrayOf(0, 60, 70, 60), intArrayOf(0, 90, 0, 90))
    fun warn() = play(longArrayOf(0, 180, 100, 180, 100, 360), intArrayOf(0, 255, 0, 255, 0, 255))
    fun overtime() = play(longArrayOf(0, 400, 200, 400), intArrayOf(0, 140, 0, 140))

    private fun play(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
