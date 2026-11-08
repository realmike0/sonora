package com.sonora.framework

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AuthExpired : IOException("Sessione scaduta: accedi di nuovo")

/** Chiamate al server Sonora ID. Manda sempre id e nome del dispositivo, così l'elenco "Dispositivi collegati" è corretto. */
object Api {
    const val BASE = "https://sonora-wrapped.sonorawrapped.workers.dev"

    fun deviceId(c: Context): String {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return MessageDigest.getInstance("SHA-256").digest(("sonora-device:$raw").toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    fun deviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    suspend fun call(c: Context, method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val con = URL(BASE + path).openConnection() as HttpURLConnection
            try {
                con.requestMethod = method
                con.connectTimeout = 15000
                con.readTimeout = 20000
                con.setRequestProperty("User-Agent", "Sonora-Framework")
                con.setRequestProperty("x-device-id", deviceId(c))
                con.setRequestProperty("x-device-name", deviceName())
                if (token != null) con.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    con.doOutput = true
                    con.setRequestProperty("Content-Type", "application/json")
                    con.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = con.responseCode
                val stream = if (code in 200..299) con.inputStream else con.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (token != null && code == 401) throw AuthExpired()
                if (code !in 200..299) throw IOException(json.optString("error").ifEmpty { "Errore del server ($code)" })
                json
            } finally {
                con.disconnect()
            }
        }
}
