# Running the keyboard-visibility comparison

> This file records **three** measurement rounds. Round 3 is below, first, because it is the one to
> run now. The round-2 and round-1 sections that follow it are kept unchanged so the committed
> output of each round stays interpretable against the procedure that produced it.

---

# Round 3 — measure the card's content, not its frame, and rotate

## What round 2 refuted

Two `Two windows: card resizes, pet follows` runs on the same Redmi Note 14 Pro (HyperOS 3, API 36),
minutes apart, in the same mode:

- **10:25:40** — `visibleDisplayFrame` stayed `[0,130,1220,2660]` across all eleven samples. It never
  shrank. But `fieldBoundsOnScreen` moved from top `2510` to top `1577` at +3991ms. The keyboard was
  up and the resize did happen; the frame simply did not report it. The instrument honestly recorded
  `no reduction ever observed` and honestly did not move the pet — it was measuring the wrong thing.
- **10:26:58** — the frame *did* shrink, same mode, same device, same orientation.

**`getWindowVisibleDisplayFrame` is not a deterministic keyboard-height source on this window
class.** Round 2's hypothesis — that the card can measure the keyboard through its own visible frame
— is refuted, and nothing in the instrument branches on that frame any more.

Three further defects, each with measured evidence in the same two runs:

| Defect | Evidence |
| --- | --- |
| A single sample could move the pet. | At +9040ms one sample read the frame as `[0,130,1220,914]` — a 1746px "reduction", nearly double a keyboard — and the pet was moved to y=586 on that one reading. It is the only sample in the whole file with a non-zero control `ime()` inset (813), i.e. a transient. |
| The sample cap truncated *behaviour*, not just the record. | Run 2 recorded `Layout-driven sampling hit its cap: true`. The follow logic hung off the same call the cap short-circuited, so once the cap was hit the pet stopped being measured, moved or restored — it ended the run stranded. |
| The baseline never reset. | It was the largest frame bottom seen so far, so after a rotation a portrait baseline was compared against landscape geometry and the "reduction" became an orientation delta. The maintainer saw exactly this: the pet moves on rotation, and after rotating back it stops responding to the keyboard at all. |

## The new derivation

The keyboard displacement is now **how far the card's own content moved on screen** under
`ADJUST_RESIZE`:

```
displacement = baselineContentTop - currentContentTop
```

- `contentTop` is the `getLocationOnScreen`-based top of the **text field**, or of the **root view**
  in a mode that has no field. Which view it came from is printed in every raw sample row, so two
  rows read off different views are never silently compared.
- The **baseline** is the largest (lowest on screen) content top observed since the last reset —
  i.e. the resting position with the keyboard down. Content returning to rest re-establishes it.
- This is the signal that was reproducible where the frame was not: `fieldBoundsOnScreen` moved by
  exactly **933px in both** round-2 runs (top `2510` → `1577`).

`visibleDisplayFrame` is still recorded in every raw sample row. It is now **evidence about an
unreliable signal**, labelled `CONTROL`, and nothing acts on it.

## The debounce rule

**No move on a single reading.** A displacement must be reported by **two consecutive samples with
the same value** before the pet is moved.

This is a debounce on *acting*, not a plausibility filter. There is deliberately **no maximum
height**, **no rejection of a value for being too big**, and **no clamping** — an unusual
displacement seen twice in a row is a measurement and is acted on.

Every observation is written into the findings, including the ones that never agreed and therefore
never caused a move:

```
- Content displacement observations (EVERY observation, including ones that never agreed ...):
  | +ms | displacement px | agreed with previous | caused a pet move |
```

An outlier stays visible as data rather than being silently dropped.

## The baseline reset, and orientation

Two independent reset paths, both recorded with an elapsed time and a cause under
`Displacement baseline resets`:

1. `SpikeOverlayService.onConfigurationChanged` — cause `service onConfigurationChanged`.
2. A change in the **measured window bounds' orientation** between two samples — cause
   `window bounds orientation changed from portrait to landscape` (or the reverse).

Every raw sample row now carries an `orientation` column, so a mixed-orientation run is readable
after the fact. A run whose bounds could not be read shows `not measured` there — never a defaulted
`portrait`.

## The cap is separated from the behaviour

