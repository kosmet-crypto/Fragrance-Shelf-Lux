# Lux · Perfume Cabinet

A private cabinet for your fragrance collection: shelves, wears, journal, statistics and a Test lab
(`lux-test.js`) for timed fragrance tests with weather. Everything is stored locally on your device
(`localStorage` and IndexedDB for photos).

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

The app checks for a newer release at most twice a day and offers to download it. Updates are not silent:
you tap **Download**, then open the file to install.

The APK bundles the web app, so it works offline from the first launch. Its data is stored inside the app,
separately from the browser version, so use **Download backup** in the browser and **Restore from file**
in the app (Settings) to move your data.
The Android project lives in `android/` (a small WebView wrapper). To build locally: `cd android && ./gradlew assembleRelease`.
