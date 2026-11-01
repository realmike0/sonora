<div align="center">

# 🎵 Sonora

**Il tuo client musicale per Android, in stile YouTube Music.**
Ascolta in background, salva i brani offline, leggi i testi sincronizzati.

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Versione](https://img.shields.io/badge/versione-0.60.2--beta-6750A4)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

[⬇️ **Scarica l'APK**](https://github.com/realmike0/sonora/releases) · [💬 **Canale Telegram**](https://t.me/sonoraclient)

</div>

---

## ✨ Cos'è Sonora

Sonora è un'app Android per ascoltare musica da YouTube Music **con i colori del tuo telefono**: l'interfaccia segue Material You e si adatta allo sfondo che hai scelto. La musica continua anche con lo schermo spento o l'app in background, con i controlli nella notifica e nella schermata di blocco.

È un progetto indipendente, nato e portato avanti da **realmike0**, ed è in **beta**: arrivano aggiornamenti spesso.

## 🚀 Cosa puoi fare

- 🏠 **Home con le scelte del giorno**: una griglia 3×3 che cambia ogni giorno, poi gli scaffali di brani come su YouTube Music.
- 🔎 **Ricerca** di brani e artisti, con i brani che hai riprodotto dopo le tue ricerche.
- 🎧 **Ascolto in background** con notifica multimediale.
- 📝 **Testi sincronizzati** riga per riga (fonte: LRCLIB).
- ❤️ **Preferiti e playlist** tutte tue, salvate sul telefono.
- 🔗 **Account Google opzionale**: collegalo per importare le tue playlist di YouTube Music con tutti i brani; se lo salti, usi un account locale.
- 💾 **Cache automatica**: ogni brano che ascolti viene salvato, così lo ritrovi anche senza rete.
- ⬇️ **Download**: tieni premuto su un brano e scegli *Scarica*. Se era già in cache passa nei download senza riscaricarlo.
- 🗂️ **Raccolta** con due sezioni separate: *In cache* e *Scaricati*.
- ⚙️ **Gestione della memoria**: scegli il limite della cache (1, 2, 5, 10 GB o illimitata) o svuotala quando vuoi.
- 🔄 **Aggiornamenti automatici**: Sonora controlla le release su GitHub e li installa da solo.
- 🎧 **Cuffie, auricolari, speaker e auto**: quando si collegano, anche ad app chiusa, arriva una notifica blu con "Inizia ad ascoltare" (preferiti in ordine casuale). Con l'app aperta compare una schermata con un modello 3D che gira.
- 🎚️ **Mixaggio fluido** tra un brano e l'altro e **testi** con sfocatura sopra e sotto la riga corrente.
- 📱 **Tablet** in orizzontale: interfaccia a due pannelli. **Telefono** in orizzontale: modalità AOD con ora, notifiche e brano.
- ℹ️ **Menu del brano** (pressione prolungata): durata, data di pubblicazione, aggiungi ai preferiti o a una playlist, scarica.

## 🆕 Novità della 0.50

- **Wrapped** di ogni anno (locale, oppure con server se accetti il tracciamento) · coda di riproduzione · pillola del volume con riconoscimento del dispositivo (cuffie, speaker, auto) e vibrazione leggera
- **Widget**: ascolti e in riproduzione, attività recente, scorciatoia all'artista che scegli
- Qualità audio (M4A/WebM × alta/media/bassa) · dissolvenza avanzata · pagina artista ridisegnata · grafica leggera automatica sui dispositivi datati

- **Testi da 5 fonti** (LRCLIB, BetterLyrics, Unison, LyricsPlus, KuGou) con scelta automatica del testo migliore · **immagine dai testi** da condividere · feedback aptico al tocco · Wrapped di tutti gli anni

- **0.60:** gesto indietro predittivo · equalizzatore · riconoscimento brani (microfono) · interruttore audio/video originale · sovrapposizione vera tra i brani
- **0.60.1:** nuovo player a schermo intero (barra a onda, controlli a pillola, casuale) · schermata testi con controlli animati · rimosso l'accesso alle notifiche (blocco Play Protect)
- **0.60.2:** impostazioni più pulite (profilo con account Google, niente descrizioni) · Wrapped visibile a dicembre · APK firmato come release

## 🔒 Privacy e Wrapped

Il tracciamento è **facoltativo**: dopo l'onboarding ti viene chiesto e se rifiuti nulla lascia il telefono. Se accetti, verifichi chi sei con Telegram (numero) o con un codice via email e solo da quel momento i tuoi ascolti vanno al server per il Wrapped. Puoi spegnere o cancellare tutto dalle Impostazioni. Dettagli in [docs/PRIVACY.md](docs/PRIVACY.md); guida al server in [server/README.md](server/README.md).

## 📲 Come installarla

1. Vai alla pagina delle [**Release**](https://github.com/realmike0/sonora/releases) e scarica l'ultimo file `.apk`.
   *(In alternativa, scarica l'APK più recente dalla sezione **Actions** del repository, nel risultato dell'ultima build.)*
2. Aprilo dal telefono. Se Android lo chiede, consenti l'installazione da questa fonte.
3. Alla prima apertura scrivi il tuo nome e scegli se collegare l'account Google.
4. Su Android 13+ accetta le notifiche, servono per i controlli di riproduzione.

**iOS (sperimentale):** il codice è in `ios/`. La build produce un IPA non firmato (Actions → *Build iOS IPA*) da installare con AltStore o Sideloadly. Su iOS 26+ l'interfaccia usa Liquid Glass; sulle versioni precedenti l'interfaccia standard.

**Requisiti:** Android 8.0 (API 26) o successivo.

### 🔄 Aggiornamenti con Obtainium

Per ricevere automaticamente ogni nuova versione puoi usare [Obtainium](https://github.com/ImranR98/Obtainium):

[➕ **Aggiungi Sonora a Obtainium**](obtainium://add/https://github.com/realmike0/sonora)

Oppure, in Obtainium, tocca *Aggiungi app*, incolla `https://github.com/realmike0/sonora` e attiva **Includi pre-release** (le versioni beta sono pre-release). Se usi Obtainium, puoi spegnere il controllo automatico in *Impostazioni → Aggiornamenti* di Sonora.

## 🔧 Come funziona

- Interfaccia in **Kotlin + Jetpack Compose** (Material 3).
- Riproduzione con **Media3 / ExoPlayer** in un servizio in primo piano, così non si ferma in background.
- I brani vengono trovati con **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)**; l'importazione delle playlist usa l'accesso web di YouTube Music con la sessione del tuo account Google.
- I testi arrivano da **[LRCLIB](https://lrclib.net)**.
- Profilo, preferiti, playlist e cronologia restano **solo sul tuo telefono**.
- La cache audio e i download usano due archivi separati; la cache elimina da sola i brani meno recenti quando supera il limite scelto.

## 💬 Resta aggiornato

Novità, anteprime delle nuove versioni e supporto sul canale Telegram:

### 👉 [t.me/sonoraclient](https://t.me/sonoraclient)

Entra per non perdere nessun aggiornamento e per segnalare bug o proporre idee.

## ⚠️ Nota

Sonora non è affiliata a YouTube, Google o YouTube Music. L'importazione delle playlist si basa su servizi web non ufficiali e potrebbe smettere di funzionare se YouTube cambia qualcosa. Usala per ascoltare contenuti nel rispetto dei termini dei servizi che utilizzi.

---

<div align="center">

*Made with ❤️ by [realmike0](https://www.github.com/realmike0) on GitHub*

</div>
