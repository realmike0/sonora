package com.sonora.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import java.security.MessageDigest

/** Ponte verso "Sonora Framework", l'app invisibile che gestisce gli account Sonora ID. Senza Framework Sonora funziona da sola. */
object Framework {
    const val PKG = "com.sonora.framework"
    private val AUTH: Uri = Uri.parse("content://com.sonora.framework.accounts")
    val avatarUri: Uri = Uri.parse("content://com.sonora.framework.accounts/avatar")

    data class State(val email: String?, val token: String?, val verified: Boolean, val emails: List<String>)

    fun installed(c: Context): Boolean = version(c) != null

    fun version(c: Context): String? = try {
        c.packageManager.getPackageInfo(PKG, 0).versionName
    } catch (e: Exception) {
        null
    }

    /** Il Framework installato è più vecchio di Sonora? */
    fun outdated(c: Context): Boolean {
        val v = version(c) ?: return false
        return Updater.isNewer(Updater.currentVersion(c), v)
    }

    private fun call(c: Context, method: String, arg: String? = null, extras: Bundle? = null): State? = try {
        c.contentResolver.call(AUTH, method, arg, extras)?.let {
            State(
                it.getString("email"), it.getString("token"), it.getBoolean("verified"),
                it.getStringArrayList("emails") ?: emptyList()
            )
        }
    } catch (e: Exception) {
        null
    }

    fun state(c: Context): State? = call(c, "state")

    fun adopt(c: Context, email: String, token: String, verified: Boolean) {
        if (!installed(c)) return
        call(c, "adopt", email, Bundle().apply { putString("token", token); putBoolean("verified", verified) })
    }

    fun remove(c: Context, email: String) {
        if (installed(c)) call(c, "remove", email)
    }

    fun setVerified(c: Context, email: String, v: Boolean) {
        if (installed(c)) call(c, "verified", email, Bundle().apply { putBoolean("verified", v) })
    }

    /** Apre la gestione account (cambio password, dispositivi, email, altri account). */
    fun manage(c: Context, add: Boolean = false) {
        runCatching {
            c.startActivity(
                Intent("com.sonora.framework.MANAGE").setPackage(PKG).putExtra("add", add).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Stesso id che usa il Framework: l'elenco "Dispositivi collegati" mostra un solo dispositivo per telefono. */
    fun deviceId(c: Context): String {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return MessageDigest.getInstance("SHA-256").digest(("sonora-device:$raw").toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    fun deviceName(): String {
        val maker = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    /** Scarica l'ultimo Framework dalle release e apre l'installer (serve un tocco di conferma di Android). */
    suspend fun installLatest(c: Context, onProgress: (Float) -> Unit = {}): Boolean {
        val rel = Updater.latest("SonoraFramework") ?: throw java.io.IOException("Framework non trovato nelle release")
        val apk = Updater.download(c, rel, onProgress)
        return Updater.install(c, apk)
    }
}
