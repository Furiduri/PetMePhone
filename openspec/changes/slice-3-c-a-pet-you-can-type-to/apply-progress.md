# Apply progress: slice-3-c-a-pet-you-can-type-to

## Phase 1: Pure domain — content and back-outcome (PR 1) — DONE

All tasks 1.1–1.6 complete. TDD RED→GREEN followed throughout.

- Created `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuContent.kt`
  - `sealed interface QuickMenuContent { Dashboard, TaskInput }`
  - `sealed interface BackOutcome { ShowDashboard, CloseCard }`
  - `fun resolveBack(content: QuickMenuContent): BackOutcome` — total, two-case
  - No `android.*` import.
- Modified `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuState.kt`
  - Added `QuickMenuEvent.BackPressed` as an additive case (not a reuse of `OutsideTouch`, per
    design decision 9 — `SAME_GESTURE_WINDOW_MS` suppression rationale).
  - Wired `BackPressed` through `reduce` for both `Closed` and `Open` branches, preserving the
    "every event from `Open` yields `Closed`" property.
- Created `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuContentTest.kt`
  - `ResolveBackTest`: `TaskInput -> ShowDashboard`, `Dashboard -> CloseCard`.
- Modified `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuStateTest.kt`
  - Added `QuickMenuEvent.BackPressed` to `ALL_EVENTS` and `coverageOf` (exhaustive `when` guard
    already present in the file caught the addition at compile time, as designed).
  - Bumped the `ALL_EVENTS contains every QuickMenuEvent subtype` expected count from 5 to 6.
  - Added `reduce(Closed, BackPressed) is a no-op - closed stays closed`.
  - The existing `every event from Open yields Closed` test now iterates `BackPressed` too, with no
    special-casing.

RED confirmed first: `./gradlew :core:domain:test --rerun-tasks` failed compilation with
`Unresolved reference` errors for `resolveBack`, `QuickMenuContent`, `BackOutcome`, `BackPressed` —
the right reason. Implemented GREEN, re-ran, `BUILD SUCCESSFUL`.

Not touched (correctly out of scope for Phase 1): `feature/overlay/**`, `QuickMenuState.Open`
signature (still `Open(anchor)` only, per design decision 4).

## Phase 2: Window becomes focusable + IME target (PR 2) — DONE

All tasks 2.1–2.5 complete.

- Modified `feature/overlay/src/test/kotlin/.../service/QuickMenuWindowParamsTest.kt`
  - Renamed/rewrote the flags test to assert `FLAG_NOT_FOCUSABLE` is **absent**, `softInputMode ==
    SOFT_INPUT_ADJUST_RESIZE`, and `FLAG_NOT_TOUCH_MODAL`/`FLAG_WATCH_OUTSIDE_TOUCH` are still set.
  - Rewrote the `OverlayWindowParams` test into a full field-by-field assertion (type, format,
    width, height, x, y, gravity, exact flags bitmask) — the closest available proxy for
    "byte-identical" given `WindowManager.LayoutParams` has no `equals()` override.
  - RED confirmed first: ran `./gradlew :feature:overlay:testDebugUnitTest --tests
    "...QuickMenuWindowParamsTest" --rerun-tasks`; failed at the new `FLAG_NOT_FOCUSABLE` assertion
    (line 42), the right reason. Then implemented GREEN and re-ran to `BUILD SUCCESSFUL`.
- Modified `feature/overlay/src/main/kotlin/.../service/QuickMenuWindowParams.kt`
  - Dropped `FLAG_NOT_FOCUSABLE` from the flags passed to `WindowManager.LayoutParams(...)`,
    keeping `FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH`.
  - Added `softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE` in the same
    `apply {}` block as the rest of `create(...)`'s construction — same task, same edit, per
    design decision 1's indivisibility and the orchestrator's hard constraint.
  - Rewrote the class kdoc to record measured facts 1–2 from design.md and state plainly that this
    reverses the file's prior "non-focusable" framing, plus decision 2's fixed-at-construction
    rule and the never-touch-`OverlayWindowParams.kt` constraint.
  - `OverlayWindowParams.kt` was **not** touched — verified by the field-by-field test above.
- Modified `feature/overlay/src/test/kotlin/.../quickmenu/QuickMenuWindowControllerTest.kt`
  - Added `focusability is fixed at construction - updateViewLayout is never called across a full
    open-close-reopen cycle`: drives `PetTapped → OutsideTouch → PetTapped → destroy()` against a
    relaxed mockk `WindowManager` and asserts `updateViewLayout` is called zero times.
  - This test was **already green** against the pre-existing controller — no production code in
    `QuickMenuWindowController` calls `updateViewLayout` today, so there was no RED step to observe
    here. Recorded honestly rather than reporting a fabricated RED: the test still pins design
    decision 2 going forward (a regression that adds an `updateViewLayout` toggle would be caught
    immediately), it just didn't drive new production code in this task.
- Modified `feature/overlay/src/main/kotlin/.../ui/ComposeOverlayHost.kt`
  - `ComposeOverlayHost` now implements `OnBackPressedDispatcherOwner`, holds its own
    `OnBackPressedDispatcher()`, and calls `setViewTreeOnBackPressedDispatcherOwner(this)` in
    `init {}` alongside the existing lifecycle/saved-state owner wiring.
  - Extended the class kdoc with a paragraph explaining the same structural gap (no ancestor
    Activity) applies to `OnBackPressedDispatcherOwner`, and that no `BackHandler` exists yet —
    that lands with the container in Phase 4.
- Modified `feature/overlay/src/test/kotlin/.../ui/ComposeOverlayHostTest.kt`
  - Added `an OnBackPressedDispatcherOwner is resolvable from the host's own view tree`, attaching
    the host through `WindowManager.addView` (same pattern as the existing lifecycle/saved-state
    tests) and asserting `host.findViewTreeOnBackPressedDispatcherOwner() != null`.
- Modified `feature/overlay/build.gradle.kts`
  - Added `implementation(libs.androidx.activity)` (the pre-existing `activity-ktx` alias, already
    used by `:app`) — `feature:overlay` had no androidx.activity dependency before this phase, and
    `setViewTreeOnBackPressedDispatcherOwner`/`OnBackPressedDispatcher`/`findViewTreeOnBackPressedDispatcherOwner`
    all come from it.
- `NoBackGestureCodeTest` collision check (per the orchestrator's explicit hard constraint): **no
  collision.** That test scans only
  `feature/overlay/src/main/kotlin/com/gcatcode/petmephone/feature/overlay/quickmenu/` for
  `setViewTreeOnBackPressedDispatcherOwner` (and three other back-related strings).
  `ComposeOverlayHost.kt` lives in the sibling `ui/` package, not `quickmenu/`, so it is outside
  that test's scanned directory and the test still passes unmodified. Verified by running the full
  suite below, not just by reading the scan path.

Verification: `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in
1m 59s, 75 actionable tasks: 75 executed`. All tests, including `NoBackGestureCodeTest` and the two
new/modified test classes above, passed.

Not touched (correctly out of scope for Phase 2): `QuickMenuWindowController.kt`'s content field,
`resolveBack` wiring, `QuickMenuCard.kt`, `QuickMenuConfig.kt`, `OverlayModule.kt` — all Phase
3/4 work.

Next: Phase 3 (controller — hoisted content, restoration, back application, PR 3, depends on PR 2).
