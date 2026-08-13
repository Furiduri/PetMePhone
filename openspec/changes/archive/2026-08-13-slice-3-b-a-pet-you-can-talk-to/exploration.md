# Exploration: Slice 3 (part B) — the overlay half

Scope decided by the maintainer: **#17 in full**, **#18's spike as a runnable deliverable only**,
**#27 explicitly not started** (conditional on the spike outcome).

## The `onTap` seam is already merged

`PetTouchController.onTouch` separates tap from drag by slop and calls
`onTap.onTap(OverlayAnchor(xPx, yPx, sizePx))`. `OverlayTapListener` is a `fun interface`.
`PetOverlayService.onPetTapped(anchor)` (`PetOverlayService.kt:256`) is a `Log.d` stub whose own
comment names slice 3's quick menu as its consumer. #17's integration criterion is achievable with
**zero** changes to the drag/touch layer, and `OverlayAnchor` already carries exactly the
pet-position input the positioning math needs.

## #17's metrics criterion is wrong on two counts

`docs/build-order.md` already corrects Happiness and Energy: no producer exists, so they render as
loading/absent, never zero. Verification found a second error: **Hunger has no Flow either.** Part A
shipped `calculateHunger` / `isHungry` / `isHungerPriority` as pure functions taking counts plus
`BalanceConfig`. A repo-wide search for consumers of `calculateHunger` outside `:core:domain` hits
only archived markdown — no production code. The card's Hunger readout is therefore **new
plumbing** (`TaskRepository` counts + `AppClock` "today" + `BalanceConfig` composed into a `Flow`),
not reuse of an existing flow. This correction is owed back to #17.

## Insets: real prior art, real gap

`PetOverlayService.usableBoundsPx()` uses `WindowMetrics` with
`getInsetsIgnoringVisibility(systemBars() or displayCutout())` on API 30+, falling back to raw
bounds below. There is **no `androidx.window` dependency** and no compat path for API 26–29, which
#17 explicitly requires. `ProjectConfig.minSdk = 26`. New dependency plus new code — unscoped, the
card silently ships correct on API 30+ only. Standing project rule: edge-to-edge insets are not
optional.

## Architecture: extract a `QuickMenuWindowController`

Mirrors the existing extraction pattern of `PetTouchController` and `OverlayWindowParams`.
`PetOverlayService` is already 365 lines and owns the pet window; the card is a second independent
`WindowManager` window (#17 pins this: two windows, never toggling `FLAG_NOT_FOCUSABLE` on the
pet's window). Decisive reason: if the spike kills in-overlay text entry, one class is deleted or
stubbed instead of a scattered service rewrite. `OverlayWindowParams` is pet-window-only today and
its own comment says `FLAG_WATCH_OUTSIDE_TOUCH` belongs to the quick-menu window.

## Absence-vs-loading needs a decided home

`PetOverlayStateHolder` exposes `sheets`, `petState`, `screenOn`, `feedback` — no metric field at
all. `CharacterSheets.Loading` is the closest existing loading-state precedent. A per-metric sealed
type (loading / available / unavailable) consumed by the Compose layer, never collapsing to `0`
anywhere in the pipeline, is the natural shape. Decide it in design; do not improvise it in Compose.

## Coroutine scope precedent exists

`OverlayApplicationScope` is a DI qualifier already injecting an app/service-scoped `CoroutineScope`
into `PetOverlayStateHolder`. Nothing new needs inventing when #27 eventually lands.

## The #70 resolver widening is not absorbed here

`PetStateProvider.evaluate` returns one `PetState?`, but the card's metrics read flows straight onto
the state holder and bypass `PetStateResolver` entirely — the pet's animation state and the card's
metrics are already separate paths. The widening belongs to #38/#70, as `Hunger.kt`'s own doc
comments already say.

## Verification reality

An emulator is available (`emulator-5554`, `sdk_gphone64_x86_64`), so instrumented tests,
overlay-permission granting via adb, `ACTION_OUTSIDE` behaviour and back-gesture wiring **can** be
exercised in this pipeline.

Cannot be closed here, and belongs to the maintainer on physical hardware: OEM-skin variance (the
whole point of the spike), IME behaviour on `TYPE_APPLICATION_OVERLAY`, the video-pause
measurement, and the manual TalkBack pass. adb-injected input is known not to reach the overlay on
the maintainer's HyperOS device. Device-only acceptance criteria must not be marked satisfiable by
this pipeline.

## Other constraints in force

- Absence never renders as zero; balance values are injected config, never literals;
  Clean/Hexagonal architecture; edge-to-edge insets are not optional.
- Positioning math is pure (anchor + bounds + insets in, offset out) → `:core:domain`, unit tested
  across all four corner anchors with no device, following the `nearestEdge` /
  `OverlayPositionFraction` precedent.
- #17's accessibility minimums are acceptance criteria, not follow-ups: content descriptions or
  semantic roles on every interactive element, 48dp touch targets, no undescribed full-bounds
  touchable scrim.
- `ACTION_OUTSIDE` is best-effort and yields no usable coordinates; the documented fallback (dismiss
  on pet tap or drag) is required.
- `BackHandler` alone does nothing in a `WindowManager`-hosted `ComposeView` — there is no
  `OnBackPressedDispatcher`. Explicit dispatcher wiring or `KEYCODE_BACK` interception is required,
  and key events need a focusable window, which ties back to the spike.
