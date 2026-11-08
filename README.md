<div align="center">

# 🎵 Sonora

**My music app for Android, YouTube Music style** 😎
Plays in the background, saves songs offline and the lyrics go in time 🎤

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-0.90--beta-6750A4)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

<a href="https://realmike0.github.io/sonora/fdroid/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/realmike0/sonora"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a>

[⬇️ **Download the APK**](https://github.com/realmike0/sonora/releases) · [💬 **Telegram channel**](https://t.me/sonoraclient)

</div>

---

## ✨ What is Sonora

Sonora is an app to listen to music from YouTube Music **in your phone's colors** 🎨: the interface follows Material You and adapts to the wallpaper you picked. Music keeps playing with the screen off, with controls in the notification and on the lock screen.

I made it myself, from my phone 📱 (yes, with Termux!). It's still in **beta**, so updates come out often. If you find a bug, tell me 🙏

## 🚀 What it can do

- 🏠 **Home with daily picks**: a 3×3 grid that changes every day, then song shelves like on YouTube Music
- 🔎 **Search** for songs and artists, also **by voice** 🎙️
- 🎧 **Background playback** with a media notification
- 📝 **Synced lyrics** line by line, from 5 sources, with translation and a share image
- ❤️ **Favorites and playlists** that are all yours
- 🔗 **Optional Google account**: link it to import your YouTube Music playlists, skip it to use a local account
- 💾 **Automatic cache** and ⬇️ **downloads**: long-press a song and choose *Download*
- ⚙️ **Storage under control**: cache limit from 1 GB to unlimited, or clear it whenever you want
- 🤖 **AI**: rating for artists and albums, daily picks and queue remix
- 💌 **Dedications**: dedicate a song to someone, everyone with a Sonora ID can see it
- 🌍 **Languages**: Italiano, English, Español, Deutsch, Français, Türkçe and Shqip
- 📳 **Audio haptics**: your phone vibrates to the beat, with an adjustable sensitivity
- 🎧 **Headphones, speakers, car**: as soon as they connect you get a blue notification with "Start listening"
- 🎚️ **Smooth mixing** between songs and an equalizer
- 📱 **Tablet** with two panes, **landscape phone** with AOD mode
- 🔄 **Automatic updates** from the GitHub releases

## 🔒 Privacy and Wrapped

Tracking is **optional**: after onboarding you get asked, and if you say no nothing leaves your phone. If you accept, you verify with Telegram or with an email code and only from then your listening goes to the server for the Wrapped. You can turn it off or delete everything from Settings. All the details are in [docs/PRIVACY.md](docs/PRIVACY.md), the server guide is in [server/README.md](server/README.md).

## 📲 How to install

Pick the way you like 👇

**1️⃣ F-Droid** 🟢 — tap the "Get it on F-Droid" button above, add the repository and install Sonora. Updates arrive on their own.

**2️⃣ Obtainium** 🟣 — tap the "Get it on Obtainium" button above. Or in Obtainium: *Add app*, paste `https://github.com/realmike0/sonora` and turn on **Include pre-releases** (betas are pre-releases).

**3️⃣ By hand** 📦 — download the latest `.apk` from the [Releases](https://github.com/realmike0/sonora/releases) page and open it. If Android asks, allow installing from this source.

On first launch write your name and choose whether to link your Google account. On Android 13+ allow notifications, they are needed for the playback controls 🔔

> 💡 If you use F-Droid or Obtainium you can turn off the automatic check in Sonora's *Settings → Updates*.

**Requirements:** Android 8.0 (API 26) or later.

**iOS (experimental) 🍎:** the code is in `ios/`, made for Swift Playgrounds. For now I only use it to test, nothing official.

## 🔧 How it's made

- Interface in **Kotlin + Jetpack Compose** (Material 3)
- Playback with **Media3 / ExoPlayer** in a foreground service, so it never stops
- Songs are found with **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)**
- Lyrics come from **[LRCLIB](https://lrclib.net)** and other sources
- The server (Wrapped, Sonora ID, dedications, AI) runs on **Cloudflare Workers + D1**
- Profile, favorites, playlists and history stay **only on your phone**, unless you turn on sync
- There's also **Sonora Web**, a separate player in the `web/` folder, for older browsers and devices

## 💬 Stay updated

News, previews and support on the Telegram channel 👇

### 👉 [t.me/sonoraclient](https://t.me/sonoraclient)

Join so you don't miss any update and to report bugs or suggest ideas 💡

## ⚠️ Note

Sonora is not affiliated with YouTube, Google or YouTube Music. It relies on unofficial web services and might stop working if YouTube changes something. Use it to listen to content in respect of the terms of the services you use.

---

<div align="center">

*Made with ❤️ by [realmike0](https://www.github.com/realmike0) on GitHub*

</div>
