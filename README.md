# Lux · Perfume Cabinet

A private cabinet for your fragrance collection: shelves, wears, journal, statistics and a Test lab
(`lux-test.js`) for timed fragrance tests with weather. Everything is stored locally on your device
(`localStorage` and IndexedDB for photos).

## Daily pick, reminders, wishlist and sharing
* **Today's pick** (top of Collection and Journal): suggests a bottle for the day from the season, today's weather
  in your city, the occasion (Everyday, Work, Evening), how long it has rested and your rating, and says why.
  Set the city in Settings → *Daily pick and reminders*. Weather comes from Open-Meteo, no account needed.
* **Reminders** (Android app): a morning notification with today's pick and an evening reminder that only shows
  when nothing is logged for the day. Both have their own time.
* **Wishlist**: price seen, target price, where to buy and how much you want it. *I bought it* moves the bottle
  to your first shelf with the price filled in.
* **Share**: Collection → *Share* draws a picture (most worn, this month, the collection or the wishlist) to share
  or save.

## Backup
Settings → **Backup and restore**: download a backup file, copy it as text, or restore from a file or pasted text.
A backup holds the collection, journal, tests, settings, photos and your own library entries.
Lux also keeps a few automatic snapshots and reminds you when the last backup is older than 14 days.

## Install as an app (PWA)
* **Android / Chrome:** open the GitHub Pages link, then menu → *Install app* (or *Add to Home screen*).
* **iPhone / Safari:** open the link, tap *Share* → *Add to Home Screen*.

It works offline after the first visit. When changing `index.html`, `lux-test.js` or the icons,
bump `VERSION` in `sw.js` so installed copies pick up the update.

## Android app (APK)
Every change merged into `main` builds a new APK with GitHub Actions and publishes it as a release.
Always the newest version: https://github.com/kosmet-crypto/Fragrance-Shelf-Lux/releases/latest/download/lux.apk

1. Open the link on your Android phone and download `lux.apk`.
2. Open the file. Android will ask to allow installs from your browser or file manager; allow it once.
3. Install. Newer APKs install over the old one and keep your data.

Updates come in two kinds, and the app checks for both on launch (at most once an hour) and with
Settings → **Check for updates**:

* **Content updates are silent.** Most changes are in `index.html` and `lux-test.js`. The app downloads them from
  the newest commit on `main` and uses them from the next launch (a manual check switches right away).
  No APK, no install screen, data stays in place. Content that needs a newer app than the installed one
  (`<meta name="lux-native">` above `Ota.NATIVE_API`) waits for the APK update.
* **App updates install from inside the app.** When a newer release exists, tap **Update**. The first time,
  Android asks you to let Lux install apps. After that, on Android 12 and newer the update usually installs
  without the install screen; some phones still ask to confirm. The app closes while it updates and a
  notification offers to open the new version.

When a change touches the Android side (`android/`), bump `Ota.NATIVE_API` and the `lux-native` meta tag together
if the page starts to rely on it.

The APK bundles the web app, so it works offline from the first launch. Its data is stored inside the app,
separately from the browser version, so use **Download backup** in the browser and **Restore from file**
in the app (Settings) to move your data.
The Android project lives in `android/` (a small WebView wrapper). To build locally: `cd android && ./gradlew assembleRelease`.
