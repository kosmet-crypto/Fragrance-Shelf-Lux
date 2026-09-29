# Working on Lux (Fragrance-Shelf-Lux)

## How the owner wants to work (keep costs low)
- Reply in Serbian (Cyrillic). Keep messages short: what was done and what to try on the phone.
- Talk first when asked to "just talk"; change nothing until the owner says so.
- Bundle small changes into one PR: one build, one merge.
- Test in a headless browser with numbers/assertions. Take screenshots only when the look changes;
  the owner checks visuals in the app himself.
- Do not poll GitHub builds in a loop; wait in the background and report once when done.
- Do not use Artifacts for previews (extra rounds cost credits).
- Do not change: shelf reordering in Settings; separate Journal entries of the same fragrance on one day.
- Each app has its own repo and its own session; do not touch other repos from a Lux session.

## App basics
- Web app: index.html (UI, state, stats) + lux-test.js (Test lab). Data lives in localStorage/IndexedDB.
- Android wrapper in android/ (WebView). Web-only changes reach installed apps silently (Ota.java);
  a change under android/ changes the fingerprint in the release notes and the app offers "Update".
- Bump VERSION in sw.js when index.html or lux-test.js change.
