package com.sonora.music

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap

data class NotifItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val icon: ImageBitmap?
)

/** Notifiche attive, lette dal listener e mostrate nella modalità AOD. */
object NotifHub {
    val items = mutableStateListOf<NotifItem>()

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

class SonoraNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = refreshAll()

    override fun onNotificationPosted(sbn: StatusBarNotification) = refreshAll()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refreshAll()

    private fun refreshAll() {
        try {
            val list = activeNotifications.orEmpty()
                .filter { it.packageName != packageName }
                .filter { it.isClearable || it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .sortedByDescending { it.postTime }
                .take(8)
                .mapNotNull { toItem(it) }
            NotifHub.items.clear()
            NotifHub.items.addAll(list)
        } catch (e: Exception) {
            // best-effort
        }
    }

    private fun toItem(sbn: StatusBarNotification): NotifItem? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName.substringAfterLast('.')
        }
        val icon = try {
            sbn.notification.smallIcon?.loadDrawable(this)?.toBitmap(64, 64)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        return NotifItem(sbn.key, app, title, text, icon)
    }

    companion object {
        /** Chiede ad Android di riconnettere il listener dopo che l'utente ha dato il consenso. */
        fun rebind(context: Context) {
            try {
                NotificationListenerService.requestRebind(ComponentName(context, SonoraNotificationListener::class.java))
            } catch (e: Exception) {
                // ignorato
            }
        }
    }
}
