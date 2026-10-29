package com.sonora.music

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Tipo di dispositivo audio collegato: decide il modello 3D e i testi. */
enum class DeviceKind(val key: String, val title: String) {
    EARBUDS("earbuds", "Auricolari collegati"),
    HEADPHONES("headphones", "Cuffie collegate"),
    SPEAKER("speaker", "Speaker collegato"),
    CAR("car", "Auto collegata")
}

data class HeadsetInfo(val name: String, val kind: DeviceKind)

/** Se l'app è in primo piano la notifica non serve: la schermata di benvenuto è dentro l'app. */
object AppState {
    @Volatile var visible = false
}

object Headsets {

    const val ACTION_START_FAVORITES = "com.sonora.music.START_FAVORITES"
    const val CH_KEEPALIVE = "keepalive"
    const val CH_HEADSET = "headset"
    const val ID_KEEPALIVE = 4101
    const val ID_HEADSET = 4102

    private val CAR_WORDS = listOf(
        "car", "auto", "carplay", "bmw", "audi", "mercedes", "volkswagen", "vw ", "fiat", "ford", "toyota",
        "honda", "hyundai", "kia", "tesla", "jeep", "alfa", "skoda", "seat", "opel", "peugeot", "renault",
        "citroen", "uconnect", "sync", "bluelink", "mazda", "volvo", "nissan", "lexus"
    )
    private val SPEAKER_WORDS = listOf(
        "speaker", "boom", "jbl", "flip", "charge", "xtreme", "soundlink", "sonos", "echo", "homepod",
        "soundcore", "megaboom", "wonderboom", "marshall", "emberton", "tribit", "soundbar", "party",
        "stanmore", "woburn", "acton", "nuki", "ue ", "bose"
    )
    private val EARBUD_WORDS = listOf(
        "buds", "airpods", "pods", "earbuds", "earphone", "tws", "freebuds", "liberty", "wf-", "nothing ear",
        "elite", "bud", "ear (", "aeropex", "air "
    )
    private val HEADPHONE_WORDS = listOf(
        "headphone", "headset", "wh-", "xm", "qc", "studio", "over-ear", "momentum", "solo", "cuffie", "px7", "px8"
    )

    fun isHeadsetDevice(d: AudioDeviceInfo): Boolean {
        if (!d.isSink) return false
        return when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    /** Riconosce il tipo di dispositivo: tipo audio, nome e (se c'è il permesso) classe Bluetooth. */
    fun classify(context: Context, d: AudioDeviceInfo): HeadsetInfo {
        val name = d.productName?.toString().orEmpty().ifBlank { "Dispositivo audio" }
        val kind = when (d.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> DeviceKind.HEADPHONES
            else -> byName(name) ?: byBluetoothClass(context, d.address, name) ?: DeviceKind.HEADPHONES
        }
        return HeadsetInfo(name, kind)
    }

    private fun byName(name: String): DeviceKind? {
        val n = " ${name.lowercase()} "
        return when {
            CAR_WORDS.any { n.contains(it) } -> DeviceKind.CAR
            SPEAKER_WORDS.any { n.contains(it) } -> DeviceKind.SPEAKER
            EARBUD_WORDS.any { n.contains(it) } -> DeviceKind.EARBUDS
            HEADPHONE_WORDS.any { n.contains(it) } -> DeviceKind.HEADPHONES
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    private fun byBluetoothClass(context: Context, address: String?, name: String): DeviceKind? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return null
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            val dev = adapter.bondedDevices.firstOrNull {
                (!address.isNullOrBlank() && it.address.equals(address, ignoreCase = true)) || it.name == name
            } ?: return null
            return when (dev.bluetoothClass?.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> DeviceKind.CAR
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> DeviceKind.SPEAKER
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> DeviceKind.HEADPHONES
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> DeviceKind.EARBUDS
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun scan(context: Context, am: AudioManager): HeadsetInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { isHeadsetDevice(it) }
            ?.let { classify(context, it) }

    /** Fotogramma del modello 3D (usato nella notifica, dove non si può animare). */
    fun frameRes(context: Context, kind: DeviceKind, variant: Int, frame: Int): Int =
        context.resources.getIdentifier("m_${kind.key}${variant}_${"%02d".format(frame)}", "drawable", context.packageName)

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEPALIVE, "Rilevamento cuffie", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Tiene Sonora pronta a riconoscere cuffie, auricolari e speaker"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_HEADSET, "Dispositivo collegato", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avviso con il pulsante per iniziare ad ascoltare"
            }
        )
    }

    fun keepAliveEnabled(context: Context): Boolean =
        context.getSharedPreferences("sonora", Context.MODE_PRIVATE).getBoolean("keep_alive", true)

    fun setKeepAlive(context: Context, on: Boolean) {
        context.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putBoolean("keep_alive", on).apply()
        if (on) startService(context) else context.stopService(Intent(context, HeadsetService::class.java))
    }

    fun startService(context: Context) {
        if (!keepAliveEnabled(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, HeadsetService::class.java))
        } catch (e: Exception) {
            // Android può rifiutare l'avvio dallo sfondo: riproveremo alla prossima apertura.
        }
    }

