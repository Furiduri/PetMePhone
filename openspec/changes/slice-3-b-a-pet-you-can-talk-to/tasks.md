# Tasks: Slice 3 part B — A pet you can talk to (#17, #18 spike)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1560 across 6 PRs (design table), each PR 90–330 |
| 400-line budget risk | Low per-PR / High for the whole change |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 (spike) → PR 3 → PR 4 → PR 5 → PR 6 |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Low (per PR)

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `minSdk` 26→30, dead compat branches removed, config/spec text | PR 1 | `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` | N/A — build config only | Revert `ProjectConfig.kt` + spec text |
| 2 | `:spike:ime-viability` module, both modes, findings scaffold | PR 2 | `./gradlew :spike:ime-viability:assembleDebug --rerun-tasks` | Maintainer installs on device, cannot be run here | Remove module + `settings.gradle.kts` include |
| 3 | `MetricReading`, `QuickMenuPlacement`, `QuickMenuState` + pure tests | PR 3 | `./gradlew :core:domain:test --rerun-tasks` | N/A — pure JVM | Delete 3 files + tests |
| 4 | `ObserveHunger`, `TaskRepository`/DAO Flow counts, DI provider | PR 4 | `./gradlew :core:domain:test :core:data:testDebugUnitTest --rerun-tasks` | N/A — Robolectric in-memory Room | Delete `ObserveHunger` + revert DAO/repo/DI |
| 5 | `QuickMenuWindowParams`, `QuickMenuWindowController`, service delegation | PR 5 | `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` | `adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow` + `assembleDebugAndroidTest` on emulator-5554 | Delete controller + params, restore `onPetTapped` stub |
| 6 | `QuickMenuCard` UI, `MetricRow`, config, state-holder metrics, semantics tests | PR 6 | `./gradlew :feature:overlay:testDebugUnitTest assembleDebugAndroidTest --rerun-tasks` | emulator-5554, semantics tree + touch target instrumented tests | Delete UI files, revert state holder |

## Phase 1: Foundation — `minSdk` bump (PR 1)

- [x] 1.1 Bump `ProjectConfig.minSdk` 26 → 30 in `build-logic/.../ProjectConfig.kt`.
- [x] 1.2 Delete `usableBoundsPx()`'s `SDK_INT < R` branch, `navigationBarInsetBottomPx()`'s `SDK_INT < R` branch, `screenBoundsPx()`'s `getRealMetrics` branch + `@Suppress("DEPRECATION")`, and `onCreate`'s `SDK_INT >= Q` fork in `PetOverlayService.kt`.
- [x] 1.3 Update `openspec/config.yaml` and `openspec/specs/build-foundation/spec.md` to state `minSdk = 30`.
- [x] 1.4 Run `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks`; confirm no compat-branch test references remain.

## Phase 2: Spike module (PR 2, depends on PR 1)

