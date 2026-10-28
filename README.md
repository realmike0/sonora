<div align="center">

# 🎵 Sonora

**Il tuo client musicale per Android, in stile YouTube Music.**
Ascolta in background, salva i brani offline, leggi i testi sincronizzati.

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Versione](https://img.shields.io/badge/versione-0.30--beta-6750A4)
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
- ℹ️ **Menu del brano** (pressione prolungata): durata, data di pubblicazione, aggiungi ai preferiti o a una playlist, scarica.

## 📲 Come installarla

1. Vai alla pagina delle [**Release**](https://github.com/realmike0/sonora/releases) e scarica l'ultimo file `.apk`.
   *(In alternativa, scarica l'APK più recente dalla sezione **Actions** del repository, nel risultato dell'ultima build.)*
2. Aprilo dal telefono. Se Android lo chiede, consenti l'installazione da questa fonte.
3. Alla prima apertura scrivi il tuo nome e scegli se collegare l'account Google.
4. Su Android 13+ accetta le notifiche, servono per i controlli di riproduzione.

**Requisiti:** Android 8.0 (API 26) o successivo.

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
