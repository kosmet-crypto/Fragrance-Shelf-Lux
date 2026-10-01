# Lux · Perfume Cabinet

A private cabinet for your fragrance collection: shelves, wears, journal, statistics and a Test lab
(`lux-test.js`) for timed fragrance tests with weather. Everything is stored locally on your device
(`localStorage` and IndexedDB for photos).

## Daily pick, reminders, wishlist and sharing
* **Today's pick** (top of Collection and Journal): suggests a bottle for the day from the season, today's weather
  in your city, the occasion (Everyday, Work, Evening), how long it has rested and your rating, and says why.
  Set the city in Settings → *Daily pick and reminders*. Weather comes from Open-Meteo, no account needed.
* **Reminders** (Android app): a morning notification with today's pick, at the time you choose.
* **Wishlist**: price seen, target price, where to buy and how much you want it. *I bought it* moves the bottle
  to your first shelf with the price filled in.
* **Share**: Collection → *Share* draws a picture (most worn, this month, the collection or the wishlist) to share
  or save, on a dark or light background with a gold, rose gold or silver accent.
* **Test lab results**: Test lab → *Share results* draws your five best tested fragrances with their scores;
  *Copy as text* copies every rated fragrance with its scores, longevity and review, ready to paste into a chat.
  Settings → *Tests as CSV* saves the same list as a spreadsheet.

## Navigation, notes, moods and more
* **Tabs**: Collection · Journal · Test · Stats · Settings. The **Library** opens from Collection (and Settings);
  importing a CSV or JSON library is in Settings → *Library and data*. Search suggestions still come from it everywhere.
* **Today's pick** is a compact card on Collection only: the bottle, the name and the main reason (tap *+ more* for
  the rest). Next to the occasion there is a **Mood**; the pick then favours what you wore before in that mood.
* **Mood when logging a wear** (optional): Relaxed, Energetic, Cozy, Confident, Romantic, Focused.
* **Notes pyramid**: in a bottle or a tested fragrance, *Paste notes* and paste the notes copied from Fragrantica
  or a shop page; top, heart and base fill in and can be corrected. Notes are kept per fragrance (house + name).
* **Seasons**: without your own *Best in*, Lux suggests seasons from the family and the notes (*use it* to keep them).
* **Decant or bottle?** (wishlist bottles, decants and tested fragrances): price per ml of each and how long each
  lasts at your pace.
* **Test lab**: *Compare* two tested fragrances side by side; a test you would buy offers *Add to wishlist* with the
  price noted in the test; *Best tested* image and text show the season.
* **Test check-ins** (Android): a notification two and six hours after you spray a test. Settings → *Daily pick and reminders*.
* **Photos**: Settings → *Add photos* picks several at once; photos named after a bottle are matched by themselves.
* **Accent colour**: Settings → gold, rose gold or silver, in both themes.
* **Fix a wear**: tap an entry in the Journal to change the fragrance, sprays or day, or delete it (× deletes too).
* **Layering**: in *Log a wear*, *Layer with another* logs two bottles at once (each loses its own sprays);
  Stats → *Layering* lists the pairs you wear.
* **This week's rotation** (Journal): seven bottles that waited longest, one a day; today's gets a push in the pick.
* **Close to what you own**: a wishlist bottle or a tested fragrance shows which of your bottles share its notes or family.
* **Your own library table**: Settings → *Table template* gives a CSV with House, Name, Family, Concentration, Year,
  Size, Top, Middle, Base. Fill it (Google Sheets works), then *Import CSV or JSON*: the notes become pyramids.
  The imported library stays on the phone and is part of the backup.
* **Widgets** (Android, 2 x 2): *Lux quick log* shows today's pick as a picture (your photo, else the drawn
  bottle); *Wear* logs it in one tap with your usual sprays, *+* opens the wear log, and the arrow *›* steps through today's pick and the next four suggestions, so *Wear*
  logs the one you see. Lux offers *Undo* when it adds a wear from the widget. *Lux today's pick* is the
  picture alone, *Lux stats* the day streak and wears this month. *Lux quick log (upright)* is the same quick log
  in 1 x 2. Every widget shows today's pick, the same as on Collection. A wear from the widget is added when Lux opens.
* When the in-app update is refused, a message shows Android's reason before the install screen opens.
  **Samsung:** Auto Blocker (Settings → Security and privacy) silently refuses app updates from outside the
  Galaxy Store; turn it off while updating.

## Stats, value and bottles
* **Stats** (was Insights): periods Today, 7, 30, 90 days, Year and All time; wears, sprays, amount used and value
  sprayed for the period; the collection's current value against what you paid; measures for current value,
  value sprayed and amount used; bottles without a price can be priced from there; bottles not worn for two
  months are listed and get a push in Today's pick.
* **Notes you rate highest**, **Cost of a wear** (your usual sprays × the price per ml), **Temperature and what you wear** (new wears keep the day's
  temperature when a city is set; the last three months can be filled in) and **When bottles run out**.
* **Sprays per ml** per bottle: your own number, or what Lux learns when you correct the remaining ml by hand,
  or the default from Settings, or by bottle size (10 for 100 ml, 12 for 50 to 75 ml, 15 for 30 ml and decants;
  common atomizers spray about 0.06 to 0.12 ml).
* **Almost empty** note under 10%, with the time left at your pace (last three months, and the last year for
  older bottles; the empty date is shown as a range). **Undo** right after logging a wear.
* **Monthly recap** in the first week of a month and **year in review** in December, both shareable as images.
* After a wear is logged, Today's pick on Collection folds into a line; tap it to add another wear.

## Backup
Settings → **Backup and restore**: download a backup file, copy it as text, or restore from a file or pasted text.
A backup holds the collection, journal, tests, settings, photos and your own library entries.
Lux also keeps a few automatic snapshots and reminds you when the last backup is older than 14 days.

## Name and icon
The app is called **Lux · Perfume Cabinet** (under the icon on the phone: *Lux Cabinet*). The icon is a rendered,
photo-like picture of a fictional Lux bottle. Made by Ivan S. · Epicurus001 · Srbija / Norge.

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
  without the install screen; some phones still ask to confirm. If the quiet install is refused, the app
  downloads the APK itself and opens Android's install screen, never the browser. The app closes while it updates and a
  notification offers to open the new version.

The APK update is only offered when the Android part (`android/`) changed: CI stores a fingerprint of that
folder in the APK and in the release notes (`native: …`), and the app compares the two. When a change touches the Android side, bump `Ota.NATIVE_API` and the `lux-native` meta tag together
if the page starts to rely on it.

The APK bundles the web app, so it works offline from the first launch. Its data is stored inside the app,
separately from the browser version, so use **Download backup** in the browser and **Restore from file**
in the app (Settings) to move your data.
The Android project lives in `android/` (a small WebView wrapper). To build locally: `cd android && ./gradlew assembleRelease`.
