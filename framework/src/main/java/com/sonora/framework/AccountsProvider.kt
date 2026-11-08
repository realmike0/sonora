package com.sonora.framework

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Ponte tra Sonora e il Framework. Protetto da un permesso "signature": solo app firmate con la chiave di Sonora lo usano.
 * call("state") → account attivo (email, token, verified) e l'elenco; "adopt"/"setActive"/"remove"/"verified" lo modificano.
 */
class AccountsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle()
        val s = Store(ctx)
        when (method) {
            "adopt" -> if (!arg.isNullOrEmpty()) {
                s.add(arg, extras?.getString("token").orEmpty(), extras?.getBoolean("verified") ?: false)
            }
            "setActive" -> if (arg != null) s.setActive(arg)
            "remove" -> if (arg != null) s.remove(arg)
            "verified" -> if (arg != null) s.setVerified(arg, extras?.getBoolean("verified") ?: false)
        }
        val a = s.active()
        return Bundle().apply {
            putString("email", a?.email)
            putString("token", a?.token)
            putBoolean("verified", a?.verified ?: false)
            putStringArrayList("emails", ArrayList(s.list().map { it.email }))
            putInt("version", 1)
        }
    }

    /** content://com.sonora.framework.accounts/avatar → immagine profilo dell'account attivo. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val a = Store(ctx).active() ?: throw FileNotFoundException()
        val f = Store(ctx).avatarFile(a.email)
        if (!f.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
