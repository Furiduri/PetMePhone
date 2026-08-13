# `:spike:ime-viability`

A standalone, installable Android app that measures whether in-overlay text entry is viable on
your device. It is not a demo — its output is evidence for a real scope decision (issue #18, and
the tracked back-gesture deviation on issue #17). It shares no code with the production overlay:
not the window controller, not the placement math, not `MetricReading`.

It is never built into `:app`'s release APK. It is its own application module with its own
`applicationId` (`com.petmephone.spike.imeviability`), and can be removed with one line in
`settings.gradle.kts` if it is no longer needed.

## Why this can't be run from CI

adb-injected input does not reach an overlay window on every OEM skin (verified: it does not on
the maintainer's HyperOS device), and the questions this spike asks — does a keyboard visually
cover a field, does a video pause when a window steals focus — need a human watching a real
screen. There is no way to automate that honestly.

## Install and grant the overlay permission

```
./gradlew :spike:ime-viability:installDebug

# Overlay permission cannot be granted via adb on most OEM skins; open it in Settings from the app
# itself instead — the app has a "Grant overlay permission" button that does exactly this. If your
# device does support the appops route:
adb shell appops set com.petmephone.spike.imeviability SYSTEM_ALERT_WINDOW allow
```

## The two modes

- **Focus-only** — a focusable window with no text field at all, no keyboard ever raised. This
  isolates the cost of a window merely taking focus, independent of the keyboard.
- **Full IME** — a focusable window with a real text field. The soft keyboard is expected to
  raise when the field takes input focus.

Recording both separately is the point: without the split, a "video paused" result can't tell you
whether the keyboard did it or the window focus alone did it — and the back-gesture decision on
issue #17 depends specifically on the focus-only answer.

## Running a measurement

1. Open the app, grant the overlay permission if you haven't already.
2. Pick a mode.
3. Tap **Start**. You have a few seconds before the window appears — switch to another app and
   start a video playing.
4. Watch what happens: does the keyboard appear (Full IME only), does it cover the field, does the
   video pause.
5. Tap **Finish**. The app asks two questions — did the video pause, did focus return correctly
   afterward — and records your answers alongside everything it measured automatically (keyboard
   presence, field coverage, whether any IME inset callback fired at all, and whether the window
   was removed cleanly).

Repeat for the other mode, and on any other device/OEM skin you want data from — each run appends
a new entry, so nothing already recorded is lost.

## Where the findings file lands

`/sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md`

(app-specific external storage — `Context.getExternalFilesDir(null)`). Pull it with:

```
adb pull /sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md
```

or use the in-app **Share findings file** button, which opens the system share sheet with the
file attached. Either way, commit the result verbatim under
`openspec/changes/slice-3-b-a-pet-you-can-talk-to/spike-findings/` — see that folder's own
`README.md` for the expected format.
