package com.sonora.music

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Widgets {
    private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    /** Brano in riproduzione (salvato qui perché i widget vivono fuori dall'app). */
    fun setNow(c: Context, title: String?, artist: String?, playing: Boolean) {
        prefs(c).edit().putString("w_title", title).putString("w_artist", artist).putBoolean("w_playing", playing).apply()
        updateAll(c)
    }

    fun artistFor(c: Context, widgetId: Int): String = prefs(c).getString("w_artist_$widgetId", "") ?: ""

    fun setArtist(c: Context, widgetId: Int, name: String) {
        prefs(c).edit().putString("w_artist_$widgetId", name.trim()).apply()
    }

    private fun openApp(c: Context, code: Int, artist: String? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (artist != null) i.putExtra(MainActivity.EXTRA_ARTIST, artist)
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun events(c: Context): List<PlayEvent> {
        PlayLog.init(c)
        return PlayLog.all().asReversed()
    }

    fun updateAll(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        for (cls in listOf(NowWidget::class.java, RecentWidget::class.java, ArtistWidget::class.java)) {
            val ids = mgr.getAppWidgetIds(ComponentName(c, cls))
            if (ids.isNotEmpty()) update(c, mgr, cls, ids)
        }
    }

    fun update(c: Context, mgr: AppWidgetManager, cls: Class<*>, ids: IntArray) {
        for (id in ids) {
            val v = when (cls) {
                NowWidget::class.java -> nowViews(c)
                RecentWidget::class.java -> recentViews(c)
                else -> artistViews(c, id)
            }
            mgr.updateAppWidget(id, v)
        }
    }

    private fun nowViews(c: Context): RemoteViews {
        val p = prefs(c)
        val title = p.getString("w_title", null)
        val v = RemoteViews(c.packageName, R.layout.widget_now)
        v.setTextViewText(R.id.w_label, if (p.getBoolean("w_playing", false)) "In riproduzione" else "Ultimo brano")
        v.setTextViewText(R.id.w_title, title ?: "Nessun brano")
        v.setTextViewText(R.id.w_artist, p.getString("w_artist", "") ?: "")
        val recent = events(c).distinctBy { it.id }.filter { it.title != title }.take(3)
        val rows = listOf(R.id.w_r1, R.id.w_r2, R.id.w_r3)
        rows.forEachIndexed { i, rid -> v.setTextViewText(rid, recent.getOrNull(i)?.let { "${it.title} · ${it.artist}" } ?: "") }
        v.setOnClickPendingIntent(R.id.w_root, openApp(c, 11))
        return v
    }

    private fun recentViews(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_recent)
        val fmt = SimpleDateFormat("EEE HH:mm", Locale.ITALY)
        val list = events(c).take(5)
        val rows = listOf(R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4, R.id.w_r5)
        rows.forEachIndexed { i, rid ->
            v.setTextViewText(rid, list.getOrNull(i)?.let { "${fmt.format(Date(it.ts))}  ${it.title} – ${it.artist}" }
                ?: if (i == 0) "Ancora nessun ascolto" else "")
        }
        v.setOnClickPendingIntent(R.id.w_root, openApp(c, 12))
        return v
    }

    private fun artistViews(c: Context, id: Int): RemoteViews {
        val name = artistFor(c, id)
        val v = RemoteViews(c.packageName, R.layout.widget_artist)
        v.setTextViewText(R.id.w_title, name.ifBlank { "Scegli un artista" })
        v.setTextViewText(R.id.w_label, "Canale artista")
        v.setOnClickPendingIntent(
            R.id.w_root,
            if (name.isBlank()) PendingIntent.getActivity(
                c, 20 + id, Intent(c, ArtistWidgetConfig::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ) else openApp(c, 20 + id, name)
        )
        return v
    }
}

class NowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.update(context, m, NowWidget::class.java, ids)
}

class RecentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.update(context, m, RecentWidget::class.java, ids)
}

class ArtistWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.update(context, m, ArtistWidget::class.java, ids)
}

/** Schermata di scelta dell'artista, aperta quando si aggiunge (o si riconfigura) il widget. */
class ArtistWidgetConfig : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "Nome dell'artista"
            setSingleLine()
            setText(Widgets.artistFor(this@ArtistWidgetConfig, id))
        }
        val ok = Button(this).apply {
            text = "Salva"
            setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    Widgets.setArtist(this@ArtistWidgetConfig, id, name)
                    Widgets.updateAll(this@ArtistWidgetConfig)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(TextView(this@ArtistWidgetConfig).apply { text = "Quale artista vuoi sul widget?"; textSize = 18f })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ok)
        })
    }
}
