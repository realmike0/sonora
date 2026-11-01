package com.sonora.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sonora.music.ui.SonoraRoot
import com.sonora.music.ui.SonoraTheme

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash di sistema con i colori dinamici; poi la splash dell'app prende il suo posto senza stacchi.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Perf.init(this)
        handleIntent(intent)
        Headsets.startService(this)
        setContent {
            SonoraTheme {
                SonoraRoot(vm)
            }
        }
    }

    // Se l'app viene riaperta da fuori (notifica, controlli multimediali) il player a tendina resta chiuso.
    /** Scorciatoia del widget: apre direttamente la pagina dell'artista scelto. */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() }?.let { vm.artistOpen = it }
    }

    companion object {
        const val EXTRA_ARTIST = "open_artist"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        vm.playerOpen = false
    }

    override fun onStart() {
        super.onStart()
        AppState.visible = true
    }

    override fun onStop() {
        AppState.visible = false
        vm.playerOpen = false
        super.onStop()
    }
}
