# Tasks: Slice 3 part C — A pet you can type to (#18)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines (code only, docs excluded) | ~950–1050 across 4 PRs, each PR 150–380 |
| 800-line budget risk | Low per PR (150–380 each), but the change as a whole is ~950–1050 and therefore EXCEEDS the 800-line budget. Chaining is required by the budget, not merely convenient. |
| Chained PRs recommended | Yes — required. The total exceeds the budget, and the natural seams (domain, window params, controller, UI) each stand on their own merit. |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 |
| Delivery strategy | auto-chain |
| Chain strategy | `stacked-to-main` — each PR targets the previous PR's branch, the last targets `master`. Collected from the maintainer 2026-08-13; same shape as slice 3-B's six-PR chain. |

Decision needed before apply: Yes — the chain strategy. The change exceeds the 800-line review
budget, so chaining is not optional and the chain shape must be settled before apply begins.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Pure domain: `QuickMenuContent`, `resolveBack`, `QuickMenuState` additive `BackPressed` case, all pure tests | PR 1 | `./gradlew :core:domain:test --rerun-tasks` | N/A — pure JVM | Delete `QuickMenuContent.kt`, revert `QuickMenuState.kt` |
| 2 | Focusable window + `ADJUST_RESIZE` (decision 1, indivisible) and back-dispatcher wiring on `ComposeOverlayHost` | PR 2 | `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` | N/A — Robolectric | Revert `QuickMenuWindowParams.kt` flag/softInputMode change and `ComposeOverlayHost.kt`'s dispatcher-owner wiring |
| 3 | Controller: hoisted content field, `resolveBack` application, restoration-on-reopen logic; inverted `NoBackGestureCodeTest` → `QuickMenuBackWiringCodeTest`; delete `QuickMenuBackGestureDoesNotDismissTest` | PR 3 | `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` | Robolectric + shadow `WindowManager` | Revert `QuickMenuWindowController.kt`, restore both retired tests from history |
| 4 | Container UI: `QuickMenuCard` container + `QuickMenuDashboardContent` + `QuickMenuTaskInputContent`, config, DI, semantics/accessibility tests, instrumented back-ordering assertions | PR 4 | `./gradlew :feature:overlay:testDebugUnitTest assembleDebugAndroidTest --rerun-tasks` | emulator-5554, semantics tree + touch target + instrumented `WindowManager` tests | Delete/revert the three UI files, `QuickMenuConfig.kt`, `OverlayModule.kt` diff |

**Constraint honored:** decision 1 (focus + `ADJUST_RESIZE`) is entirely inside PR 2's single unit;
no task splits the flag change from the `softInputMode` change.

## Phase 1: Pure domain — content and back-outcome (PR 1)

- [ ] 1.1 RED: write `QuickMenuContentTest`/`ResolveBackTest` in `:core:domain` — `resolveBack` is
      total over both `QuickMenuContent` cases: `TaskInput → ShowDashboard`, `Dashboard →
      CloseCard`. (Satisfies `overlay-quick-menu`: "Back ordering, levels 2–3", design decision 7.)
- [ ] 1.2 GREEN: create `core/domain/.../overlay/QuickMenuContent.kt` — `sealed interface
      QuickMenuContent { Dashboard, TaskInput }`, no `android.*` import. (Design decision 4.)
- [ ] 1.3 GREEN: add `BackOutcome` (`ShowDashboard`, `CloseCard`) and `resolveBack(content):
      BackOutcome` to the same file or a sibling file per the design's interface listing.
      (Design decision 7.)