`Layout-driven sampling hit its cap` and `Displacement observation recording hit its cap` bound only
what is **written down**. Measuring, debouncing, moving and restoring all keep running after either
cap is reached. This is the exact defect that left the pet stranded in run 2.

## Rotation is now part of what to exercise

Round 2 said portrait only. **Round 3 explicitly asks you to rotate.** Run the
`Two windows: card resizes, pet follows` mode and, in one run:

1. Open the keyboard in portrait. Watch the orange block move up and back.
2. **Rotate to landscape mid-run.**
3. **Rotate back to portrait.**
4. Open and close the keyboard **again**, and watch whether the pet still follows.

Step 4 is the one that failed before. Landscape is still not a *strategy* under test — the IME goes
fullscreen there with its own extracted field — but the rotation itself is now under test, because
the baseline's behaviour across a configuration change is what round 2 got wrong.

Answer the pet questions from what you saw in the **portrait** phases; note in the findings if the
landscape phase behaved differently.

---

# Round 2 — does the card work as a measuring instrument for the pet?

## Portrait only

**Run every round-2 run in portrait, and do not rotate the device mid-run.** Landscape is settled
and out of scope: there the IME goes fullscreen with its own extracted text field, so the overlay's
own field is not the one being typed into and there is nothing left to keep visible. A landscape run
would produce a clean-looking result about a problem that does not exist.

## What round 1 established, so this round does not re-derive it

Measured on a Redmi Note 14 Pro, HyperOS 3, API 36:

- `SOFT_INPUT_ADJUST_PAN` did **not** help — it matched the no-strategy control exactly: field
  partially covered, placement judged unacceptable.
- `SOFT_INPUT_ADJUST_RESIZE` and anchor-to-top both produced a fully visible, acceptable field.
- The visible display frame changed in **exactly one run out of five**: the portrait Resize run,
  from `[0,130,1220,2660]` to `[0,130,1220,1727]`. That 933px reduction is consistent with a
  keyboard height.
- `dumpsys window windows` showed `imeLayeringTarget`, `imeInputTarget` and `imeControlTarget` all
  pointing at the spike's own window. **`softInputMode` is a property of the IME target window
  only** — a window that is not the IME target is unaffected by any value it carries.

## The hypothesis under test

The real overlay has two windows: a pet window (small, non-focusable, never the IME target) and a
quick-menu card window. Once the card is focusable it becomes the IME target, so `ADJUST_RESIZE`
applies to it — but the pet can never be the IME target and will therefore never be moved by the
keyboard, leaving it stranded behind it.

The proposed design makes the card the **measuring instrument** for the pet: the card reads its own
reduced visible display frame, the reduction is the keyboard height, and the service moves the pet
up by exactly that measured amount, restoring it when the keyboard goes away.

Two things are unproven, and this round measures both:

1. **When the resize actually lands.** In round 1 the reduction appeared only in the
   `after dismissal` sample at +13609ms, never in the `after showSoftInput returned` sample at
   +938ms. Either the resize takes longer than 938ms to propagate, or that sample was taken too
   early — round 1 cannot tell which.
2. **Whether the derived height is stable enough** to drive another window's position.

## What is new in the instrument

- **A late sample** at `after late settle window` (+2500ms), so a slow resize is caught. The delay
  is a measurement window, not a tuned value; nothing in production should adopt it as a timing.
- **Layout-driven samples** (`on layout change`), taken from a
  `ViewTreeObserver.OnGlobalLayoutListener` on the overlay root, so the moment the frame actually
  changes is recorded rather than guessed at. They are capped at 20 per run, and hitting the cap is
  written into the findings as `Layout-driven sampling hit its cap: true` — a truncated series must
  never be read as a series that simply stopped changing.
- **`Two windows: card resizes, pet follows`**, a new mode. Every round-1 sample point and mode is
  kept, so the round-1 output stays comparable.

## The new mode, and what to watch

The mode adds two windows at once:

| Window | Shape |
| --- | --- |
| Card | Focusable, `SOFT_INPUT_ADJUST_RESIZE`, holds the text field, placed LOW at the same 72% used by every other mode. |
| Pet | A 305x305 px solid **orange** block, `FLAG_NOT_FOCUSABLE`, **no** `softInputMode` at all, placed at 86% of the window height — deep inside the band the keyboard occupies, because a pet that starts above the keyboard is never stranded and would prove nothing. |

