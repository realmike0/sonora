# Sonora

Client musicale Android (Kotlin, Jetpack Compose, Material 3 Expressive).
Cerca brani su YouTube Music tramite NewPipeExtractor e li riproduce in background
con Media3 (notifica, schermata di blocco, cuffie/Bluetooth).

## Compilare

1. Apri la cartella in Android Studio (Ladybug o più recente) e lascia finire il sync.
2. Run su un dispositivo/emulatore (Android 8.0+), oppure da terminale:
   `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## Struttura

- `PlaybackService.kt` – servizio in primo piano + MediaSession; risolve l'URL audio al momento del play
- `MusicRepository.kt` – ricerca e estrazione dello stream (NewPipeExtractor)
- `PlayerViewModel.kt` – collega la UI al servizio tramite MediaController
- `ui/` – tema Expressive, ricerca, mini player con barra ondulata, player a schermo intero

## Se la riproduzione smette di funzionare

YouTube cambia spesso. Aggiorna la versione di `NewPipeExtractor` in `app/build.gradle.kts`
(ultime release: github.com/TeamNewPipe/NewPipeExtractor/releases).
