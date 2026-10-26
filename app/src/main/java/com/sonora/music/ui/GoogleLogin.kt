package com.sonora.music.ui

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sonora.music.GoogleSync

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
        "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

// Google rifiuta gli accessi dalle WebView "dichiarate": usiamo un user agent da Chrome mobile.
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Mobile Safari/537.36"

/** Pagina di login Google dentro l'app. Appena la sessione è valida restituisce i cookie. */
@Composable
fun GoogleLoginScreen(onCookie: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi")
                }
                Text("Accedi a Google", style = MaterialTheme.typography.titleMedium)
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val cookies = CookieManager.getInstance()
                    val web = WebView(context)
                    var delivered = false

                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(web, true)

                    web.settings.javaScriptEnabled = true
                    web.settings.domStorageEnabled = true
                    web.settings.userAgentString = BROWSER_UA
                    web.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (delivered || url == null || !url.startsWith("https://music.youtube.com")) return
                            val c = cookies.getCookie("https://music.youtube.com")
                            if (c != null && GoogleSync.hasSession(c)) {
                                delivered = true
                                onCookie(c)
                            }
                        }
                    }

                    // Parte da una sessione pulita, così si può scegliere l'account.
                    cookies.removeAllCookies { web.post { web.loadUrl(LOGIN_URL) } }
                    web
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
