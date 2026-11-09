package com.sonora.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notifiche degli avvisi dell'amministrazione: gialla per il reclamo, grigia per la sospensione, rossa per il ban. */
object ModNotify {
    private const val CH = "sonora_moderation"
    const val YELLOW = 0xFFFFC107.toInt()
    const val GREY = 0xFF757575.toInt()
    const val RED = 0xFFD32F2F.toInt()
    private const val GREEN = 0xFF2E7D32.toInt()
    private const val BLUE = 0xFF1976D2.toInt()

    fun color(kind: String) = when (kind) {
        "warn" -> YELLOW; "suspend" -> GREY; "ban" -> RED; "ticket" -> GREEN; else -> BLUE
    }

    fun title(kind: String) = when (kind) {
        "warn" -> "Reclamo dal team di Sonora"; "suspend" -> "Account sospeso"; "ban" -> "Account bannato"
        "ticket" -> "Risposta al tuo ticket"; else -> "Sonora"
    }

    fun show(context: Context, n: SonoraId.Notice) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CH, "Avvisi di Sonora", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, 21,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_INBOX, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val bg = color(n.kind)
        val fg = if (n.kind == "warn") Color.BLACK else Color.WHITE
        val view = RemoteViews(context.packageName, R.layout.notif_mod).apply {
            setInt(R.id.root, "setBackgroundColor", bg)
            setTextViewText(R.id.title, title(n.kind))
            setTextViewText(R.id.text, n.text)
            setTextColor(R.id.title, fg)
            setTextColor(R.id.text, fg)
        }
        val notif = NotificationCompat.Builder(context, CH)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle(title(n.kind))
            .setContentText(n.text)
            .setColor(bg)
            .setColorized(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(4300 + (n.id.hashCode() and 0xff), notif)
        } catch (e: SecurityException) {
            // permesso notifiche non concesso
        }
    }
}
