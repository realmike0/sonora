package com.sonora.music

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/** Il 10 dicembre arriva una notifica verde: il Wrapped dell'anno è pronto. */
object WrappedReminder {
    private const val CHANNEL = "wrapped"
    private const val GREEN = 0xFF1DB954.toInt()

    /** Programma la prossima notifica (si può chiamare quante volte si vuole: sostituisce la precedente). */
    fun schedule(context: Context) {
        val now = Calendar.getInstance()
        val at = Calendar.getInstance().apply {
            set(now.get(Calendar.YEAR), Calendar.DECEMBER, 10, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis <= now.timeInMillis) at.add(Calendar.YEAR, 1)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC, at.timeInMillis, alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 10,
        Intent(context, WrappedReceiver::class.java).setAction("com.sonora.music.WRAPPED_READY"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun notifyNow(context: Context) {
        PlayLog.init(context)
        if (PlayLog.all().isEmpty()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wrapped", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 11,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WRAPPED, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle(com.sonora.music.ui.tr("Il tuo Wrapped è pronto"))
            .setContentText(com.sonora.music.ui.tr("Scopri com'è stato il tuo anno in musica"))
            .setColor(GREEN)
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(4210, n)
        } catch (e: SecurityException) {
            // permesso notifiche non concesso
        }
    }
}

class WrappedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.sonora.music.WRAPPED_READY") WrappedReminder.notifyNow(context)
        // Dopo l'invio, e dopo un riavvio del telefono, si riprogramma l'anno successivo.
        WrappedReminder.schedule(context)
    }
}
