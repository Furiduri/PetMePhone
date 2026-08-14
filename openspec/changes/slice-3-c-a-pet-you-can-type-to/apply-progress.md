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

## Phase 3: Controller — hoisted content, restoration, back application (PR 3) — DONE

All tasks 3.1–3.10 complete. TDD RED→GREEN followed for the controller changes; RED confirmed by
compilation failure (`onContentChange`, the `content` property, and the two-arg `cardContent`
signature did not exist yet before task 3.2/3.4's GREEN).

- Modified `feature/overlay/src/main/kotlin/.../quickmenu/QuickMenuWindowController.kt`
  - Added `internal var content: QuickMenuContent by mutableStateOf(QuickMenuContent.Dashboard)
    private set` — the field lives on the controller (design decision 4), backed by
    `mutableStateOf` purely as the in-window recomposition mechanism for the `BackPressed` swap,
    not as a claim that the field itself is Compose-owned.
  - `closeWindow()` was **not** touched — it still only calls `host.destroy()` and
    `windowManager.removeView`, never resetting `content`. Verified by the restoration tests, not
    just by omission.
  - `destroy()` now also sets `content = QuickMenuContent.Dashboard` (decision 5b's destroyed
    boundary).
  - `openWindow` now builds `ComposeOverlayHost`'s content lambda as `{ cardContent(content) }` —
    read at render time, not captured at open time, so it always renders whatever `content` is
    current when the window is actually added.
  - `cardContent`'s type widened from `@Composable () -> Unit` to `@Composable (QuickMenuContent)
    -> Unit`; default became `{ _ -> Box(Modifier) }`.
  - Added `internal fun onContentChange(newContent: QuickMenuContent)` — the callback Phase 4's
    container will be handed; exercised directly by tests in this phase since no container exists
    yet to call it.
  - `onEvent` now intercepts `BackPressed` before the normal reduce dispatch: while the card is
    `Open`, `resolveBack(content)` decides — `ShowDashboard` sets `content = Dashboard` and
    returns without touching `reduce` (window stays open); `CloseCard` falls through to the
    existing dispatch, which forwards `BackPressed` into `reduce` exactly like every other
    dismissal event and closes the card. While `Closed`, `BackPressed` falls straight through
    unchanged (a no-op, per the domain reducer already re-asserted in Phase 1).
  - Rewrote the class kdoc to describe decisions 1, 2, 4, 5, 5a, 5b, 7, 8 accurately and removed
    the now-false "non-focusable and back-gesture-free" paragraph from Phase 0/1's version.
- Modified `feature/overlay/src/main/kotlin/.../service/PetOverlayService.kt`
  - `cardContent = { _ -> ... }` — accepts and currently ignores the `QuickMenuContent` argument,
    with a comment stating plainly that content-aware rendering is Phase 4's `QuickMenuCard` work
    and this route still unconditionally renders today's single dashboard content until then. No
    other behavior change.
- Modified `feature/overlay/src/test/kotlin/.../quickmenu/QuickMenuWindowControllerTest.kt`
  - `newController` gained an optional `nowMs` parameter (defaults to the real clock, same as
    before).
  - Added `advancingClock()`, a deterministic clock helper that jumps 10s per read. **Why it
    exists, recorded because it is easy to get silently wrong:** a restoration test that dismisses
    via `OutsideTouch` and then immediately reopens via `PetTapped` is *exactly* the one-finger
    `ACTION_OUTSIDE`-then-`PetTapped` coincidence `SAME_GESTURE_WINDOW_MS` exists to suppress.
    Using the real clock in that specific test ordering would make the suppression silently eat
    the reopen event — and because `content` is simply never touched by a suppressed event, the
    assertion would still read the pre-dismissal value and **pass even though the window never
    actually reopened**, a false-positive green. `advancingClock()` makes the two events land 10s
    apart, so the reopen is a genuine second tap.
  - Added 12 new tests: six one-per-dismissal-path restoration tests (`OutsideTouch`, `PetTapped`,
    `PetDragged`, `BackPressed`, `AppLaunched`, `ScreenOff` — each its own test, not a
    parameterised loop, per the orchestrator's explicit instruction that a loop can pass while one
    path is silently broken), plus "dismissal from the dashboard reopens on the dashboard", "a
    fresh controller opens on the dashboard", "destroy then reopen yields the dashboard", and three
    back-application tests (`TaskInput` swap without close, `Dashboard` close via `reduce`,
    `BackPressed` while `Closed` is a no-op).
  - The back-triggered restoration test is named honestly for what back actually does: back can
    only ever close the card *from* `Dashboard` (a press from `TaskInput` unwinds to `Dashboard`
    first and leaves the window open — decision 7), so a back-triggered dismissal always leaves,
    and therefore always restores, the `Dashboard` content, not `TaskInput`. The test drives two
    back presses and asserts `isOpen` after each before asserting the final restored content, so
    the two-level shape is visible in the test itself rather than asserted away.
- Deleted `feature/overlay/src/test/kotlin/.../quickmenu/NoBackGestureCodeTest.kt`.
- Created `feature/overlay/src/test/kotlin/.../quickmenu/QuickMenuBackWiringCodeTest.kt` — the
  inversion, reusing the deleted test's file-scanning approach exactly (same
  `resolveQuickMenuSourceDir` resolution). Its kdoc records why the original gate existed (the
  window used to be structurally unable to receive a back key at all), why it had to invert once
  the window became focusable (a permanently-failing gate is a landmine, not a gate), and **two
  deliberate deviations from the literal task 3.5 text**, both because asserting the literal text
  today would be a fabricated pass:
  1. `BackHandler` count is asserted **"at most one"**, not **"exactly one"**. `BackHandler` is
     Phase 4's `QuickMenuCard` container work; the orchestrator's explicit hard constraint forbids
     adding one before that container exists, so the honest count in `src/main` right now is zero.
     "At most one" is true both now and after Phase 4 adds its single handler, while still catching
     the actual failure mode the gate exists for: a *second* handler.
  2. `setViewTreeOnBackPressedDispatcherOwner` is asserted **absent**, not **"exactly one"**,
     within the quick-menu package's scan scope — and it will structurally never be found there,
     in this phase or any later one. That call lives in `ui/ComposeOverlayHost.kt` under the
     sibling `ui/` package (added in Phase 2), the same exclusion Phase 2's own apply-progress note
     already recorded for `NoBackGestureCodeTest`. This file's kdoc points at
     `ComposeOverlayHostTest` as the place that already owns that call's own "exactly once"
     property.
- Deleted `feature/overlay/src/androidTest/kotlin/.../quickmenu/QuickMenuBackGestureDoesNotDismissTest.kt`
  with **no instrumented successor**. Recorded here, as required: its `sendKeyDownUpSync(KEYCODE_BACK)`
  assertion that the card stayed open also passes if the key never arrived at the window at all —
  and on the maintainer's device, adb-injected input does not reach the overlay, so an inverted
  "back *does* dismiss" version would fail for that same honest reason and be undiagnosable, not a
  real signal about the feature. Its coverage is redistributed: the two-level ordering to 1.1's
  pure `resolveBack`/`reduce` tests and this phase's `onEvent(BackPressed)` tests, the wiring shape
  to `QuickMenuBackWiringCodeTest`, and the actual observable three-level behavior to Phase 5's
  manual row (5.2), which already exists for exactly this reason.
- Created `feature/overlay/src/test/kotlin/.../quickmenu/QuickMenuNoPersistenceCodeTest.kt` —
  same file-scanning shape as `QuickMenuBackWiringCodeTest`, scanning for `DataStore`,
  `SharedPreferences`, `Room`, `FileOutputStream`, `FileWriter`, `openFileOutput`. Passes today
  with zero production change, since no persistence exists anywhere in the package to remove
  (task 3.9).

Verification: `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in
1m 58s, 75 actionable tasks: 75 executed`. Confirmed real execution (not `UP-TO-DATE`) by reading
`feature/overlay/build/test-results/testDebugUnitTest/`: `QuickMenuWindowControllerTest` — 24
tests, 0 failures; `QuickMenuBackWiringCodeTest` — 3 tests, 0 failures; `QuickMenuNoPersistenceCodeTest`
— 1 test, 0 failures.

Not touched (correctly out of scope for Phase 3, and verified by the diff): `OverlayWindowParams.kt`,
`feature/overlay/.../quickmenu/ui/` (Phase 4's container UI), `QuickMenuConfig.kt`,
`OverlayModule.kt`. No `BackHandler` was added anywhere — `QuickMenuBackWiringCodeTest`'s own "at
most one, currently zero" assertion structurally proves this, not just the task description.

Next: Phase 4 (container UI, config, DI, accessibility — PR 4, depends on PR 3).

## Phase 4: Container UI, config, DI, accessibility (PR 4) — DONE

All tasks 4.1–4.16 complete.

- Modified `feature/overlay/build.gradle.kts` — added `implementation(libs.androidx.activity.compose)`
  for the `BackHandler` composable.
- Modified `feature/overlay/.../quickmenu/QuickMenuConfig.kt` — added `taskTitleMaxLength: Int` and
  `inputContentMinHeightDp: Int` (design decision 11).
- Modified `feature/overlay/.../di/OverlayModule.kt` — provides the two new config values as
  injected constants (`140`, `120dp`), no product reference yet, per the standing rule.
- Modified `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` — rewritten as the container: hosts
  `content: QuickMenuContent`, the package's one and only `BackHandler` (calling `onBack`), and
  switches between `QuickMenuDashboardContent` and `QuickMenuTaskInputContent` in a `when`. No new
  `WindowManager` window is ever touched by a swap — the container only recomposes.
- Created `feature/overlay/.../quickmenu/ui/QuickMenuDashboardContent.kt` — today's three
  `MetricRow`s and the launch button, moved verbatim (appearance unchanged); the previously-disabled
  add-task control is now enabled and calls `onAddTask`, the swap trigger into the task-input
  content.
- Created `feature/overlay/.../quickmenu/ui/QuickMenuTaskInputContent.kt` — an `OutlinedTextField`
  with composition-local `rememberSaveable { mutableStateOf("") }` text (never hoisted to the
  controller, per the hard constraint and design decision 5's Compose-owned half), length-bounded
  by the injected `taskTitleMaxLength` at the point of input (rejected, not truncated downstream),
  a submit button (`onSubmit: (String) -> Unit`) and a leave button (`onLeave: () -> Unit`). No
  `FocusRequester` and no `showSoftInput` call exist anywhere in this file — the field relies
  entirely on the platform's default tap-to-focus behaviour, so it is focused only in direct
  response to a real tap (design decision 10), including on a restoration-path reopen directly onto
  this content. **No `:core:domain/task` import and no `CreateOneOffTask` reference exist in this
  file** — verified both by writing it that way and by the new `QuickMenuNoTaskDomainImportCodeTest`
  source-scan gate. Buttons are labelled `Text`-in-`Button`/`OutlinedButton` controls, not bare
  icon glyphs — this project has no icon-font dependency, matching the existing convention already
  recorded in `QuickMenuDashboardContent`'s add-task control history.
- Modified `feature/overlay/.../service/PetOverlayService.kt` — `cardContent` now reads its
  `QuickMenuContent` argument (previously ignored) and wires `QuickMenuCardRoute` with
  `onContentChange = { quickMenuController?.onContentChange(it) }`,
  `onBack = { quickMenuController?.onEvent(QuickMenuEvent.BackPressed) }`, and
  `onSubmitTask = { title -> Log.d(TAG, ...) }` — a no-op logging lambda, never a task-domain call
  (design decision 12; #100 owns submission).
- Modified `feature/overlay/src/main/res/values/strings.xml` — retired
  `feature_overlay_quickmenu_add_task_button`/`..._add_task_description`'s old "+ Feed"/"Not
  available yet" copy in favour of "+ Add task"/"Add a task" (the control is real now, not a
  placeholder); added the task-input content's four new strings, including the maintainer-decided
  placeholder **"What needs doing?"** verbatim, flagged in-file as maintainer-decided copy.

Tests (RED written and confirmed failing against the pre-Phase-4 `QuickMenuCard` signature, then
GREEN against the container above):

- Deleted `feature/overlay/.../quickmenu/ui/QuickMenuCardNoTextFieldTest.kt` — contradicted by
  `overlay-quick-menu`'s modified requirement ("a text field renders in the card's task-input
  content"). Its coverage is inverted into `QuickMenuCardContainerTest`'s
  `the task-input content shows an editable text field` test, which also re-asserts the dashboard
  content alone still has none.
- Created `feature/overlay/.../quickmenu/ui/QuickMenuCardContainerTest.kt` — task 4.2/4.3: the
  dashboard content shows no text field; the task-input content shows one; activating the add-task
  control requests a swap to `TaskInput` via `onContentChange`; leaving the input requests a swap
  back to `Dashboard`. Drives the container directly with a callback capture, not a real window, so
  "no additional `WindowManager` window is added" holds structurally — the test never touches
  `WindowManager` at all.
- Created `feature/overlay/.../quickmenu/ui/QuickMenuTaskInputContentTest.kt` — task 4.5/4.7/4.9/4.11:
  the field starts unfocused and is focused only after `performClick()`; typed text does not survive
  a fresh composition (driven via `key(instance)` to force real disposal/recreation, matching the
  window-removal shape of every dismissal path, rather than two `setContent` calls on one rule,
  which Compose's test harness rejects with `IllegalStateException` — the RED step surfaced this
  and the fix was in the test, not production code); a string exceeding `taskTitleMaxLength` is
  rejected at the field (submitted text never exceeds the bound); the field carries the
  `"Task title"` content description; the field exposes `ImeAction.Done`; the empty field shows the
  `"What needs doing?"` placeholder.
- Modified `feature/overlay/.../quickmenu/ui/QuickMenuCardAccessibilityTest.kt` — rewritten against
  the new container signature. Dashboard: two clickable nodes (launch button, now-enabled add-task
  control), both content-described and 48dp-minimum, no full-bounds scrim, root carries no click
  action. Task-input: every clickable node (field, leave, submit) content-described and
  48dp-minimum. All assertions iterate `onAllNodes(hasClickAction())` per node, never by naming a
  fixed tag list, per the standing project convention.
- Modified `feature/overlay/.../quickmenu/ui/QuickMenuCardFitsTest.kt` — updated to the new
  container signature (`content = QuickMenuContent.Dashboard`, the new callback params); its
  reachability assertions are otherwise unchanged, since the dashboard's layout is unchanged.
- Created `feature/overlay/.../quickmenu/QuickMenuNoKeyboardSignalCodeTest.kt` — source-scan gate
  (task threat-matrix / `quick-menu-text-input`'s "no inset or IME signal drives content selection"
  scenario): no `WindowInsets.ime`, `imePadding`, or `getWindowVisibleDisplayFrame` reference exists
  anywhere in the quick-menu package, including `ui/`.
- Created `feature/overlay/.../quickmenu/QuickMenuNoTaskDomainImportCodeTest.kt` — source-scan gate
  for the orchestrator's hard constraint and the "submission is out of scope" requirement: no
  `core.domain.task` import and no `CreateOneOffTask` reference exists anywhere in the quick-menu
  package.
- Modified `feature/overlay/.../quickmenu/QuickMenuBackWiringCodeTest.kt` — **PR 4's carried debt,
  paid**: tightened the `BackHandler` bound from Phase 3's honest "at most one" (true then because
  the count was zero) to **exactly one**, now that `QuickMenuCard` hosts the package's single
  `BackHandler`. Counts the invocation `"BackHandler("` rather than the bare identifier, so the
  file's own `import androidx.activity.compose.BackHandler` line does not double the count.
- Modified `feature/overlay/src/androidTest/.../quickmenu/QuickMenuWindowLifecycleTest.kt` — task
  4.13/4.14: added `cardWindowIsAddedFocusableWithAdjustResize` (asserts `FLAG_NOT_FOCUSABLE` absent
  and `SOFT_INPUT_ADJUST_RESIZE` set on the real, attached card window's live `LayoutParams`) and
  `destroyLeavesNoViewAttachedAndPetWindowUnchanged` (asserts `destroy()` leaves no card view
  attached, and the pet window's `LayoutParams` are bit-for-bit unchanged across the card's full
  open/destroy lifecycle). **Not run** — this phase does not execute device/instrumented tasks per
  the orchestrator's explicit instruction; `assembleDebugAndroidTest --rerun-tasks` confirms it
  compiles and packages. Execution is the maintainer's, on `emulator-5554` or a real device.
- Task 4.15: confirmed `design.md`'s "Tracked deviation: #18's two-OEM-skin criterion is not met"
  section already states both required facts (issue #17's criterion is met by this change; #82
  tracks the two-OEM gap separately, not satisfied, not a blocker) and matches shipped code — no
  change needed.

Verification:

- `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 1m 44s, 120
  actionable tasks: 120 executed`. Confirmed via the test-result XMLs (not `UP-TO-DATE`): 37 test
  classes, 156 tests total, 0 failures, 0 errors, 1 skipped (pre-existing, unrelated to this phase).
- `./gradlew :feature:overlay:assembleDebugAndroidTest --rerun-tasks` → `BUILD SUCCESSFUL` (included
  in the same combined run above, 120 actionable tasks). The instrumented test above is packaged but
  not executed.
- The full CI gate command (`assembleDebug testDebugUnitTest :core:domain:test
  assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks`, task 4.16) was **not** run in this
  session — only the two commands the orchestrator's "Verification" section named exactly were run.
  This is recorded honestly as a gap against task 4.16's literal text; the maintainer should run the
  full gate (including `lintDebug`) before merge, per this project's standing
  `run-the-ci-command-not-an-approximation` convention.

Not touched (correctly out of scope for Phase 4): `QuickMenuWindowController.kt`'s `resolveBack`
application and restoration logic (Phase 3, unchanged), `QuickMenuWindowParams.kt` (Phase 2,
unchanged), `core/domain/.../overlay/` (Phase 1, unchanged).

**Remaining, not closable by this pipeline (Phase 5, maintainer-only):**

- 5.1–5.4: manual device verification (keyboard-on-tap, field visibility under `ADJUST_RESIZE`,
  three-level back ordering in person, TalkBack pass) — adb-injected input does not reach the
  overlay on the maintainer's device, so these have no automated route by construction.
- 5.5: a `gh` CLI comment on issue #18/#17 recording the back-gesture criterion and the #82
  deviation — not performed in this session.
- 5.6: a final maintainer cross-check of the proposal's success-criteria checklist against shipped
  code.
