package com.sonora.framework

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle

data class Acc(val email: String, val token: String, val verified: Boolean)

/** Account Sonora ID salvati sul telefono (AccountManager: compaiono anche in Impostazioni → Password e account). */
class Store(private val c: Context) {
    companion object {
        const val TYPE = "com.sonora.account"
    }

    private val am = AccountManager.get(c)
    private val prefs = c.getSharedPreferences("fw", Context.MODE_PRIVATE)

    private fun accounts(): List<Account> = am.getAccountsByType(TYPE).toList()

    fun list(): List<Acc> = accounts().map {
        Acc(it.name, am.getUserData(it, "token").orEmpty(), am.getUserData(it, "verified") == "1")
    }

    fun get(email: String): Acc? = list().firstOrNull { it.email == email }

    fun active(): Acc? {
        val all = list()
        val a = prefs.getString("active", null)
        return all.firstOrNull { it.email == a } ?: all.firstOrNull()
    }

    fun setActive(email: String) {
        if (get(email) != null) prefs.edit().putString("active", email).apply()
    }

    fun add(email: String, token: String, verified: Boolean, makeActive: Boolean = true) {
        val acc = Account(email, TYPE)
        val data = Bundle().apply { putString("token", token); putString("verified", if (verified) "1" else "0") }
        if (!am.addAccountExplicitly(acc, null, data)) {
            am.setUserData(acc, "token", token)
            am.setUserData(acc, "verified", if (verified) "1" else "0")
        }
        if (makeActive) setActive(email)
    }

    fun setVerified(email: String, v: Boolean) {
        accounts().firstOrNull { it.name == email }?.let { am.setUserData(it, "verified", if (v) "1" else "0") }
    }

    fun remove(email: String) {
        accounts().firstOrNull { it.name == email }?.let {
            am.removeAccountExplicitly(it)
        }
        avatarFile(email).delete()
        if (prefs.getString("active", null) == email) prefs.edit().remove("active").apply()
    }

    /** Cambio email: l'account resta lo stesso, cambia solo il nome (e quindi anche l'immagine profilo si sposta). */
    fun rename(old: String, new: String) {
        val a = get(old) ?: return
        val wasActive = active()?.email == old
        val av = avatarFile(old)
        val bytes = if (av.exists()) av.readBytes() else null
        remove(old)
        add(new, a.token, a.verified, makeActive = wasActive)
        if (bytes != null) avatarFile(new).writeBytes(bytes)
    }

    fun avatarFile(email: String) = java.io.File(c.filesDir, "avatar_" + email.hashCode().toUInt().toString(16) + ".png")
}