- [ ] 1.4 RED: extend `QuickMenuStateTest` — add `QuickMenuEvent.BackPressed`; re-assert the "every
      event from `Open` yields `Closed`" property now includes `BackPressed`; `reduce(Closed,
      BackPressed) = Closed`. (Design decision 9; `overlay-quick-menu` "no reachable state leaves
      the card undismissable".)
- [ ] 1.5 GREEN: add `BackPressed` as an additive case to `QuickMenuEvent` in
      `core/domain/.../overlay/QuickMenuState.kt`; wire it through `reduce`. Do not reuse
      `OutsideTouch` (design decision 9's rationale — the `SAME_GESTURE_WINDOW_MS` suppression
      would misfire).
- [ ] 1.6 Run `./gradlew :core:domain:test --rerun-tasks`.

## Phase 2: Window becomes focusable + IME target (PR 2, depends on PR 1)

- [ ] 2.1 RED: write/extend `QuickMenuWindowParamsTest` (Robolectric) — the created
      `LayoutParams` does **not** carry `FLAG_NOT_FOCUSABLE`, **does** set `softInputMode ==
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE`, and still carries
      `FLAG_NOT_TOUCH_MODAL` and `FLAG_WATCH_OUTSIDE_TOUCH`. Assert `OverlayWindowParams` output is
      byte-identical to today (unchanged file — do not touch `OverlayWindowParams.kt`).
- [ ] 2.2 GREEN: in `feature/overlay/.../service/QuickMenuWindowParams.kt`, drop
      `FLAG_NOT_FOCUSABLE` and set `softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE`
      in the same `create(...)` change; update the kdoc to record measured facts 1–2 from
      design.md and note this reverses the file's prior "non-focusable" framing. Do not touch
      `OverlayWindowParams.kt`. (Design decision 1 — indivisible; both halves land in this one task.)
- [ ] 2.3 RED: write a Robolectric/JVM test asserting `QuickMenuWindowController` never calls
      `updateViewLayout` or otherwise mutates the card's live `LayoutParams` to toggle focusability
      after `addView` — the value is fixed at construction only. (Design decision 2.)
- [ ] 2.4 GREEN: wire `ComposeOverlayHost` with `setViewTreeOnBackPressedDispatcherOwner` so an
      `OnBackPressedDispatcherOwner` exists on the card's view tree (design decision 8). No
      `BackHandler` yet — that lands in Phase 4 with the container.
- [ ] 2.5 Run `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks`.

## Phase 3: Controller — hoisted content, restoration, back application (PR 3, depends on PR 2)

- [ ] 3.1 RED: write `QuickMenuWindowControllerTest` (Robolectric) covering restoration per design's
      test-split table: open → swap to `TaskInput` → dismiss via each of `OutsideTouch`,
      `PetTapped`, `PetDragged`, `BackPressed`, `AppLaunched`, `ScreenOff` → reopen renders
      `TaskInput`, once per dismissal path so none is special-cased; left on `Dashboard`, reopens
      on `Dashboard`; a fresh controller opens on `Dashboard`; `destroy()` then reopen yields
      `Dashboard` (design decisions 5, 5b).
- [ ] 3.2 GREEN: add a `content: QuickMenuContent` field to `QuickMenuWindowController`, **not**
      reset in `closeWindow()`; `openWindow` reads it and renders whatever content was active;
      `destroy()` and a fresh controller both start at `Dashboard`. (Design decisions 4, 5, 5a, 5b —
      the field lives on the controller, not `QuickMenuState`, not the service, not Compose.)
- [ ] 3.3 RED: write a test asserting `onEvent(BackPressed)` applies `resolveBack(content)`:
      `TaskInput` swaps `content` to `Dashboard` without closing the window; `Dashboard` forwards
      `QuickMenuEvent.BackPressed` into `reduce`, closing the card. (Design decision 7's
      "only `CloseCard` forwards... into `reduce`".)
- [ ] 3.4 GREEN: apply `resolveBack` in the controller's back-handling path per 3.3.
- [ ] 3.5 RED (source-scan): write `QuickMenuBackWiringCodeTest` — exactly one
      `setViewTreeOnBackPressedDispatcherOwner` and exactly one `BackHandler` reference exist in
      the quick-menu package's `src/main`, and `KEYCODE_BACK` appears nowhere. This is the
      inversion of `NoBackGestureCodeTest`, not a new independent gate — reuse its file-scanning
      approach.
- [ ] 3.6 GREEN: delete `feature/overlay/.../quickmenu/NoBackGestureCodeTest.kt` and add
      `QuickMenuBackWiringCodeTest.kt` in its place, recording in its kdoc that it inverts the
      structural gate now that the card is focusable (design's "two contradicted tests" section).
      Do not delete this coverage silently — the replacement file's kdoc is the record.
- [ ] 3.7 GREEN: delete
      `feature/overlay/.../androidTest/.../QuickMenuBackGestureDoesNotDismissTest.kt` with no
      instrumented successor, recording in a code comment or this task's completion note why: its
      assertion also passes if the key never arrives, and adb-injected input does not reach the
      overlay on the maintainer's device, so an inverted version would be un-diagnosable. Coverage
      is redistributed to 1.1 (ordering, pure unit), 3.5 (wiring, structural), and the manual row
      in Phase 5.
- [ ] 3.8 RED (source-scan): write a test asserting no `DataStore`, `SharedPreferences`, `Room`, or
      file-write reference appears anywhere in the quick-menu package. (Design decision 5b —
      "introduces no persisted storage" held structurally.)
- [ ] 3.9 GREEN: confirm 3.8 passes with no production code change (no persistence exists to
      remove).
- [ ] 3.10 Run `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks`.

## Phase 4: Container UI, config, DI, accessibility (PR 4, depends on PR 3)

- [ ] 4.1 Add `taskTitleMaxLength: Int` and `inputContentMinHeightDp: Int` to
      `feature/overlay/.../quickmenu/QuickMenuConfig.kt`; provide both via `@Provides` in
      `feature/overlay/.../di/OverlayModule.kt`. No inline literals in the input composable.
      (Design decision 11 — the standing injected-config rule.)
- [ ] 4.2 RED: write a Compose semantics test — the container (`QuickMenuCard`) shows exactly one
      content at a time; activating the add-task control swaps to the input content; leaving the
      input restores the dashboard content; no second window is opened during a swap.
- [ ] 4.3 GREEN: split `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` into the container
      (hosts `content: QuickMenuContent`, one `BackHandler` calling `onBack`, per design decision
      8) and move today's three metric rows + launch button into a new
      `QuickMenuDashboardContent.kt`, appearance unchanged. The disabled add-task control becomes
      the enabled swap trigger.
- [ ] 4.4 GREEN: create `feature/overlay/.../quickmenu/ui/QuickMenuTaskInputContent.kt` — the text
      field, submit action, and a leave-to-dashboard action. Submit's callback signature is
      `(String) -> Unit`, wired to a no-op logging lambda in the controller. **No `:core:domain/task`
      import anywhere in this file or the controller — `CreateOneOffTask` is not called.** (Design
      decision 12; #100 boundary.)
- [ ] 4.5 RED: write a test asserting the field requests focus only on tap — no `FocusRequester`
      auto-focus call fires on card open, on swap to the input content, or on a restoration-path
      reopen directly onto the input content. (Design decision 10 — sharper on the restoration
      path, not just the first-open path.)
- [ ] 4.6 GREEN: implement tap-to-focus only, per 4.5; verify no `showSoftInput` call exists on any
      open/swap/reopen path.
- [ ] 4.7 RED: write a Compose semantics test — typed text is not restored: enter text, dispose the
      composition, recompose on `TaskInput`, assert the field is empty. Assert this independently
      from the content-restoration test (3.1) so a regression in one cannot be misread as the other
      passing. (Design decision 5's Compose-owned half.)
- [ ] 4.8 GREEN: confirm the field's state is composition-local (`remember`/local `mutableStateOf`),
      never hoisted to the controller.
- [ ] 4.9 RED: write a Compose semantics test — a max-length-exceeding string is rejected at the
      field (bounded by `taskTitleMaxLength`), not silently truncated downstream. (Threat matrix:
      untrusted input crossing the window boundary.)
- [ ] 4.10 GREEN: bound field input length in `QuickMenuTaskInputContent` using the injected config.
- [ ] 4.11 RED: write a Compose semantics test — the field carries a content description, meets the
      48dp minimum touch target, exposes IME action semantics matching its role (done/send-class),
      and displays a placeholder/hint string sourced from a string resource when empty.
      Accessibility assertions iterate `onAllNodes(hasClickAction())` and assert per node, never by
      naming test tags.
- [ ] 4.12 GREEN: implement the field's accessibility semantics and placeholder string resource;
      retire `feature_overlay_quickmenu_add_task_button` and `..._add_task_description` string
      resources since the disabled control they described no longer exists (spec's open question —
      exact wording left to the maintainer; use a neutral placeholder value and flag it in the PR
      description as pending copy review).
- [ ] 4.13 RED: write an instrumented `WindowManager` test — the card window is added with the
      expected focus flags and `softInputMode`; `addView`/`removeView` land at the right lifecycle
      points; `destroy()` leaves no view attached; the pet window's `LayoutParams` are bit-for-bit
      unchanged before and after the card's full open/dismiss lifecycle.
- [ ] 4.14 GREEN: confirm 4.13 passes against the Phase 2/3 controller changes with no further
      production change expected.
- [ ] 4.15 Record the two spec-required statements as explicit, searchable text (not silent
      satisfaction): (a) issue #17's back-gesture criterion is now met, with the #82 two-OEM gap
      recorded separately as a tracked deviation; (b) the #82 two-OEM deviation itself, naming
      issue #82 as the open tracker and stating the criterion is not met by this change. Place both
      in `proposal.md` or `design.md` per where the spec's machine-verifiable scenario expects to
      find them (already drafted in design.md's "Tracked deviation" section — confirm it matches
      shipped code and is still present).
- [ ] 4.16 Run
      `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks`
      (the real CI gate command — includes `lintDebug`, not an approximation).

## Phase 5: Manual verification and documentation (not closable by this pipeline)

- [ ] 5.1 **Maintainer-blocking**: keyboard appears on field tap; field fully visible while typing
      under `ADJUST_RESIZE`; no video-pause regression on the device used for the spike.
- [ ] 5.2 **Maintainer-blocking**: the three-level back ordering, in person, in this exact order —
      first press dismisses the keyboard only (card stays open, input content stays shown); second
      press swaps to the dashboard content (card stays open); third press dismisses the card. No
      automated route exists for this: adb-injected `KEYCODE_BACK` does not reach the overlay on
      this device, so the IME-consumes-first-press behavior can only be observed by hand.
- [ ] 5.3 **Maintainer-blocking**: reopening the card on the dashboard after a real process kill
      (not `QuickMenuWindowController.destroy()`) — kill the app process, reopen the card by
      tapping the pet, confirm it opens on the dashboard content with no keyboard raised. This is
      distinct from 3.1's `destroy()` coverage, which only proves service-teardown behavior, not an
      actual process death.
- [ ] 5.4 **Maintainer-blocking**: manual TalkBack pass on the container — both contents, the swap
      trigger, and the field — every element announced correctly, app underneath remains reachable.
- [ ] 5.5 Via `gh` CLI, add a comment on issue #18 (or #17, per maintainer preference) recording
      that the back-gesture criterion is now met and pointing at the #82 tracked deviation for the
      two-OEM gap, mirroring how slice 3-B closed #17.
- [ ] 5.6 Verify the proposal.md success-criteria checklist against shipped code: no
      `CreateOneOffTask` or other task-domain use case call anywhere in the diff, no text-draft
      persistence, `OverlayWindowParams.kt` unchanged, pet window `LayoutParams` never mutated.
