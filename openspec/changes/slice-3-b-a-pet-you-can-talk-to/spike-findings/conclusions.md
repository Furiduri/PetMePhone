# IME viability spike — conclusions

Raw measurements: `redmi-note-14-pro-hyperos3-api36.md` (seven runs, 2026-08-12).
Device: Xiaomi Redmi Note 14 Pro 5G (`24090RA29G`), HyperOS 3.0, Android 16, API 36.

This file separates what was measured from what it means. Nothing below is inferred from a
signal the instrument did not actually capture.

## Verdict: in-overlay text entry IS viable on this device

The keyboard appears, text can be typed, the app underneath keeps playing video, and focus
returns cleanly on dismissal. Issue #27 does not need re-scoping, and task creation stays in
the overlay rather than moving to the full-screen app in slice 7.

**This verdict rests on one OEM skin.** Issue #18 asks for two. See "What is still unproven".

## Measured, and trustworthy

| Signal | Result | Source |
|---|---|---|
| Window took focus | `true`, every run | `onWindowFocusChanged`, observed |
| Video paused when the window took focus | **No**, both modes, six answered runs | Human answer |
| Focus returned to the app underneath | `Yes` on every run of the corrected build | Human answer |
| Window removed cleanly, no leaked focusable state | `true`, every run | `removeView` outcome |
| IME inset listener attached and firing | `true`, every Full IME run | `setOnApplyWindowInsetsListener` |

The focus result is the decisive one. The card shell shipped non-focusable specifically because
the cost of taking window focus was unmeasured, and the back gesture was dropped for the same
reason. On this device that cost is zero: focus was genuinely taken and no video paused.

## Measured, and NOT trustworthy — read this before using the raw file

**`Keyboard appeared: false` is wrong.** The maintainer confirms the keyboard appeared and text
could be typed in every Full IME run.

The instrument sets `keyboardAppeared` from `WindowInsetsCompat.Type.ime()` inside the window's
own inset listener. That listener fired (`imeInsetCallbackFired: true`) but never once reported a
non-zero IME inset, so the flag stayed at its initial `false`.

That is not a null result. It is the finding issue #18 predicted in its own words: **IME insets
are not delivered to a raw `WindowManager`-added view.** The keyboard exists; the window is simply
never told about it.

**`Keyboard covers field: false` is not a measurement at all.** The instrument only computes it
inside `if (keyboardAppeared)`, which never became true, so the recorded value is the untouched
default. Do not cite it as evidence either way. What is known is the maintainer's direct
observation that the field remained usable — the window's `softInputMode` is `adjust=pan`
(confirmed in `dumpsys window windows`), and panning is the likely reason, but that mechanism was
not isolated by this spike.

## Consequences for the implementation

1. **`imePadding()` and `WindowInsets.ime` cannot be relied on** for the quick-menu card. They are
   the natural approach and they will silently do nothing here. Whatever keeps the field visible
   must not depend on the overlay window receiving IME insets.
2. **`softInputMode = adjust=pan` on the window's `LayoutParams` is the leading candidate**, since
   it was set during these runs and the field stayed usable. Verify it explicitly rather than
   inheriting it by accident.
3. **The back gesture can return to scope.** It was dropped only because a focusable card was
   assumed to cost the user their video. On this device it does not.
4. **The card may be created focusable** for the same reason.

## What is still unproven

- **Only one OEM skin was tested.** Issue #18 asks for at least two. Samsung One UI and Xiaomi
  MIUI/HyperOS are named there as the historically worst offenders; HyperOS is the one covered.
- **Whether the keyboard ever covers the field** — the signal that would have shown it never ran.
- **Whether `adjust=pan` is what keeps the field visible**, as opposed to something incidental to
  this device.
- The instrument's `keyboardAppeared` signal is known-bad and would mislead a second device run in
  the same way unless it is fixed first.
