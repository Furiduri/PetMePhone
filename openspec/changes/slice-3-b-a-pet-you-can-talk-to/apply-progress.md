# Apply progress: slice-3-b-a-pet-you-can-talk-to

## PR 1 — `minSdk` 26 → 30 bump (Phase 1) — DONE

- [x] 1.1 `ProjectConfig.minSdk` 26 → 30 in `build-logic/convention/src/main/kotlin/ProjectConfig.kt`.
- [x] 1.2 Deleted the four dead compat branches in `PetOverlayService.kt` (see below).
- [x] 1.3 `openspec/config.yaml` and `openspec/specs/build-foundation/spec.md` updated to state
      `minSdk = 30`, with a new "minSdk is 30, with no compat path below it" requirement recorded
      under an `ADDED Requirements (slice-3-b)` section.
- [x] 1.4 `./gradlew :feature:overlay:testDebugUnitTest --rerun-tasks` — BUILD SUCCESSFUL, no
      compat-branch test references found (`SDK_INT` / `VERSION_CODES.R` / `VERSION_CODES.Q`
      absent from `feature/overlay/src/test`).

Full CI gate also run and green (see below) even though task 1.4 only required the focused
command — the phase boundary in `design.md`'s PR table puts the CI gate at the end of the whole
change, but running it now catches any minSdk-bump regression early while the diff is still small.

**Not started**: PR 3+ (card work). Out of scope for this run per explicit instruction.

## PR 2 — `:spike:ime-viability` module (Phase 2) — DONE (build-side; maintainer run still open)

- [x] 2.1 New standalone application module `spike/ime-viability` (`com.android.application`,
      `applicationId = com.petmephone.spike.imeviability`). Deliberately does NOT apply the shared
      `com.petmephone.android.application` convention plugin — the convention plugin hardcodes
      `applicationId = "com.gcatcode.petmephone"`, which this module must not share (decision 13:
      "own `applicationId`"). Instead it applies the raw `com.android.application` +
      `org.jetbrains.kotlin.plugin.compose` plugins directly via the version catalog, with
      `minSdk`/`compileSdk`/`targetSdk` as literals — documented in the build script as an
      intentional exception to the "no build literal" convention, because adding a `ProjectConfig`
      dependency here would pull this deliberately-isolated module back into the shared
      convention-plugin graph for no reason beyond one constant. Added to `settings.gradle.kts`;
      not referenced from `:app`'s `build.gradle.kts` or any release variant.