- [x] 2.1 Create `:spike:ime-viability` `com.android.application` module with its own `applicationId`; add to `settings.gradle.kts`; exclude from `:app`'s dependency graph and release variants.
- [x] 2.2 Implement focus-only mode: focusable `TYPE_APPLICATION_OVERLAY` window, no text field.
- [x] 2.3 Implement full-IME mode: focusable window with a text field; wire `ViewCompat.setOnApplyWindowInsetsListener` for `ime()` visibility/coverage.
- [x] 2.4 Record automatic findings per mode: keyboard-appears, keyboard-covers-field, ime-insets-without-imePadding, focusable-after-dismiss (via `LayoutParams` re-read + `onWindowFocusChanged`).
- [x] 2.5 Add human-answer prompts (Yes/No/Not-tested) per mode for video-pause and focus-return, recorded separately from automatic answers.
- [x] 2.6 Write results to app-specific external files dir as Markdown; add a share/copy action.
- [x] 2.7 Scaffold `openspec/changes/.../spike-findings/README.md` with the per-device/per-mode findings template (satisfies ime-viability-spike: "Findings record exists in the diff").
- [x] 2.8 Run `./gradlew :spike:ime-viability:assembleDebug --rerun-tasks`. **Maintainer-blocking**: install + run on real hardware, commit actual findings — cannot be closed by this pipeline (adb-injected input does not reach the overlay on the maintainer's HyperOS device).

## Phase 3: Pure domain — positioning + state machine (PR 3, depends on PR 1)

- [x] 3.1 RED: write `QuickMenuPlacementTest` covering four corner anchors, two mid-edges, clamping on each axis when the card exceeds available space, inset/cutout subtraction, and determinism on repeated calls.
- [x] 3.2 GREEN: create `core/domain/.../metric/MetricReading.kt` (`Loading | Available(percent) | Unavailable`).
- [x] 3.3 GREEN: create `core/domain/.../overlay/QuickMenuPlacement.kt` with `QuickMenuAnchor`, `ScreenInsets`, `place(...)`.
- [x] 3.4 RED: write `QuickMenuStateTest` — exhaustive state×event matrix with a named test asserting every event from `Open` yields `Closed` (no undismissable state), and `PetTapped` while `Open` closing.
- [x] 3.5 GREEN: create `core/domain/.../overlay/QuickMenuState.kt` — `Closed | Open(anchor)`, `QuickMenuEvent` (`PetTapped, PetDragged, OutsideTouch, AppLaunched, ScreenOff` — no `BackPressed`), total `reduce(state, event)`.
- [x] 3.6 Run `./gradlew :core:domain:test --rerun-tasks`.

## Phase 4: Hunger reactive plumbing (PR 4, depends on PR 3)

- [x] 4.1 RED: write `ObserveHungerTest` (fake `AppClock`/`TaskRepository`, `runTest` virtual time) — emits on count change, re-emits across a day boundary.
- [x] 4.2 Add `observeManuallyCreatedOn(date): Flow<Int>` and `observeRecurringScheduledOn(date): Flow<Int>` to `core/domain/.../task/TaskRepository.kt`.
- [x] 4.3 RED: write Robolectric `TaskDaoTest` cases for both new `@Query` counts (emit on insert, ignore generated occurrences).
- [x] 4.4 GREEN: add both `@Query` counts to `core/data/.../local/task/TaskDao.kt` (no balance literal).
- [x] 4.5 GREEN: implement both methods in `core/data/.../repository/TaskRepositoryImpl.kt`.
- [x] 4.6 GREEN: create `core/domain/.../balance/ObserveHunger.kt` — `todayFlow` + `flatMapLatest` + `combine` into `calculateHunger`.
- [x] 4.7 Add `@Provides ObserveHunger` to `core/data/.../di/DataModule.kt`.
- [x] 4.8 Run `./gradlew :core:domain:test :core:data:testDebugUnitTest --rerun-tasks`.

## Phase 5: Window controller + service wiring (PR 5, depends on PR 4)

- [x] 5.1 RED: write Robolectric `QuickMenuWindowParamsTest` — `FLAG_WATCH_OUTSIDE_TOUCH` + `FLAG_NOT_FOCUSABLE` set, `FLAG_ALT_FOCUSABLE_IM` absent; assert `OverlayWindowParams` still omits `FLAG_WATCH_OUTSIDE_TOUCH`.
- [x] 5.2 GREEN: create `feature/overlay/.../service/QuickMenuWindowParams.kt` mirroring `OverlayWindowParams`, differing only in `FLAG_WATCH_OUTSIDE_TOUCH`.
- [x] 5.3 RED: write `QuickMenuWindowControllerTest` — every dismissal path calls `removeView` and clears the view field.
- [x] 5.4 GREEN: create `feature/overlay/.../quickmenu/QuickMenuWindowController.kt` — `onEvent(event)`, `destroy()`, owns add/remove, non-focusable (decision 6), no back dispatcher (decision 7).
- [x] 5.5 RED (source-scan): write a test asserting no `OnBackPressedDispatcher`, `setViewTreeOnBackPressedDispatcherOwner`, `BackHandler`, or `KEYCODE_BACK` reference exists anywhere in `feature/overlay/src/main`'s quick-menu package.
- [x] 5.6 Modify `PetOverlayService.kt`: `onPetTapped` delegates to the controller; drag maps to `PetDragged`; screen-off maps to `ScreenOff`.
- [x] 5.7 RED: write an instrumented test — the launch button's `Intent` is explicit, names this app's launcher component, carries `FLAG_ACTIVITY_NEW_TASK`; failure is caught and logged, never crashes the service (threat-matrix row: process integration).
- [x] 5.8 GREEN: implement the launch action in the controller with an explicit `Intent`.
- [x] 5.9 Instrumented (emulator-5554): tap opens a second window with the pet's params byte-identical before/after; `ACTION_OUTSIDE`, pet-tap, pet-drag dismiss; launch button starts the launcher `Activity`. Command: `adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow && ./gradlew :feature:overlay:connectedDebugAndroidTest --rerun-tasks`.
- [ ] 5.10 **Maintainer-blocking**: confirm the app underneath never receives `onWindowFocusChanged(false)` because of the card, on real hardware.

## Phase 6: Card UI + metrics + accessibility (PR 6, depends on PR 5)

- [ ] 6.1 Modify `PetOverlayStateHolder.kt`: `hunger: StateFlow<MetricReading>` via `stateIn(scope, WhileSubscribed(0), Loading)`; `happiness`/`energy` as plain `val = MetricReading.Unavailable`.
- [ ] 6.2 Create `feature/overlay/.../quickmenu/QuickMenuConfig.kt` (card width/height dp, gap) and `@Provides` it in `OverlayModule.kt`.
- [ ] 6.3 RED: write a Compose semantics test — no editable text field or IME-triggering element anywhere in the card.
- [ ] 6.4 RED: write a Compose semantics test — every interactive node has a content description or role, no undescribed full-bounds scrim, every target ≥48dp.
- [ ] 6.5 GREEN: create `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` and `MetricRow.kt` with semantics and 48dp targets, `@Preview` data only.
- [ ] 6.6 RED: write an instrumented test — pressing device back while the card is open does not dismiss it; dismissal only via `ACTION_OUTSIDE` or pet-tap/drag.
- [ ] 6.7 Run `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks` (CI gate command).
- [ ] 6.8 **Maintainer-blocking**: manual TalkBack pass on real hardware — every element announced correctly, app underneath remains reachable.

## Phase 7: Documentation and deviation tracking

- [ ] 7.1 Update `openspec/changes/.../specs/overlay-quick-menu/spec.md` back-gesture requirement to record the deviation and its unblocking condition (already drafted in design; confirm it matches shipped code).
- [ ] 7.2 Via `gh` CLI, add a comment on issue #17 recording the tracked deviation: the back-gesture criterion is not met by this change, why (non-focusable window, no key-event delivery, spike gates it), and what unblocks it (spike's focus-only result).
- [ ] 7.3 Verify success-criteria checklist from `proposal.md` against shipped code; confirm no `TaskRepository` fake, no IME implementation outside the spike module, no #27 work exists in the diff.
