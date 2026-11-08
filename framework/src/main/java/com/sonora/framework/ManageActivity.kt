package com.sonora.framework

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Schermata di gestione account. Non ha icona: si apre da Sonora (Gestisci) o da Impostazioni → Password e account. */
class ManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val add = intent.getBooleanExtra("add", false)
        setContent { FwTheme { FwApp(startWithAdd = add, onClose = { finish() }) } }
    }
}
