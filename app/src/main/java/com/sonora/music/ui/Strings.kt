package com.sonora.music.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Lingua dell'app: "auto" segue il telefono, altrimenti it / en / es / de. */
object Lang {
    var code by mutableStateOf("auto")
        private set

    val options = listOf("auto" to "Automatica", "it" to "Italiano", "en" to "English", "es" to "Español", "de" to "Deutsch")

    fun load(c: Context) {
        code = c.getSharedPreferences("sonora", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"
    }

    fun set(c: Context, value: String) {
        code = value
        c.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putString("lang", value).apply()
    }

    /** Lingua effettiva (mai "auto"). Le lingue non supportate usano l'inglese. */
    fun effective(): String {
        val v = if (code == "auto") Locale.getDefault().language else code
        return if (v in setOf("it", "en", "es", "de")) v else "en"
    }
}

/** Traduce una stringa scritta in italiano. Se manca nella tabella resta com'è. */
fun tr(s: String): String {
    val l = Lang.effective()
    if (l == "it") return s
    val idx = when (l) { "en" -> 0; "es" -> 1; else -> 2 }
    return TABLE[s]?.get(idx) ?: s
}

private val TABLE: Map<String, List<String>> = mapOf(
    "Accedi a Google" to listOf("Sign in to Google", "Iniciar sesión en Google", "Bei Google anmelden"),
    "Accetto" to listOf("I agree", "Acepto", "Ich stimme zu"),
    "Account locale" to listOf("Local account", "Cuenta local", "Lokales Konto"),
    "Aggiornamenti" to listOf("Updates", "Actualizaciones", "Updates"),
    "Aggiornamento disponibile" to listOf("Update available", "Actualización disponible", "Update verfügbar"),
    "Aggiungi a playlist" to listOf("Add to playlist", "Añadir a playlist", "Zur Playlist hinzufügen"),
    "Aggiungi alla coda" to listOf("Add to queue", "Añadir a la cola", "Zur Warteschlange"),
    "Altro" to listOf("More", "Más", "Mehr"),
    "Annulla" to listOf("Cancel", "Cancelar", "Abbrechen"),
    "Apri Wrapped" to listOf("Open Wrapped", "Abrir Wrapped", "Wrapped öffnen"),
    "Apri su Telegram" to listOf("Open on Telegram", "Abrir en Telegram", "In Telegram öffnen"),
    "Ascolta" to listOf("Listen", "Escuchar", "Zuhören"),
    "Attiva il tracciamento" to listOf("Turn on tracking", "Activar el seguimiento", "Tracking aktivieren"),
    "Avanti di 10 secondi" to listOf("Forward 10 seconds", "Adelantar 10 segundos", "10 Sekunden vor"),
    "Avvia un brano dai risultati e lo ritroverai qui" to listOf("Play a song from the results and you'll find it here", "Reproduce una canción de los resultados y la encontrarás aquí", "Spiele einen Titel aus den Ergebnissen und du findest ihn hier"),
    "Avvia un brano per regolare l'equalizzatore (o il telefono non lo supporta)." to listOf("Play a song to adjust the equalizer (or your phone doesn't support it).", "Reproduce una canción para ajustar el ecualizador (o tu teléfono no lo admite).", "Spiele einen Titel, um den Equalizer einzustellen (oder dein Telefon unterstützt ihn nicht)."),
    "Avvicina il telefono alla musica: ascolto per 10 secondi." to listOf("Hold your phone close to the music: listening for 10 seconds.", "Acerca el teléfono a la música: escucho 10 segundos.", "Halte das Telefon an die Musik: Ich höre 10 Sekunden zu."),
    "Bassi" to listOf("Bass", "Graves", "Bass"),
    "Cancella" to listOf("Clear", "Borrar", "Löschen"),
    "Casuale" to listOf("Shuffle", "Aleatorio", "Zufall"),
    "Cerca brani, artisti…" to listOf("Search songs, artists…", "Buscar canciones, artistas…", "Titel, Künstler suchen…"),
    "Cerca un brano o un artista per iniziare" to listOf("Search for a song or an artist to start", "Busca una canción o un artista para empezar", "Suche einen Titel oder Künstler zum Start"),
    "Cerco il brano…" to listOf("Looking for the song…", "Buscando la canción…", "Suche den Titel…"),
    "Che brano è?" to listOf("What song is this?", "¿Qué canción es?", "Welcher Titel ist das?"),
    "Chiudi" to listOf("Close", "Cerrar", "Schließen"),
    "Coda" to listOf("Queue", "Cola", "Warteschlange"),
    "Codice a 6 cifre" to listOf("6-digit code", "Código de 6 dígitos", "6-stelliger Code"),
    "Collega account Google" to listOf("Connect Google account", "Conectar cuenta de Google", "Google-Konto verbinden"),
    "Community" to listOf("Community", "Comunidad", "Community"),
    "Continua" to listOf("Continue", "Continuar", "Weiter"),
    "Continua senza account Google" to listOf("Continue without a Google account", "Continuar sin cuenta de Google", "Ohne Google-Konto fortfahren"),
    "Controllo automatico" to listOf("Automatic check", "Comprobación automática", "Automatische Prüfung"),
    "Crea" to listOf("Create", "Crear", "Erstellen"),
    "Crea immagine dal testo" to listOf("Create image from lyrics", "Crear imagen de la letra", "Bild aus Songtext erstellen"),
    "Curva" to listOf("Curve", "Curva", "Kurve"),
    "Dissolvenza avanzata" to listOf("Advanced fade", "Fundido avanzado", "Erweiterter Übergang"),
    "Dissolvenza tra i brani" to listOf("Fade between songs", "Fundido entre canciones", "Übergang zwischen Titeln"),
    "Dopo" to listOf("Next", "Siguiente", "Weiter"),
    "Elimina" to listOf("Delete", "Eliminar", "Löschen"),
    "Elimina i miei dati" to listOf("Delete my data", "Eliminar mis datos", "Meine Daten löschen"),
    "Elimina playlist" to listOf("Delete playlist", "Eliminar playlist", "Playlist löschen"),
    "Eliminare la playlist?" to listOf("Delete the playlist?", "¿Eliminar la playlist?", "Playlist löschen?"),
    "Equalizzatore" to listOf("Equalizer", "Ecualizador", "Equalizer"),
    "Feedback aptico al tocco" to listOf("Haptic feedback on touch", "Respuesta háptica al tocar", "Haptisches Feedback beim Tippen"),
    "Fonte predefinita" to listOf("Default source", "Fuente predeterminada", "Standardquelle"),
    "Formato" to listOf("Format", "Formato", "Format"),
    "I brani in cache verranno eliminati. I brani scaricati restano." to listOf("Cached songs will be deleted. Downloaded songs stay.", "Se eliminarán las canciones en caché. Las descargadas se conservan.", "Zwischengespeicherte Titel werden gelöscht. Heruntergeladene bleiben."),
    "I due brani suonano insieme (con la grafica leggera si usa la dissolvenza semplice)." to listOf("The two songs play together (simple fade is used with light graphics).", "Las dos canciones suenan juntas (con gráficos ligeros se usa el fundido simple).", "Beide Titel spielen gleichzeitig (mit leichter Grafik wird der einfache Übergang genutzt)."),
    "Il brano finisce con una dissolvenza e il successivo entra in sfumatura." to listOf("The song fades out and the next one fades in.", "La canción termina con un fundido y la siguiente entra suavemente.", "Der Titel blendet aus und der nächste blendet ein."),
    "Il tuo nome" to listOf("Your name", "Tu nombre", "Dein Name"),
    "Impostazioni" to listOf("Settings", "Ajustes", "Einstellungen"),
    "Indietro" to listOf("Back", "Atrás", "Zurück"),
    "Indietro di 10 secondi" to listOf("Back 10 seconds", "Atrasar 10 segundos", "10 Sekunden zurück"),
    "Indirizzo server" to listOf("Server address", "Dirección del servidor", "Serveradresse"),
    "Informazioni" to listOf("About", "Información", "Info"),
    "Leggi la privacy policy" to listOf("Read the privacy policy", "Leer la política de privacidad", "Datenschutzerklärung lesen"),
    "Memoria e cache" to listOf("Storage and cache", "Memoria y caché", "Speicher und Cache"),
    "Mixaggio fluido" to listOf("Smooth mixing", "Mezcla fluida", "Fließendes Mischen"),
    "Nessun brano in riproduzione" to listOf("Nothing playing", "Nada en reproducción", "Keine Wiedergabe"),
    "Niente in coda" to listOf("Nothing in the queue", "Nada en la cola", "Nichts in der Warteschlange"),
    "Nome della playlist" to listOf("Playlist name", "Nombre de la playlist", "Playlist-Name"),
    "Non l'ho riconosciuto" to listOf("I couldn't recognize it", "No lo he reconocido", "Nicht erkannt"),
    "Non ora" to listOf("Not now", "Ahora no", "Nicht jetzt"),
    "Novità, aggiornamenti e supporto sul canale Telegram di Sonora." to listOf("News, updates and support on the Sonora Telegram channel.", "Novedades, actualizaciones y soporte en el canal de Telegram de Sonora.", "Neuigkeiten, Updates und Support im Sonora-Telegram-Kanal."),
    "Nuova playlist" to listOf("New playlist", "Nueva playlist", "Neue Playlist"),
    "Opzioni" to listOf("Options", "Opciones", "Optionen"),
    "Per verificare che sei tu usiamo Telegram (numero di telefono) oppure l'email: vengono conservati solo come impronta (hash), mai in chiaro. Niente pubblicità, niente vendita dei dati. Puoi smettere e cancellare tutto quando vuoi dalle Impostazioni." to listOf("To check it's you we use Telegram (phone number) or email: they are stored only as a hash, never in plain text. No ads, no selling of data. You can stop and delete everything anytime in Settings.", "Para verificar que eres tú usamos Telegram (número de teléfono) o el correo: solo se guardan como huella (hash), nunca en claro. Sin publicidad ni venta de datos. Puedes parar y borrar todo cuando quieras en Ajustes.", "Zur Bestätigung nutzen wir Telegram (Telefonnummer) oder E-Mail: Sie werden nur als Hash gespeichert, nie im Klartext. Keine Werbung, kein Datenverkauf. Du kannst jederzeit in den Einstellungen aufhören und alles löschen."),
    "Playlist sincronizzate" to listOf("Playlists synced", "Playlists sincronizadas", "Playlists synchronisiert"),
    "Prestazioni" to listOf("Performance", "Rendimiento", "Leistung"),
    "Privacy" to listOf("Privacy", "Privacidad", "Datenschutz"),
    "Profilo" to listOf("Profile", "Perfil", "Profil"),
    "Qualità" to listOf("Quality", "Calidad", "Qualität"),
    "Qualità audio" to listOf("Audio quality", "Calidad de audio", "Audioqualität"),
    "RIPRODUCI" to listOf("PLAY", "REPRODUCIR", "ABSPIELEN"),
    "Raccolta" to listOf("Library", "Biblioteca", "Bibliothek"),
    "Riapri Telegram" to listOf("Reopen Telegram", "Reabrir Telegram", "Telegram erneut öffnen"),
    "Ricerca" to listOf("Search", "Buscar", "Suche"),
    "Riconosci brano" to listOf("Identify song", "Reconocer canción", "Titel erkennen"),
    "Riconosci cuffie e speaker" to listOf("Detect headphones and speakers", "Detectar auriculares y altavoces", "Kopfhörer und Lautsprecher erkennen"),
    "Rimuovi" to listOf("Remove", "Quitar", "Entfernen"),
    "Rimuovi dai download" to listOf("Remove from downloads", "Quitar de descargas", "Aus Downloads entfernen"),
    "Rimuovi dalla playlist" to listOf("Remove from playlist", "Quitar de la playlist", "Aus Playlist entfernen"),
    "Riprodotti dopo le tue ricerche" to listOf("Played after your searches", "Reproducido tras tus búsquedas", "Nach deinen Suchen gespielt"),
    "Riproduci" to listOf("Play", "Reproducir", "Abspielen"),
    "Riproduci dopo" to listOf("Play next", "Reproducir después", "Als Nächstes spielen"),
    "Riproduci le scelte" to listOf("Play the picks", "Reproducir la selección", "Auswahl abspielen"),
    "Riprova" to listOf("Try again", "Reintentar", "Erneut versuchen"),
    "Salta" to listOf("Skip", "Omitir", "Überspringen"),
    "Scarica" to listOf("Download", "Descargar", "Herunterladen"),
    "Scegli fino a 8 righe" to listOf("Pick up to 8 lines", "Elige hasta 8 líneas", "Wähle bis zu 8 Zeilen"),
    "Scegli l'artista" to listOf("Choose the artist", "Elige el artista", "Künstler wählen"),
    "Scegli un brano per iniziare" to listOf("Pick a song to start", "Elige una canción para empezar", "Wähle einen Titel zum Start"),
    "Scelte del giorno" to listOf("Daily picks", "Selección del día", "Tagesauswahl"),
    "Scollega" to listOf("Disconnect", "Desconectar", "Trennen"),
    "Se accetti, Sonora salva sul nostro server i brani che ascolti (titolo, artista, orario, durata) per creare il tuo Wrapped di ogni anno." to listOf("If you agree, Sonora saves the songs you play (title, artist, time, duration) on our server to create your yearly Wrapped.", "Si aceptas, Sonora guarda en nuestro servidor las canciones que escuchas (título, artista, hora, duración) para crear tu Wrapped anual.", "Wenn du zustimmst, speichert Sonora die gehörten Titel (Titel, Künstler, Zeit, Dauer) auf unserem Server, um dein jährliches Wrapped zu erstellen."),
    "Se rifiuti, nulla lascia il telefono: il Wrapped resta solo locale." to listOf("If you decline, nothing leaves your phone: Wrapped stays local only.", "Si rechazas, nada sale del teléfono: el Wrapped queda solo en local.", "Wenn du ablehnst, verlässt nichts dein Telefon: Wrapped bleibt nur lokal."),
    "Se salti, creiamo un profilo locale: Mi piace e playlist restano su questo telefono." to listOf("If you skip, we create a local profile: Likes and playlists stay on this phone.", "Si omites, creamos un perfil local: Me gusta y playlists se quedan en este teléfono.", "Wenn du überspringst, legen wir ein lokales Profil an: Likes und Playlists bleiben auf diesem Telefon."),
    "Sempre attiva" to listOf("Always on", "Siempre activa", "Immer aktiv"),
    "Sincronizza ora" to listOf("Sync now", "Sincronizar ahora", "Jetzt synchronisieren"),
    "Sovrapposizione vera" to listOf("True overlap", "Superposición real", "Echte Überblendung"),
    "Spegni" to listOf("Turn off", "Apagar", "Ausschalten"),
    "Sposta su" to listOf("Move up", "Subir", "Nach oben"),
    "Sto ascoltando…" to listOf("Listening…", "Escuchando…", "Ich höre zu…"),
    "Svuota" to listOf("Clear", "Vaciar", "Leeren"),
    "Svuota cache" to listOf("Clear cache", "Vaciar caché", "Cache leeren"),
    "Svuotare la cache?" to listOf("Clear the cache?", "¿Vaciar la caché?", "Cache leeren?"),
    "Te lo chiediamo una volta sola." to listOf("We'll only ask once.", "Solo te lo pedimos una vez.", "Wir fragen nur einmal."),
    "Testi" to listOf("Lyrics", "Letras", "Songtexte"),
    "Testo non disponibile per questo brano" to listOf("Lyrics not available for this song", "Letra no disponible para esta canción", "Songtext für diesen Titel nicht verfügbar"),
    "Tocco" to listOf("Touch", "Tacto", "Berührung"),
    "Una vibrazione leggerissima quando tocchi qualcosa." to listOf("A very light vibration when you touch something.", "Una vibración muy leve al tocar algo.", "Eine ganz leichte Vibration beim Tippen."),
    "Verifica con Telegram" to listOf("Verify with Telegram", "Verificar con Telegram", "Mit Telegram bestätigen"),
    "Verifica con email" to listOf("Verify with email", "Verificar con correo", "Per E-Mail bestätigen"),
    "Volume" to listOf("Volume", "Volumen", "Lautstärke"),
    "Volume extra" to listOf("Extra volume", "Volumen extra", "Zusatzlautstärke"),
    "Wrapped e tracciamento" to listOf("Wrapped and tracking", "Wrapped y seguimiento", "Wrapped und Tracking"),
    "Esplora" to listOf("Explore", "Explorar", "Entdecken"),
    "Home" to listOf("Home", "Inicio", "Start"),
    "Ultimi 7 giorni" to listOf("Last 7 days", "Últimos 7 días", "Letzte 7 Tage"),
    "Artisti" to listOf("Artists", "Artistas", "Künstler"),
    "Brani" to listOf("Songs", "Canciones", "Titel"),
    "ascolti" to listOf("plays", "reproducciones", "Wiedergaben"),
    "ascolto" to listOf("play", "reproducción", "Wiedergabe"),
    "Ascolta qualche brano e qui vedrai le tue statistiche" to listOf("Listen to a few songs and your stats will show up here", "Escucha algunas canciones y aquí verás tus estadísticas", "Höre ein paar Titel und hier siehst du deine Statistiken"),
    "Generi" to listOf("Genres", "Géneros", "Genres"),
    "Playlist" to listOf("Playlist", "Playlist", "Playlist"),
    "Nessuna playlist trovata" to listOf("No playlists found", "No se encontraron playlists", "Keine Playlists gefunden"),
    "Lingua" to listOf("Language", "Idioma", "Sprache"),
    "Automatica" to listOf("Automatic", "Automático", "Automatisch"),
    "Traduci testo" to listOf("Translate lyrics", "Traducir letra", "Songtext übersetzen"),
    "Traduzione non riuscita" to listOf("Translation failed", "Traducción fallida", "Übersetzung fehlgeschlagen"),
    "Il tuo Wrapped è pronto" to listOf("Your Wrapped is ready", "Tu Wrapped está listo", "Dein Wrapped ist fertig"),
    "Scopri com'è stato il tuo anno in musica" to listOf("See how your year in music went", "Descubre cómo fue tu año en música", "Entdecke dein Musikjahr"),
    "Video" to listOf("Video", "Vídeo", "Video"),
    "Testo" to listOf("Lyrics", "Letra", "Songtext"),
)
