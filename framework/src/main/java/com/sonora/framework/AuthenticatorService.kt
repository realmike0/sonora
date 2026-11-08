package com.sonora.framework

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/** Fa comparire "Sonora" tra gli account di Android (Impostazioni → Password e account) e permette di aggiungerne altri da lì. */
class AuthenticatorService : Service() {
    private lateinit var auth: Authenticator
    override fun onCreate() { auth = Authenticator(this) }
    override fun onBind(intent: Intent?): IBinder? = auth.iBinder
}

private class Authenticator(private val c: Context) : AbstractAccountAuthenticator(c) {
    override fun addAccount(
        response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?,
        requiredFeatures: Array<out String>?, options: Bundle?
    ): Bundle {
        val i = Intent(c, ManageActivity::class.java)
            .putExtra("add", true)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        return Bundle().apply { putParcelable(AccountManager.KEY_INTENT, i) }
    }

    override fun getAuthToken(response: AccountAuthenticatorResponse?, account: Account, authTokenType: String?, options: Bundle?): Bundle {
        val t = Store(c).get(account.name)?.token
        return Bundle().apply {
            if (t.isNullOrEmpty()) putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_AUTHENTICATION)
            else {
                putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
                putString(AccountManager.KEY_ACCOUNT_TYPE, Store.TYPE)
                putString(AccountManager.KEY_AUTHTOKEN, t)
            }
        }
    }

    override fun editProperties(r: AccountAuthenticatorResponse?, t: String?): Bundle = Bundle()
    override fun confirmCredentials(r: AccountAuthenticatorResponse?, a: Account?, o: Bundle?): Bundle? = null
    override fun getAuthTokenLabel(t: String?): String? = null
    override fun updateCredentials(r: AccountAuthenticatorResponse?, a: Account?, t: String?, o: Bundle?): Bundle? = null
    override fun hasFeatures(r: AccountAuthenticatorResponse?, a: Account?, f: Array<out String>?): Bundle =
        Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