    private const val BLUE = 0xFF1F5BFF.toInt()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Notifica discreta che tiene viva l'app. */
    fun keepAliveNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CH_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle("Sonora è pronta")
            .setContentText("Riconosco cuffie, auricolari e speaker quando si collegano")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /**
     * Notifica "dispositivo collegato" in stile Material (come quella del timer dell'orologio) ma blu:
     * titolo, nome del dispositivo e il pulsante di testo "Inizia ad ascoltare".
     * È la notifica del servizio in primo piano, perciò Android la colora davvero.
     */
    fun connectedNotification(context: Context, info: HeadsetInfo): Notification {
        ensureChannels(context)
        val large = try {
            BitmapFactory.decodeResource(context.resources, frameRes(context, info.kind, (1..2).random(), 6))
        } catch (e: Exception) {
            null
        }
        val start = PendingIntent.getService(
            context, 1,
            Intent(context, HeadsetService::class.java).setAction(ACTION_START_FAVORITES),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CH_HEADSET)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle(info.kind.title)
            .setContentText(info.name)
            .setLargeIcon(large)
            .setColor(BLUE)
            .setColorized(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Inizia ad ascoltare", start)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** Servizio sempre attivo (notifica discreta): riconosce quando si collega un dispositivo audio. */
class HeadsetService : Service() {

    private var callback: AudioDeviceCallback? = null
    private var lastKey: String? = null
    private var lastTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val restore = Runnable { showKeepAlive() }

    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun showKeepAlive() {
        try {
            ServiceCompat.startForeground(this, Headsets.ID_KEEPALIVE, Headsets.keepAliveNotification(this), fgsType())
            NotificationManagerCompat.from(this).cancel(Headsets.ID_HEADSET)
        } catch (e: Exception) {
            // ignorato
        }
    }

    private fun showConnected(info: HeadsetInfo) {
        handler.removeCallbacks(restore)
        try {
            ServiceCompat.startForeground(this, Headsets.ID_HEADSET, Headsets.connectedNotification(this, info), fgsType())
            // Dopo due minuti senza risposta torna la notifica discreta.
            handler.postDelayed(restore, 2 * 60 * 1000L)
        } catch (e: Exception) {
            showKeepAlive()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Headsets.ensureChannels(this)
        try {
            ServiceCompat.startForeground(this, Headsets.ID_KEEPALIVE, Headsets.keepAliveNotification(this), fgsType())
        } catch (e: Exception) {
            stopSelf()
            return
        }

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val dev = addedDevices.firstOrNull { Headsets.isHeadsetDevice(it) } ?: return
                if (AppState.visible) return // l'app è aperta: ci pensa lei
                val info = Headsets.classify(this@HeadsetService, dev)
                val now = System.currentTimeMillis()
                // Un dispositivo può annunciarsi più volte di fila: una sola notifica.
                if (info.name == lastKey && now - lastTime < 20_000L) return
                lastKey = info.name
                lastTime = now
                showConnected(info)
            }
        }
        callback = cb
        am.registerAudioDeviceCallback(cb, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Headsets.ACTION_START_FAVORITES) {
            // Tocco su "Inizia ad ascoltare": torna la notifica discreta e parte un brano dei preferiti.
            handler.removeCallbacks(restore)
            showKeepAlive()
            try {
                startService(Intent(this, PlaybackService::class.java).setAction(Headsets.ACTION_START_FAVORITES))
            } catch (e: Exception) {
                // ignorato
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(restore)
        callback?.let {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it)
        }
        super.onDestroy()
    }
}

/** Riavvia il rilevamento dopo l'accensione del telefono o l'aggiornamento dell'app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Headsets.startService(context)
    }
}
