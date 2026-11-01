package com.sonora.music

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Riconoscimento brani (tipo Shazam): registra qualche secondo dal microfono e lo manda al nostro server. */
object Recognizer {
    private const val RATE = 16000

    data class Result(val title: String, val artist: String)

    /** Registra [seconds] secondi di audio e li restituisce come file WAV (16 kHz, mono). */
    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Int = 10): ByteArray = withContext(Dispatchers.IO) {
        val total = RATE * seconds * 2
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IOException("Microfono non disponibile")
        }
        val pcm = ByteArray(total)
        var read = 0
        try {
            rec.startRecording()
            while (read < total) {
                val n = rec.read(pcm, read, minOf(4096, total - read))
                if (n <= 0) break
                read += n
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        wav(pcm, read)
    }

    private fun wav(pcm: ByteArray, len: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + len).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + len).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(len).put(pcm, 0, len)
        return b.array()
    }

    /** Manda l'audio al server; null = brano non riconosciuto. */
    suspend fun identify(context: Context, wav: ByteArray): Result? = withContext(Dispatchers.IO) {
        val base = Tracking.serverUrl(context)
        if (base.isEmpty()) throw IOException("Server non configurato (Impostazioni → Wrapped e tracciamento)")
        val req = Request.Builder().url("$base/v1/recognize")
            .post(wav.toRequestBody("audio/wav".toMediaType())).header("User-Agent", "Sonora").build()
        Http.client.newCall(req).execute().use { r ->
            val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            if (!r.isSuccessful) throw IOException(j.optString("error").ifEmpty { "Errore del server (${r.code})" })
            if (j.optBoolean("found")) Result(j.optString("title"), j.optString("artist")) else null
        }
    }
}
