package com.sonora.music

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import okhttp3.Request

object Widgets {
    private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    /** Brano in riproduzione (salvato qui perché i widget vivono fuori dall'app). */
    fun setNow(c: Context, title: String?, artist: String?, playing: Boolean, thumb: String? = null) {
        prefs(c).edit().putString("w_title", title).putString("w_artist", artist)
            .putBoolean("w_playing", playing).putString("w_thumb", thumb).apply()
        updateAll(c)
    }

    fun artistFor(c: Context, widgetId: Int): String = prefs(c).getString("w_artist_$widgetId", "") ?: ""
    private fun artistThumb(c: Context, widgetId: Int): String? = prefs(c).getString("w_athumb_$widgetId", null)

    fun setArtist(c: Context, widgetId: Int, name: String, thumb: String?) {
        prefs(c).edit().putString("w_artist_$widgetId", name.trim()).putString("w_athumb_$widgetId", thumb).apply()
    }

    private fun openApp(c: Context, code: Int, artist: String? = null, track: PlayEvent? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (artist != null) i.putExtra(MainActivity.EXTRA_ARTIST, artist)
        if (track != null) {
            i.action = "play_${track.id}"
            i.putExtra(MainActivity.EXTRA_PLAY_ID, track.id).putExtra(MainActivity.EXTRA_PLAY_TITLE, track.title)
                .putExtra(MainActivity.EXTRA_PLAY_ARTIST, track.artist).putExtra(MainActivity.EXTRA_PLAY_THUMB, track.thumb)
        }
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun events(c: Context): List<PlayEvent> {
        PlayLog.init(c)
        return PlayLog.all().asReversed()
    }

    // ---- immagini ----

    private fun download(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    }

    private fun fileFor(c: Context, url: String) = java.io.File(c.cacheDir, "w_" + url.hashCode() + ".png")

    /** Copertina in versione piccola e arrotondata, con cache su disco (i widget non possono caricare da URL). */
    private fun cover(c: Context, url: String?, size: Int, round: Float, circle: Boolean = false): Bitmap? {
        if (url.isNullOrBlank()) return null
        val f = java.io.File(c.cacheDir, "w_${size}_${circle}_" + url.hashCode() + ".png")
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        val src = download(url) ?: return null
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val r = if (circle) size / 2f else round
        cv.drawRoundRect(rect, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        cv.drawBitmap(Bitmap.createScaledBitmap(sq, size, size, true), null, rect, paint)
        runCatching { f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        return out
    }

    /** Sfondo sfocato: riduzione a pochi pixel e riscalatura morbida, scurita per far leggere il testo. */
    private fun blurred(c: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val f = fileFor(c, "blur$url")
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        val src = download(url) ?: return null
        val tiny = Bitmap.createScaledBitmap(src, 14, 14, true)
        val big = Bitmap.createScaledBitmap(tiny, 280, 280, true)
        Canvas(big).drawColor(0x88000000.toInt())
        runCatching { f.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        return big
    }

    // ---- aggiornamento ----

    fun updateAll(c: Context) {
        val app = c.applicationContext
        Thread {
            val mgr = AppWidgetManager.getInstance(app)
            for (cls in listOf(NowWidget::class.java, RecentWidget::class.java, ArtistWidget::class.java)) {
                val ids = mgr.getAppWidgetIds(ComponentName(app, cls))
                if (ids.isNotEmpty()) update(app, mgr, cls, ids)
            }
        }.start()
    }

    /** Da chiamare fuori dal thread principale (scarica le copertine). */
    fun update(c: Context, mgr: AppWidgetManager, cls: Class<*>, ids: IntArray) {
        for (id in ids) {
            val v = runCatching {
                when (cls) {
                    NowWidget::class.java -> nowViews(c)
                    RecentWidget::class.java -> recentViews(c)
                    else -> artistViews(c, id)
                }
            }.getOrNull() ?: continue
            mgr.updateAppWidget(id, v)
        }
    }

    fun updateAsync(c: Context, mgr: AppWidgetManager, cls: Class<*>, ids: IntArray) {
        val app = c.applicationContext
        Thread { update(app, mgr, cls, ids) }.start()
    }

    private fun nowViews(c: Context): RemoteViews {
        val p = prefs(c)
        val title = p.getString("w_title", null)
        val playing = p.getBoolean("w_playing", false)
        val thumb = p.getString("w_thumb", null)
        val v = RemoteViews(c.packageName, R.layout.widget_now)
        v.setTextViewText(R.id.w_label, if (playing) "In riproduzione" else "Ultimo brano")
        v.setTextViewText(R.id.w_title, title ?: "Nessun brano")
        v.setTextViewText(R.id.w_artist, p.getString("w_artist", "") ?: "")
        v.setImageViewResource(R.id.w_play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        blurred(c, thumb)?.let { v.setImageViewBitmap(R.id.w_bg, it) }
        cover(c, thumb, 240, 36f)?.let { v.setImageViewBitmap(R.id.w_cover, it) }
        v.setOnClickPendingIntent(R.id.w_play, PendingIntent.getBroadcast(
            c, 13, Intent(c, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        v.setOnClickPendingIntent(R.id.w_root, openApp(c, 11))
        return v
    }

    private fun recentViews(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_recent)
        v.setTextViewText(R.id.w_label, "Attività recente")
        val list = events(c).distinctBy { it.id }.take(4)
        val ids = listOf(R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4)
        ids.forEachIndexed { i, rid ->
            val e = list.getOrNull(i)
            if (e == null) {
                v.setImageViewBitmap(rid, null)
            } else {
                cover(c, e.thumb, 200, 28f)?.let { v.setImageViewBitmap(rid, it) }
                v.setOnClickPendingIntent(rid, openApp(c, 40 + i, track = e))
            }
        }
        v.setOnClickPendingIntent(R.id.w_root, openApp(c, 12))
        return v
    }

    private fun artistViews(c: Context, id: Int): RemoteViews {
        val name = artistFor(c, id)
        val thumb = artistThumb(c, id)
        val v = RemoteViews(c.packageName, R.layout.widget_artist)
        v.setTextViewText(R.id.w_title, name.ifBlank { "Scegli un artista" })
        v.setTextViewText(R.id.w_label, "Artista")
        blurred(c, thumb)?.let { v.setImageViewBitmap(R.id.w_bg, it) }
        cover(c, thumb, 160, 0f, circle = true)?.let { v.setImageViewBitmap(R.id.w_cover, it) }
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
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateAsync(context, m, NowWidget::class.java, ids)
}

class RecentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateAsync(context, m, RecentWidget::class.java, ids)
}

class ArtistWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateAsync(context, m, ArtistWidget::class.java, ids)
}

/** Play/pausa dal widget: inviato come tasto multimediale alla sessione attiva. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    companion object {
        const val ACTION_TOGGLE = "com.sonora.music.WIDGET_TOGGLE"
    }
}

/** Scelta dell'artista: barra di ricerca e solo artisti che esistono davvero su YouTube Music. */
class ArtistWidgetConfig : Activity() {
    private val results = ArrayList<Pair<String, String?>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "Cerca un artista"
            setSingleLine()
        }
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList())
        val list = ListView(this).apply { this.adapter = adapter }
        val status = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        var seq = 0
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                val mine = ++seq
                if (q.length < 2) return
                Thread {
                    Thread.sleep(400)
                    if (mine != seq) return@Thread
                    val found = runCatching { ArtistRepository.searchArtists(q) }.getOrNull()
                    runOnUiThread {
                        if (mine != seq) return@runOnUiThread
                        results.clear()
                        found?.let { results.addAll(it) }
                        adapter.clear()
                        adapter.addAll(results.map { it.first })
                        status.text = if (found == null) "Ricerca non riuscita" else if (results.isEmpty()) "Nessun artista trovato" else ""
                    }
                }.start()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        list.setOnItemClickListener { _, _, pos, _ ->
            val (name, thumb) = results[pos]
            Widgets.setArtist(this, id, name, thumb)
            Widgets.updateAll(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (320 * resources.displayMetrics.density).toInt()))
        })
    }
}