While the run is up, watch the orange block:

- Does it move up above the keyboard at all?
- Does it move smoothly, or jump and lag?
- Does it go back to where it started when the keyboard closes?

The dialog asks exactly those three questions after Finish, and they join the six from round 1 —
all nine must be answered before **Save answers** enables.

## How the height is derived, and what happens when it cannot be

The reduction is `baseline frame bottom − current frame bottom`, read off the card's own
`getWindowVisibleDisplayFrame` at every sample. The baseline is the largest frame bottom observed so
far, so the frame returning to full size re-establishes it.

**Nothing is derived that was not measured.** If no reduction is ever observed, the pet does not
move at all, and the findings say so explicitly. The three "no height" outcomes are separate values
in the file and mean different things:

| Findings line | Meaning |
| --- | --- |
| `not measured (the card's visible display frame could not be read at any sample)` | No reading was available. Not a zero. |
| `no reduction ever observed (...)` | Readings were available throughout and never shrank. A real measurement, and the pet was correctly left alone. |
| `keyboard height derived from a measured frame reduction of up to N px` | A height was measured; the `Pet moves` table lists every move and the reduction that caused it. |

If the pet moved, the file also records its original y, every y it was moved to, and whether it was
restored. `Pet restored to its original y` renders as `not measured` when the pet never moved —
there was nothing to restore, which is not the same as a failed restoration.

Run the new mode alongside `Resize` for comparison; the other three round-1 modes need no second
portrait pass unless something contradicts round 1.

---

# Round 1 — the keyboard-visibility comparison

