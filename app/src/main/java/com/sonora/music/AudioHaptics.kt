package com.sonora.music

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.hypot

/**
 * Haptics audio: il telefono vibra a tempo con i bassi del brano (come "Haptics musicali" di Apple Music).
 * Analizza l'audio in riproduzione con Visualizer (serve il permesso del microfono, richiesto quando si accende l'opzione).
 */
object AudioHaptics {
    private var viz: Visualizer? = null
    private var session = 0
    private var level = 0f
    private var peak = 1f
    private var lastPulse = 0L
    @Volatile private var sens = 0.5f

    fun sensitivity(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE).getFloat("audio_haptics_sens", 0.5f)

    /** 0 = pochi colpi leggeri, 1 = molti colpi forti. */
    fun setSensitivity(c: Context, v: Float) {
        sens = v.coerceIn(0f, 1f)
        c.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putFloat("audio_haptics_sens", sens).apply()
    }

    fun enabled(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE).getBoolean("audio_haptics", false)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putBoolean("audio_haptics", on).apply()
        if (on) start(c.applicationContext) else stop()
    }

    /** Chiamato quando il player cambia sessione audio. */
    fun attach(c: Context, sessionId: Int) {
        session = sessionId
        stop()
        if (enabled(c) && sessionId != 0) start(c.applicationContext)
    }

    fun release() = stop()

    private fun vibrator(c: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun start(c: Context) {
        if (viz != null || session == 0) return
        if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val vib = vibrator(c) ?: return
        sens = sensitivity(c)
        try {
            val v = Visualizer(session)
            val size = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.captureSize = size
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, w: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft == null) return
                    // Bassi: i primi contenitori dello spettro (circa 40-170 Hz con 1024 campioni).
                    var energy = 0f
                    for (k in 1..3) {
                        val re = fft[2 * k].toInt()
                        val im = fft[2 * k + 1].toInt()
                        energy += hypot(re.toFloat(), im.toFloat())
                    }
                    peak = maxOf(energy, peak * 0.995f, 20f)
                    val now = energy / peak
                    val beat = now > 0.85f - 0.6f * sens && now > level * 1.15f
                    level = level * 0.6f + now * 0.4f
                    val t = System.currentTimeMillis()
                    if (beat && t - lastPulse > 110) {
                        lastPulse = t
                        val amp = ((60 + 195 * now) * (0.5f + sens)).toInt().coerceIn(1, 255)
                        try {
                            vib.vibrate(
                                if (vib.hasAmplitudeControl()) VibrationEffect.createOneShot(28, amp)
                                else VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        } catch (e: Exception) {
                        }
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            viz = v
        } catch (e: Exception) {
            viz = null
        }
    }

    private fun stop() {
        try {
            viz?.enabled = false
            viz?.release()
        } catch (e: Exception) {
        }
        viz = null
    }
}
