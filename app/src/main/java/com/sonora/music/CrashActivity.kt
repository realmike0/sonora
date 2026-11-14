package com.sonora.music

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Se Sonora si chiude da sola, questa schermata (in un processo a parte) mostra l'errore, così si può copiare e mandare.
 * Usa solo viste semplici di Android: deve funzionare anche quando tutto il resto è rotto.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "(nessun dettaglio)"
        val info = "Sonora ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        root.addView(TextView(this).apply { text = "Sonora si è chiusa"; textSize = 22f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Copia l'errore e mandalo a Mike su Telegram.\n$info"; setPadding(0, pad / 2, 0, pad / 2) })
        val body = TextView(this).apply { text = trace; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, pad / 2, 0, 0) }
        row.addView(Button(this).apply {
            text = "Copia"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", "$info\n\n$trace"))
                text = "Copiato ✓"
            }
        })
        row.addView(Button(this).apply {
            text = "Riapri Sonora"
            setOnClickListener {
                startActivity(packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        root.addView(row)
        setContentView(root)
    }
}
