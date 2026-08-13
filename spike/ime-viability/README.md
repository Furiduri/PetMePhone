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

## The modes

- **Focus-only** — a focusable window with no text field at all, no keyboard ever raised. This
  isolates the cost of a window merely taking focus, independent of the keyboard.
- **Full IME (no strategy control)** — a focusable window with a real text field, no
  `softInputMode` and no repositioning. The baseline the three strategies are compared against.
- **Pan** — the control, plus `LayoutParams.softInputMode = SOFT_INPUT_ADJUST_PAN`.
- **Resize** — the control, plus `SOFT_INPUT_ADJUST_RESIZE`.
- **Anchor top on focus** — no `softInputMode` at all; the window is moved to the top of the usable
  bounds while the field holds focus and restored when it loses focus.
- **Two windows: card resizes, pet follows** — adds a focusable `ADJUST_RESIZE` card AND a second
  non-focusable block standing in for the pet, placed inside the band the keyboard occupies. It
  tests whether the card can act as the measuring instrument for a window the IME never targets.

Recording focus-only separately is the point: without the split, a "video paused" result can't tell
you whether the keyboard did it or the window focus alone did it — and the back-gesture decision on
issue #17 depends specifically on the focus-only answer.

## The measurement procedure

For the keyboard-visibility comparison (issue #18) — including the low-start-position requirement,
what to observe per mode, and where the exported file is committed — follow **`RUN.md`** next to
this file.

## Running a measurement

1. Open the app, grant the overlay permission if you haven't already.
2. Pick a mode.
3. Tap **Start**. You have a few seconds before the window appears — switch to another app and
   start a video playing.
4. Watch what happens: does the keyboard appear, does it cover the field, does the card move, does
   the video pause.
5. Tap **Finish**. The window is removed at that moment, before the question dialog opens — an
   overlay sits above the activity by definition, so a run left up would cover the questions. The
   app then asks everything it cannot answer for itself and records your answers alongside the raw
   geometry samples: the content top on screen, the orientation, the visible display frame, the
   window bounds, the field's on-screen bounds and the `LayoutParams.y` in effect. Samples are
   taken at the fixed moments, again after a late settle window, and on every layout change.

   The old derived `keyboardAppeared` / `keyboardCoversField` signals are gone: an overlay window is
   never told about the IME, so the first recorded `false` while the keyboard was in use and the
   second was a defaulted field rather than a measurement. Anything the window cannot observe is now
   asked of the human, and anything it could not read is recorded as `not measured`, never as zero.

   The keyboard height is derived from the card's own **content displacement** under
   `ADJUST_RESIZE`, not from its visible display frame. The frame reported the resize on some runs
   and not others on the same device, so it is kept in the output as `CONTROL` evidence and drives
   nothing. See `RUN.md`'s round-3 section for the full derivation.

Repeat for the other mode, and on any other device/OEM skin you want data from — each run appends
a new entry, so nothing already recorded is lost.

## Where the findings file lands

`/sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md`

(app-specific external storage — `Context.getExternalFilesDir(null)`). Pull it with:

```
adb pull /sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md
```

or use the in-app **Share findings file** button, which opens the system share sheet with the
file attached. Either way, commit the result verbatim — see `RUN.md` for the current destination
folder, and
`openspec/changes/archive/2026-08-13-slice-3-b-a-pet-you-can-talk-to/spike-findings/README.md`
for the process the earlier run followed.