This is the manual procedure for the second measurement this module exists for: comparing the
keyboard-visibility strategies for issue #18 on one real device. It is a per-device, per-strategy
run driven by a human watching the screen; there is no automated path, and there will not be one
(adb-injected input does not reach an overlay window on every OEM skin, and "did the keyboard cover
the field" is a visual judgement).

For the module's background and the earlier focus-cost measurement, see `README.md` next to this
file.

## What changed since the previous run, and why it matters

The previous instrument derived `keyboardAppeared` from `WindowInsetsCompat.Type.ime()`. A
`TYPE_APPLICATION_OVERLAY` window is never told about the IME, so the flag recorded `false` on all
seven runs while the maintainer was typing into the field, and `keyboardCoversField` was only ever
computed inside `if (keyboardAppeared)` — its recorded value was an untouched default, not a
measurement.

Both derived signals are gone. In their place:

- **Raw geometry samples.** At four moments the run records the root view's
  `getWindowVisibleDisplayFrame`, the `WindowMetrics` bounds, the field's on-screen bounds, and the
  `LayoutParams.y` actually in effect. Every value is written verbatim; a value that could not be
  read is written as `not measured`, never as `0`.
- **One derived outcome, and only one.** The visible display frame before focus is compared against
  the frame with the keyboard up. If they are identical, the findings say *no keyboard geometry
  signal available on this window class* — a real, first-class result, distinct from a `false` and
  from a zero.
- **The `ime()` inset is kept as a labelled CONTROL only,** to confirm the known-bad behaviour
  reproduces on this device. It gates nothing.
- **Human answers are authoritative** for everything the window cannot observe.

## Before you start

1. Install the module. Do not install it from a release variant — it has none that matters, and it
   is deliberately outside `:app`'s dependency graph.

   ```
   ./gradlew :spike:ime-viability:installDebug
   ```

   If more than one device is attached, pin the serial first (`ANDROID_SERIAL=<serial>`), because
   Gradle device tasks reach every connected device.

2. Grant the overlay permission. On most OEM skins this cannot be granted over adb — open the app
   and use its own **Grant overlay permission** button, which opens the right Settings screen. On a
   device that does allow the appops route:

   ```
   adb shell appops set com.petmephone.spike.imeviability SYSTEM_ALERT_WINDOW allow
   ```

3. Have an app playing a video ready to switch to, so the focus-cost questions can be answered in
   the same runs rather than needing a second pass.

## The modes to run

Run **all five**, in this order. The comparison is only meaningful against its own control.

| Mode | What the window does |
| --- | --- |
| `Focus-only` | Focusable window, no field, no keyboard. Unchanged from the previous run; kept so the older findings stay comparable. |
| `Full IME (no strategy control)` | Real field, keyboard raised, **no** `softInputMode` and **no** repositioning. This is the baseline. |
| `Pan` | Same, plus `LayoutParams.softInputMode = SOFT_INPUT_ADJUST_PAN`. |
| `Resize` | Same, plus `LayoutParams.softInputMode = SOFT_INPUT_ADJUST_RESIZE`. |
| `Anchor top on focus` | No `softInputMode` at all. On field focus the window is moved to the top of the usable bounds with `updateViewLayout`, and moved back when focus is lost. |

## The low-position requirement

**The card must start low on screen, and it does — do not change this.** The window is added at
72% of the measured window height (`SpikeWindowParams.LOW_START_FRACTION`), and the absolute `y`
used is recorded in every findings entry.

This is the whole point. Pan, resize and anchor-to-top are all strategies for rescuing a field the
keyboard would otherwise cover. A field that starts near the top of the screen is never covered in
the first place, so a run from there produces a clean-looking result that proves nothing. If a run
shows the card near the top before the keyboard appears, that run is invalid — check the recorded
start `y` against the recorded window bounds before trusting anything else in it.

## One run, step by step

1. Open the app. The heading is visible below the status bar; if it is not, the run is on a build
   without the edge-to-edge fix and should not be trusted for anything visual.
2. Pick the mode. The selected mode is the filled button; every other mode is outlined. The line
   below the selector restates the `softInputMode` and whether the mode repositions, so a
   mis-tapped mode is visible before Start.
3. Tap **Start**. The window appears after about three seconds — use that time to switch to the
   video app if you are answering the focus-cost questions in this run.
4. Watch, and note as you go:
   - Did a keyboard appear at all?
   - Was the field fully visible, partially covered, or fully covered while you typed?
   - Did the card visibly jump or move when the field took focus?
   - Was the resulting placement actually usable?
   - Did the video pause, and did focus return to it afterwards?
5. Return to the spike app and tap **Finish**. The overlay window is removed at that moment, before
   the question dialog opens — an overlay sits above the activity by definition, so a run left
   running would cover the questions and they could not be answered at all.
6. Answer every question in the dialog. Round 1 asked six; the current build asks nine, the three
   added ones being round 2's pet questions — answer them `Not tested` in a mode with no pet.
   **Save answers** stays disabled until every question is answered —
   a partially answered run would write defaults into the findings file, which is the exact failure
   that invalidated the previous instrument's coverage result.
7. Repeat for the next mode. Entries are appended, never overwritten, so nothing already recorded
   is lost.

## Exporting the findings

The file lives in app-specific external storage:

```
/sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md
```

Pull it:

```
adb pull /sdcard/Android/data/com.petmephone.spike.imeviability/files/ime-viability-findings.md
```

or use the in-app **Share findings file** button, which opens the system share sheet with the file
attached.

## Where the exported file is committed

Commit it **verbatim**, exactly as the app produced it — never hand-transcribed, never tidied, never
partially quoted — into:

```
openspec/changes/slice-3-c-a-pet-you-can-type-to/spike-findings/<manufacturer>-<model>.md
```

One file per device; a device tested twice keeps both sessions in the same file, because the app
appends. This mirrors the archived process in
`openspec/changes/archive/2026-08-13-slice-3-b-a-pet-you-can-talk-to/spike-findings/README.md`,
whose `conclusions.md` is the model for separating what was measured from what it means: write the
interpretation in a separate file, and leave the device output untouched.

## Reading the result honestly

- `no keyboard geometry signal available on this window class` is the expected outcome, and it is a
  finding, not a failure. It means the window is never told about the keyboard — so the *human*
  answers, not the geometry, decide which strategy kept the field visible.
- If the geometry signal instead reports a frame change, that is new information about this OEM and
  should be called out explicitly, because it contradicts the HyperOS result.
- The `CONTROL` line exists to show the known-bad `ime()` behaviour reproducing. Do not promote it
  into evidence for anything else.
- A `not measured` in any raw sample column means the reading could not be taken. It does not mean
  the value was zero, and it must not be summarised as one.