- [x] 2.2 Focus-only mode: `SpikeOverlayService` adds a `TYPE_APPLICATION_OVERLAY` window
      (`SpikeWindowParams`, focusable — deliberately unlike the production card's
      `FLAG_NOT_FOCUSABLE`, since measuring focus's cost is the point) with no text field.
- [x] 2.3 Full-IME mode: same window with an `EditText` that requests focus and is shown via
      `InputMethodManager.showSoftInput`; `ViewCompat.setOnApplyWindowInsetsListener` wired for
      `ime()` visibility and to detect coverage (`EditText` bottom vs. `screenHeight - imeBottom`).
- [x] 2.4 Automatic findings recorded per run: `keyboardAppeared`, `keyboardCoversField`,
      `imeInsetCallbackFired` (literally "did any inset callback fire at all," per spec wording),
      `windowEverReceivedFocus` (via a `FocusTrackingContainer` overriding
      `onWindowFocusChanged`), `windowRemovedCleanly` (best-effort — `removeView` completing
      without an exception; documented in `FindingsEntry`'s kdoc as not itself proof that focus
      returned, which is why the human question below exists).
- [x] 2.5 `SpikeActivity` shows an `AlertDialog` after Finish with two explicit Yes/No/Not-tested
      questions (video-pause, focus-return), recorded as `HumanAnswer` separately from the
      automatic fields in the same `FindingsEntry`.
- [x] 2.6 `FindingsRepository` appends Markdown to
      `context.getExternalFilesDir(null)/ime-viability-findings.md`; a `FileProvider` (declared in
      the module's manifest, `xml/file_paths.xml`) backs a "Share findings file" button that opens
      the system share sheet.
- [x] 2.7 `openspec/changes/.../spike-findings/README.md` scaffolded with the exact Markdown
      format the app produces and instructions for committing a device's findings.
- [x] 2.8 `./gradlew :spike:ime-viability:assembleDebug --rerun-tasks` — BUILD SUCCESSFUL (run
      twice, once before and once after adding the `windowEverReceivedFocus` signal). Only
      pre-existing-pattern deprecation warnings (`SHOW_FORCED`, `Notification.Builder.addAction`)
      and an AGP `disallowKotlinSourceSets` info warning shared with `:app`; no errors.
      **Maintainer-blocking, as designed**: install + run on real hardware, commit actual findings
      under `spike-findings/` — cannot be closed by this pipeline (adb-injected input does not
      reach the overlay on the maintainer's HyperOS device).

### Design decisions honored, verified against `design.md`

- Decision 6 (non-focusable card) is about the *production* quick-menu card, not this module — the
  spike's window is deliberately focusable in both modes, since measuring focus's cost is what it
  exists to do. `SpikeWindowParams`'s kdoc records this distinction explicitly so it is never
  mistaken for a violation of decision 6.
- Decision 13 (separate module, own `applicationId`, not in `:app`'s graph, not in any release
  variant) — verified: `spike/ime-viability` has no `project(":spike:ime-viability")` reference
  anywhere in `app/build.gradle.kts`, and its own `applicationId` differs from `:app`'s.
- Decision 14 (findings recorded automatically, not hand-transcribed, with a share action) —
  implemented via `FindingsRepository` + the share button; only the two genuinely cross-app
  questions are human-answered, and both are explicit prompts with no default/blank state (the
  dialog's confirm button stays disabled until both are answered).
- Decision 14a (focus-only vs. full-IME measured and recorded separately) — `SpikeMode` is a
  first-class enum threaded through the service, the findings entry, and the Markdown output — no
  shared/merged recording path exists between the two modes.
- No code sharing with the production card — verified: `spike/ime-viability` has zero dependency
  on `:feature:overlay`, `:core:domain`, or `:core:designsystem` (its only dependencies are
  `androidx.core`, `androidx.activity.compose`, and the Compose BOM/UI/Material3 libraries, all
  declared directly in its own `build.gradle.kts`).

### Files changed/added (PR 2)

- `settings.gradle.kts` — `:spike:ime-viability` included, with a comment recording decision 13.
- `spike/ime-viability/build.gradle.kts`, `.gitignore`, `README.md` (install/permission/mode/share
  instructions).
- `spike/ime-viability/src/main/AndroidManifest.xml` — `SYSTEM_ALERT_WINDOW`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` permissions; `SpikeActivity` (launcher);
  `SpikeOverlayService` (`specialUse` foreground service, mirroring `PetOverlayService`'s manifest
  pattern without sharing code); `FileProvider`.
- `spike/ime-viability/src/main/kotlin/com/petmephone/spike/imeviability/`:
  `SpikeMode.kt`, `HumanAnswer.kt`, `DeviceInfo.kt`, `FindingsEntry.kt`, `FindingsRepository.kt`,
  `SpikeWindowParams.kt`, `SpikeOverlayService.kt`, `SpikeActivity.kt`.
- `spike/ime-viability/src/main/res/values/strings.xml`, `res/xml/file_paths.xml`.
- `openspec/changes/slice-3-b-a-pet-you-can-talk-to/spike-findings/README.md` — new.
- `openspec/changes/slice-3-b-a-pet-you-can-talk-to/tasks.md` — tasks 2.1–2.8 marked `[x]`.

Committed as one work unit: `feat(spike): add the :spike:ime-viability measuring instrument`.

### Deviation from the literal task wording, and why it's not a scope change

Task 2.4 says "focusable-after-dismiss (via `LayoutParams` re-read + `onWindowFocusChanged`)".
What's implemented is `windowEverReceivedFocus` (via `onWindowFocusChanged`, confirming the window
genuinely became focused at least once — proving the measurement is real) plus
`windowRemovedCleanly` (a `removeView` success/failure marker) rather than a literal
post-`removeView` `LayoutParams` re-read, because after `removeView` succeeds there is no longer a
live `LayoutParams` instance attached to a window to re-read — the object either never existed
past removal or reading it tells you nothing about the *system's* window-focus state. The
questions the spec actually requires answered ("does the window ever remain focusable after the
card closes", "does focus return correctly to the app underneath") are covered by the human
`focusReturnedAfterDismissal` prompt, which is the only actor that can observe the app underneath's
real state — exactly per decision 14's split between automatic and human-owned signals.

### Next

`sdd-apply` again for PR 3 (`core/domain` positioning + state machine: `MetricReading`,
`QuickMenuPlacement`, `QuickMenuState`), per `design.md`'s PR table (PR 3 depends only on PR 1,
same as PR 2).

## What the bump made dead, and what was removed

All four in `feature/overlay/.../service/PetOverlayService.kt`, exactly as `design.md`'s "What the
`minSdk` bump makes dead" section specifies:

1. `onCreate`'s `SDK_INT >= Q` fork around `startForeground(...)` — Q is API 29, below the new
   floor of 30, so the `else` branch (`startForeground` without a type) was unreachable. Removed;
   now unconditionally calls the typed overload.
2. `usableBoundsPx()`'s `SDK_INT < R` early return (raw bounds, no inset subtraction) — R is API
   30, i.e. exactly the new floor, so the branch could never trigger. Removed; the function now
   always subtracts insets via `WindowMetrics`.
3. `navigationBarInsetBottomPx()`'s `SDK_INT < R` early return (`return 0`) — same reasoning.
   Removed.
4. `screenBoundsPx()`'s `else` branch using `defaultDisplay.getRealMetrics()` plus its
   `@Suppress("DEPRECATION")` — the `SDK_INT >= R` condition is now always true, so the whole
   `if/else` collapsed to the `WindowMetrics.bounds` branch alone; the now-unused `@Suppress`
   annotation was removed with it.

The now-unused `android.os.Build` import was also removed from `PetOverlayService.kt` (nothing
else in the file referenced `Build` after the above).

## Guards found elsewhere in the repo, and their disposition

Searched the whole repo for `Build.VERSION` / `SDK_INT` guards (`grep -rn "Build\.VERSION|SDK_INT"
**/*.kt`). Two hits outside `PetOverlayService.kt`, both kept as still meaningful at `minSdk = 30`:

- `core/designsystem/.../theme/Theme.kt:82` — `dynamicColor && Build.VERSION.SDK_INT >=
  Build.VERSION_CODES.S`. `S` is API 31, above the new floor of 30, so this branch is still
  reachable and still gates real behavior (dynamic color availability). **Kept.**
- `core/data/src/test/.../ActiveCharacterRepositoryImplTest.kt:29` — a comment explaining that an
  un-stubbed `Build.VERSION.SDK_INT` reads as `0` on a plain JVM test. Not a version guard at all,
  just documentation of Robolectric/JVM test behavior. **Kept, out of scope.**

No manifest, `build.gradle.kts`, or other module hardcodes `minSdk` or the literal `26` — the only
`minSdk` declaration in the whole repo is `ProjectConfig.kt` (verified via repo-wide grep before
and after the change).

## CI gate command run and observed

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

First attempt failed with a Windows file-lock (`FileSystemException` on
`app/build/intermediates/.../classes.jar`, "El proceso no tiene acceso al archivo porque está
siendo utilizado por otro proceso") — a transient Windows/Gradle-daemon issue, not a code problem,
matching the known pattern from part A. Ran `./gradlew --stop`, then reran the exact same command
cleanly:

```
BUILD SUCCESSFUL in 3m 51s
472 actionable tasks: 472 executed
Configuration cache entry reused.
```

## Files changed (PR 1)

- `build-logic/convention/src/main/kotlin/ProjectConfig.kt` — `minSdk` 26 → 30.
- `feature/overlay/src/main/kotlin/com/gcatcode/petmephone/feature/overlay/service/PetOverlayService.kt`
  — four dead compat branches removed, unused `Build` import removed.
- `openspec/config.yaml` — tech-stack line states `minSdk 30`.
- `openspec/specs/build-foundation/spec.md` — new requirement recording the `minSdk = 30` decision
  and the "no guard below API 30" constraint, with two scenarios.
- `openspec/changes/slice-3-b-a-pet-you-can-talk-to/tasks.md` — tasks 1.1–1.4 marked `[x]`.
- `openspec/changes/slice-3-b-a-pet-you-can-talk-to/` (proposal, design, exploration, specs/*,
  tasks.md) — committed as part of this branch's work; previously untracked.

## Next

`sdd-apply` again for PR 2 (the `:spike:ime-viability` module), per the chain in `design.md`'s PR
table (PR 2 depends only on PR 1, same as PR 3).

## PR 3 — `MetricReading`, `QuickMenuPlacement`, `QuickMenuState` (Phase 3) — DONE

Branch `feat/slice-3b-domain-types`, cut from `feat/slice-3b-ime-spike` (PR #81), stacked on
`feat/slice-3b-minsdk-30` (PR #80). Scope for this run was strictly the pure `:core:domain` types —
no `QuickMenuWindowController`, no window params, no Compose, no service wiring (those are PR 5/6).

- [x] 3.1 RED: `QuickMenuPlacementTest` — 4 corner anchors, 2 mid-edges, clamping on each axis,
      top-inset and cutout subtraction, determinism.
- [x] 3.2 GREEN: `core/domain/.../metric/MetricReading.kt` — `Loading | Available(percent) |
      Unavailable`.
- [x] 3.3 GREEN: `core/domain/.../overlay/QuickMenuPlacement.kt` — `QuickMenuAnchor`,
      `ScreenInsets`, `place(...)`.
- [x] 3.4 RED: `QuickMenuStateTest` — full state×event matrix, named test asserting every event
      from `Open` yields `Closed`, and `PetTapped` while `Open` closing.
- [x] 3.5 GREEN: `core/domain/.../overlay/QuickMenuState.kt` — `Closed | Open(anchor)`,
      `QuickMenuEvent` (`PetTapped, PetDragged, OutsideTouch, AppLaunched, ScreenOff` — no
      `BackPressed`), total `reduce(state, event)`.
- [x] 3.6 `./gradlew :core:domain:test --rerun-tasks` — BUILD SUCCESSFUL, all new tests green.

### `QuickMenuPlacement.place` algorithm, and why

Each axis is decided **independently** by comparing the space on either side of the anchor inside
the usable bounds (screen bounds with `insets` subtracted):

- **Unequal space** on an axis → the card opens toward the larger side, offset from the anchor's
  near edge by `gapPx`. At a screen corner both axes are imbalanced, so both axes open toward
  their larger side — this alone produces the diagonal corner placement (`down-right`,
  `down-left`, `up-right`, `up-left`) the spec requires, with no separate "corner" code path.
- **Exactly equal space** on an axis (anchor centered on that axis, e.g. the vertical mid-point of
  an edge) → the card is centered on the anchor's center for that axis instead of being pushed to
  an edge, matching the spec's "vertically centered toward the side with more room" wording for
  the mid-edge scenario.
- The raw result is then clamped to the usable bounds on both axes (`coerceIn`, with the upper
  bound floored at the lower bound so a card wider/taller than the available space still clamps to
  a valid, non-inverted range rather than throwing).

No `android.*` / `androidx.*` import exists in either new file, matching the existing
`nearestEdge` / `OverlayPositionFraction.ofPixels` precedent in this module.

### `QuickMenuState` / `reduce`, and the dismissability property

`reduce` is a `when` over `(state, event)` with every branch enumerated (no `else`), so removing an
event case from either state's `when` is a compile error, not a silent fallthrough. From `Open`,
all five events (`PetTapped`, `PetDragged`, `OutsideTouch`, `AppLaunched`, `ScreenOff`) map to
`Closed` — asserted directly by
`every event from Open yields Closed - no undismissable state exists`. From `Closed`, only
`PetTapped` has an effect (opens at the tapped anchor); the other four are no-ops, asserted by a
separate test so a future change accidentally making one of them open the card from `Closed` fails
loudly.

### Test quality — concrete failing input per test

| Test | Concrete input that makes it fail |
|---|---|
| `pet at top-left corner opens down-right` | Anchor `(0,0)`; fails if `place` returned `x < PET_SIZE_PX + GAP_PX` or `y < PET_SIZE_PX + GAP_PX` — e.g. a bug that left the card at `(0,0)` overlapping the pet |
| `pet at top-right corner opens down-left` | Anchor `(860,0)`; fails if `result.x >= anchor.xPx` (card placed right of / under the pet instead of left) |
| `pet at bottom-left corner opens up-right` | Anchor `(0,2060)`; fails if `result.y >= anchor.yPx` (card placed below the pet instead of above) |
| `pet at bottom-right corner opens up-left` | Anchor `(860,2060)`; fails if either axis is not offset away from the anchor |
| `pet at left-edge vertical midpoint opens right and centers vertically` | Anchor `(0, 1030)` — exact vertical midpoint; fails if `result.y` equals the edge-offset formula (`anchor.bottom+gap`) instead of the centered value, proving the tie branch actually fired rather than falling through to the edge-offset branch |
| `pet at right-edge vertical midpoint opens left and centers vertically` | Mirror of the above on the right edge; same failure mode |
| `card wider than available space is clamped within horizontal bounds` | 900px card, anchor at top-right (only ~524px available leftward); fails if `result.x < 0` or `result.x + 900 > 1080` |
| `card taller than available space is clamped within vertical bounds` | 2000px card, anchor at bottom-left; fails if `result.y < 0` or `result.y + 2000 > 2280` |
| `top system-bar inset keeps the card clear of the status bar` | 80px top inset, anchor at `y = 80`; fails if the inset were ignored (unclamped `y` could go below 80) |
| `right display-cutout inset keeps the card clear of the cutout region` | 60px right inset, anchor at right edge; fails if `result.x + cardWidth > 1080 - 60` |
| `repeated calls with identical inputs return the same result` | Any fixed input pair; fails if `place` carried hidden mutable state (e.g. a shared counter or clock read) |
| `PetTapped on Closed opens the card at the tapped anchor` | `reduce(Closed, PetTapped(ANCHOR))`; fails if it returned `Closed` or `Open` with the wrong anchor |
| `every event from Open yields Closed` | Each of the 5 events from `Open`; fails if **any single one** left the state `Open` — e.g. an accidental `PetTapped -> Open(newAnchor)` branch under `Open` |
| `PetTapped while Open closes the card` | `reduce(Open(ANCHOR), PetTapped(OTHER_ANCHOR))`; fails if it returned `Open(OTHER_ANCHOR)` (re-opening at the new anchor) instead of `Closed` |
| `non-PetTapped events while Closed are no-ops` | Each of `PetDragged/OutsideTouch/AppLaunched/ScreenOff` from `Closed`; fails if any spontaneously opened the card |
| `reduce is total and deterministic across the full state x event matrix` | Every `(state, event)` pair called twice; fails if two calls with the same pair differ |

### CI gate command run and observed

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

First attempt failed — not from this change's code, but from an internal Kotlin-lint analyzer
crash (`RuntimeException: Unexpected failure during lint analysis ... Error while resolving
FirRegularClassImpl from RAW_FIR to COMPILER_REQUIRED_ANNOTATIONS`) while lint was analyzing
`core/data`'s pre-existing `ActiveCharacterRepositoryImplTest.kt` — a file this PR does not touch.
Matches the documented transient-failure pattern in this repo (`gradle-up-to-date-is-not-evidence`,
and PR 1's Windows file-lock precedent). Ran `./gradlew --stop`, then reran the exact same command
cleanly:

```
BUILD SUCCESSFUL in 5m 49s
548 actionable tasks: 548 executed
Configuration cache entry reused.
```

`:core:domain:test` alone (focused command, run first): `BUILD SUCCESSFUL in 1m 3s`, 8 tasks
executed, all 24 new tests (10 `QuickMenuPlacementTest` + 5 `QuickMenuStateTest` + existing suite)
green.

### Files changed/added (PR 3)

- `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/metric/MetricReading.kt` — new.
- `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuPlacement.kt`
  — new.
- `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuState.kt` —
  new.
- `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuPlacementTest.kt`
  — new, 10 tests.
- `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuStateTest.kt`
  — new, 5 tests.
- `openspec/changes/slice-3-b-a-pet-you-can-talk-to/tasks.md` — tasks 3.1–3.6 marked `[x]`.

Committed as one work unit: `feat(domain): add quick-menu placement and dismissal state machine`.

### Next

`sdd-apply` again for PR 4 (`ObserveHunger`, `TaskRepository`/DAO Flow counts, DI provider), per
`design.md`'s PR table (PR 4 depends on PR 3).
