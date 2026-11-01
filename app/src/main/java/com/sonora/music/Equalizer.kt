package com.sonora.music

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

/** Equalizzatore (bande + bass boost + volume extra) agganciato alla sessione audio del player. */
object Eq {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var sessionId = 0
    private lateinit var prefs: android.content.SharedPreferences

    class Info(val bands: Int, val minMb: Int, val maxMb: Int, val freqsHz: List<Int>, val presets: List<String>)

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("eq_on", false)
    fun levels(c: Context): List<Int> = p(c).getString("eq_levels", "")!!.split(",").mapNotNull { it.toIntOrNull() }
    fun bass(c: Context) = p(c).getInt("eq_bass", 0)
    fun loud(c: Context) = p(c).getInt("eq_loud", 0)

    private var cached: Info? = null

    private fun infoOf(e: Equalizer) = Info(
        e.numberOfBands.toInt(), e.bandLevelRange[0].toInt(), e.bandLevelRange[1].toInt(),
        (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
        (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
    )

    /** Caratteristiche dell'equalizzatore: dalla sessione del player se c'è, altrimenti da un'istanza temporanea. */
    fun info(): Info? = cached ?: try {
        val e = Equalizer(0, 0)
        val r = e.bandLevelRange
        val res = Info(
            e.numberOfBands.toInt(), r[0].toInt(), r[1].toInt(),
            (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
            (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
        )
        e.release()
        res
    } catch (t: Throwable) {
        null
    }

    fun attach(c: Context, session: Int) {
        if (session == 0 || session == -1) return
        prefs = p(c)
        release()
        sessionId = session
        try {
            eq = Equalizer(0, session)
            cached = infoOf(eq!!)
            bass = BassBoost(0, session)
            loud = LoudnessEnhancer(session)
            apply(c)
        } catch (t: Throwable) {
            release()
        }
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { loud?.release() }
        eq = null; bass = null; loud = null
    }

    /** Applica le impostazioni salvate (acceso/spento, bande, bass boost, volume extra). */
    fun apply(c: Context) {
        val on = enabled(c)
        try {
            eq?.let { e ->
                e.enabled = on
                val lv = levels(c)
                for (i in 0 until e.numberOfBands) e.setBandLevel(i.toShort(), (lv.getOrNull(i) ?: 0).toShort())
            }
            bass?.let { b ->
                b.enabled = on && bass(c) > 0
                if (b.strengthSupported) b.setStrength(bass(c).toShort())
            }
            loud?.let { l ->
                l.setTargetGain(loud(c))
                l.enabled = on && loud(c) > 0
            }
        } catch (t: Throwable) {
            // alcuni telefoni non supportano tutti gli effetti
        }
    }

    fun save(c: Context, on: Boolean, levels: List<Int>, bass: Int, loud: Int) {
        p(c).edit().putBoolean("eq_on", on).putString("eq_levels", levels.joinToString(","))
            .putInt("eq_bass", bass).putInt("eq_loud", loud).apply()
        apply(c)
    }

    /** Livelli di un preset del telefono. */
    fun presetLevels(preset: Int): List<Int>? = try {
        val e = eq ?: Equalizer(0, 0)
        e.usePreset(preset.toShort())
        val out = (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
        if (e !== eq) e.release()
        out
    } catch (t: Throwable) {
        null
    }
}
