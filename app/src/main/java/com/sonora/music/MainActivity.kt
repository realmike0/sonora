package com.sonora.music

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
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
        com.sonora.music.ui.Lang.load(this)
        com.sonora.music.ui.AppStyle.load(this)
        WrappedReminder.schedule(this)
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
        if (intent?.getBooleanExtra(EXTRA_WRAPPED, false) == true) vm.wrappedOpen = true
        intent?.getStringExtra(EXTRA_PLAY_ID)?.takeIf { it.isNotBlank() }?.let { id ->
            vm.play(
                listOf(Track(id, intent.getStringExtra(EXTRA_PLAY_TITLE) ?: "", intent.getStringExtra(EXTRA_PLAY_ARTIST) ?: "",
                    intent.getStringExtra(EXTRA_PLAY_THUMB), 0L)), 0
            )
            intent.removeExtra(EXTRA_PLAY_ID)
        }
    }

    companion object {
        const val EXTRA_ARTIST = "open_artist"
        const val EXTRA_WRAPPED = "open_wrapped"
        const val EXTRA_PLAY_ID = "play_id"
        const val EXTRA_PLAY_TITLE = "play_title"
        const val EXTRA_PLAY_ARTIST = "play_artist"
        const val EXTRA_PLAY_THUMB = "play_thumb"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        vm.playerOpen = false
    }

    private var downX = 0f
    private var downY = 0f
    private var downT = 0L

    /** Vibrazione leggerissima a ogni tocco breve (non allo scorrimento). Si può spegnere in Impostazioni. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downT = ev.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (ev.eventTime - downT < 250 &&
                    kotlin.math.abs(ev.x - downX) < slop && kotlin.math.abs(ev.y - downY) < slop &&
                    getSharedPreferences("sonora", MODE_PRIVATE).getBoolean("haptics", true)
                ) {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
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
