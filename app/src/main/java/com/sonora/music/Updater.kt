package com.sonora.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException

/** Aggiornamenti: cerca nuove release su GitHub, scarica l'APK e lo apre per l'installazione. */
object Updater {
    private const val REPO = "realmike0/sonora"

    data class Release(val tag: String, val name: String, val apkUrl: String, val sizeBytes: Long)

    /** Ultima release pubblicata (anche pre-release) che contiene un APK. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=5")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Sonora-Updater")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub ha risposto ${resp.code}")
            val arr = JSONArray(resp.body?.string() ?: "[]")
            var found: Release? = null
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                if (rel.optBoolean("draft")) continue
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) {
                        found = Release(
                            tag = rel.optString("tag_name"),
                            name = rel.optString("name").ifBlank { rel.optString("tag_name") },
                            apkUrl = a.optString("browser_download_url"),
                            sizeBytes = a.optLong("size")
                        )
                        break
                    }
                }
                if (found != null) break
            }
            found
        }
    }

    private fun numbers(v: String): List<Int> = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()

    /** "v0.40-beta" è più nuova di "0.31-beta"? */
    fun isNewer(tag: String, current: String): Boolean {
        val a = numbers(tag)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    suspend fun download(context: Context, rel: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val out = File(dir, "Sonora-${rel.tag}.apk")
            val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "Sonora-Updater").build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download non riuscito (${resp.code})")
                val body = resp.body ?: throw IOException("Risposta vuota")
                val total = body.contentLength().takeIf { it > 0 } ?: rel.sizeBytes
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            out
        }

    /** Apre l'installer di Android. Se manca il permesso "installa app sconosciute" apre prima quella schermata. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
