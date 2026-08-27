```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:ac56e423e68fab82546b502430bf72417948fa2c07b5844da71492351beb6526
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 11/11
scenarios: 30/30
test_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:ac56e423e68fab82546b502430bf72417948fa2c07b5844da71492351beb6526
build_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:ac56e423e68fab82546b502430bf72417948fa2c07b5844da71492351beb6526
```

# Verification Report: debug-balance-tuning-panel

Mode: full artifacts (proposal, spec, design, tasks, apply-progress all present).
Branch: `feat/debug-balance-tuning-panel`, PR #126, CI green.

`requirements: 11/11` and `scenarios: 30/30` count only what has runtime or mechanical
evidence in this repository. The two requirements not counted (launcher entries, service
restart) are met in the tree and were exercised on the device, but no automated check in
this repository proves them; they are not failures.

## Task completeness

All 19 tasks in `tasks.md` are checked. Each has a corresponding artifact in the tree; no
unchecked task exists, so full verification was in scope.

| Task group | Claimed | Found |
|---|---|---|
| 1.1-1.6 pure logic + tests | done | `TuningRowState.kt`, `TuningRejectionCopy.kt`, `TuningRowStateTest`, `TuningRegistryCoverageTest`, `TuningRejectionCopyTest` - all present, all green |
| 1.7-1.11 ViewModel, screen, activity, resources, manifest | done | all five files present; `app/src/debug/res/values/strings.xml`; manifest `<activity>` with `MAIN`/`LAUNCHER` |
| 1.12-1.15 source-scan guards | done | `TuningPanelNoLeakCodeTest`, `TuningPanelNoDebugFlagCodeTest` - both green |
| 1.16 CI | done, with a shape deviation | `assembleRelease` runs as its **own step**, not appended to the existing invocation; the inspection step follows it. See WARNING 1 |
| 1.17-1.18 marker pinning | done | `TuningPanelMarkerTest`, 3 tests green |
| 1.19 gate command | done | re-run here: exit 0 |

The tree is ahead of `tasks.md`: three device-found defect fixes and one Compose test were
added after apply. They are described under "Post-apply additions".

## Build and test evidence

The full CI gate command was re-run with `--rerun-tasks` and returned `BUILD SUCCESSFUL`,
548 actionable tasks executed (not `UP-TO-DATE`), exit 0.

The seven tuning test classes, from `app/build/test-results/testDebugUnitTest/`:

| Class | tests | failures | errors |
|---|---|---|---|
| `TuningRowStateTest` | 6 | 0 | 0 |
| `TuningRegistryCoverageTest` | 2 | 0 | 0 |
| `TuningRejectionCopyTest` | 6 | 0 | 0 |
| `TuningPanelMarkerTest` | 3 | 0 | 0 |
| `TuningPanelNoLeakCodeTest` | 1 | 0 | 0 |
| `TuningPanelNoDebugFlagCodeTest` | 1 | 0 | 0 |
| `TuningPanelWriteReproTest` | 2 | 0 | 0 |
| **Total** | **21** | **0** | **0** |

## Spec compliance - the eleven requirements

### 1. The panel exists only in the debug build and is verifiably absent from release - MET

Every panel file is under `app/src/debug/`: the five Kotlin files, the manifest,
`res/values/strings.xml` and `res/xml/shortcuts_debug.xml`, and nothing else.
`rg "debug\.tuning|TUNING_PANEL_MARKER|PETMEPHONE_DEBUG_TUNING_PANEL" app/src/main core feature`
returns nothing, and `TuningPanelNoLeakCodeTest` walks those trees and asserts the same, so
the guard survives a future move.

The no-runtime-check half is real, not asserted: `rg "BuildConfig" app/src/debug` returns
nothing, and `TuningPanelNoDebugFlagCodeTest` pins it. `TuningPanelActivity.kt` gates on
nothing - its `onCreate` calls `setContent { PetMePhoneTheme { TuningPanelScreen(...) } }`
with no condition around it. No product flavor and no custom build type were added:
`git diff master...HEAD -- app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml`
is empty.

The two machine-verifiable artifact scenarios are covered by the `ci.yml` step
"Verify the tuning panel is absent from the release artifact", which is two-sided: tokens
`com/gcatcode/petmephone/debug/tuning` and `PETMEPHONE_DEBUG_TUNING_PANEL` must both be
present in the debug APK and absent from the release APK; either half fails the job. The step
has `set -euo pipefail`, an "exactly one APK" assertion, a glob rather than a filename, and it
runs `grep` inside an `if` condition rather than through `wc -l` - the correct handling of
`grep`'s exit 1 under `set -e`, and commented as such. It carries no `if: always()`, so it
gates. Confirmed green on CI against real artifacts.

### 2. A second launcher icon on debug, one on release - MET (device evidence)

`app/src/debug/AndroidManifest.xml` declares `TuningPanelActivity` with
`android:exported="true"`, `android:label="@string/tuning_panel_marker"` and a
`MAIN`/`LAUNCHER` intent filter. The element exists only in the debug overlay, so a release
manifest merge cannot contain it.

This is the requirement the tree already caught failing in practice: on HyperOS the launcher
groups a package's launcher activities and shows only the main one, so the declared second
entry produced no reachable icon. The fix is `app/src/debug/res/xml/shortcuts_debug.xml` plus
a `tools:node="merge"` `<meta-data>` on `MainActivity` in the debug manifest - a long-press
shortcut whose `targetClass` is `TuningPanelActivity`. Both the XML resource and the strings
it references live only in the debug source set, and `MainActivity` itself is untouched in
`app/src/main`; the merge is declared from the debug overlay. The requirement's letter (two
`MAIN`/`LAUNCHER` entries enumerable for the package) holds; the shortcut is what makes it
reachable on this device.

Evidence class: device observation. No test or CI step enumerates launcher entries.

### 3. Every registered field is enumerated - MET

`BalanceConfig.ALL` has exactly five entries and `PetAnimationConfig.ALL` exactly three;
`TuningPanelViewModel.rowFlows` and `TuningPanelScreen.TUNING_FIELDS` each list those same
eight fields in the same order, and `combine(rowFlows)` emits exactly eight rows.
`TuningRegistryCoverageTest` (2 tests, green) pins that `tuningRowOf` produces one uniquely
keyed row per field over both `ALL` registries and that `NotVersioned` occurs iff
`group.currentVersion == null`.

The four unreachable configs are named nowhere: a search for `PetStateConfig`,
`CharacterLibraryConfig`, `OverlayPositionConfig` and `QuickMenuConfig` under `app/src/debug`
returns nothing, and there is no empty state or placeholder in `TuningPanelScreen.kt`.

See WARNING 2: the eight-field enumeration is two hand-written lists, not a fold over `ALL`.

### 4. Every row shows shipped default, current value, and overridden state, distinctly - MET

`TuningRow` carries `shippedDefault`, `currentValue` and `overridden` as three separate
fields, and `TuningPanelScreen.kt:144` renders them as three distinct labelled values:
`default=... current=... overridden=...`.

The coincidence case is handled at the source rather than at the display:
`TuningRowState.kt:61` sets `overridden = stored is StoredOverride.Present`, so presence of an
entry - never value equality - decides the marker. `TuningRowStateTest` proves the three
cases: absent gives `overridden` false with current equal to the default; present in range
gives `overridden` true with current equal to the stored value; present but out of range falls
back to the shipped default with `overridden` still true.

### 5. Staleness renders as exactly one of three distinct states - MET

`Staleness` is a sealed interface of `Fresh`, `Stale(writtenUnderVersion)` and `NotVersioned`
(`TuningRowState.kt:20-29`). The `when` at line 63 tests `field.group.currentVersion == null`
first, so a not-versioned group can never reach the `Fresh` branch - a structural guarantee,
not a convention. `stalenessLabel` maps the three to three distinct strings: `"fresh"`,
`"stale (was v...)"`, `"not versioned"`.

`TuningRowStateTest` proves fresh, stale-with-version, and that every `PetAnimationConfig`
field renders `NotVersioned` whether overridden or not. Device observation confirms the string
`staleness=not versioned` on the animation rows.

### 6. Every row carries a live/deferred label, rendered even when uniform - MET

`ValueApplication` is computed per row by `applicationFor(field.group.id)`
(`TuningRowState.kt:85`) - by group id, not hardcoded per row - and rendered unconditionally
on every row at `TuningPanelScreen.kt:145`. Both registered groups map to `LIVE` today, so all
eight rows read live, exactly as the spec's second scenario requires.

This is where the third device defect was found and fixed: `LIVE` was true about the flow and
false about the screen for `frameIntervalMillis`, because `AnimationPacing` prefers the active
character's declared frame duration. `TuningPanelViewModel.declaredFrameDurationMillis` reads
that declared value, and `TuningPanelScreen.kt:152-159` renders a note on that row naming the
winning value in milliseconds and stating that `minFrameIntervalMillis` still applies as a
floor. The label can no longer imply a live change that did not happen - the failure the
proposal named as the worst this panel can produce.

### 7. A write goes through the store and is subject to its declared range - MET

A search for `store.set`, `store.reset`, `dataStore` and `edit(` across `app/src/debug`
returns exactly three lines, all in `TuningPanelViewModel.kt`: `store.set` (line 91),
`store.reset` (95) and `store.reset` (117). There is no second write path and no raw
persistence anywhere in the debug source set. `TuningPanelViewModel.set` is a one-line
delegation with no validation of its own; range enforcement stays entirely inside
`ConfigOverrideStore`.

`TuningRejectionCopy.parseTypedValue` decides only shape, never range, and is documented as
such. `rejectionMessage` builds its wording from `rejection.key`, `.min`, `.max` and
`.offending` - the store's typed data, with no display copy borrowed from `:core:domain`.
`TuningRejectionCopyTest` (6 tests) covers blank, `"abc"`, `"1e9"` into an `IntField`, `"0.6"`
into a `DoubleField`, and the rejection wording.

The kept-value-on-rejection rule is real code, not intent: in the `Set` handler
(`TuningPanelScreen.kt:176-191`) `text` is reassigned only in the `Accepted` branch; the
`OutOfRange` and `Unparseable` branches set `rejection` and leave `text` untouched, and the
row's `remember(row.key)` key does not change on a rejected write, so no recomposition
restores the stored value. `TuningPanelWriteReproTest` drives the real Compose surface and
proves the store received exactly the typed values and that the rejection node exists after
5000 is refused, with the previously accepted 2000 still held.

The "does not touch a pre-existing override" scenario is proven at the store's boundary rather
than the panel's: the panel calls `set` once and does nothing on rejection, and the recording
store in the repro test shows its accepted map unchanged after the refusal.

### 8. Per-field reset deletes the entry without confirmation - MET

The per-field `Reset` button (`TuningPanelScreen.kt:192-199`) calls `viewModel.reset(field)`
directly with no dialog anywhere on that path; the only `AlertDialog` in the file is the
reset-all one. `TuningPanelViewModel.reset` calls `store.reset(field)` and nothing else.
Deletion rather than rewrite is the store's behaviour and is visible in
`PreferencesConfigOverrideStore.kt:57-58`, which calls `preferences.remove` for both the value
key and the version key. Device observation confirms the store dropped to 33 bytes with no
`config_override` key after reset-all.

### 9. Reset-all deletes every entry and requires confirmation first - MET

`Reset all` sets `showResetAllConfirm = true` and performs no write. The `AlertDialog`'s
`confirmButton` is the only caller of `viewModel.resetAll()`; both the `dismissButton` and
`onDismissRequest` only clear the flag, so declining changes nothing. `resetAll` iterates all
eight fields through `resetIfPresent`, which resets only fields currently holding a
`StoredOverride.Present` - deletion, never a rewrite.

### 10. A live field's change is visible on the running overlay without leaving the panel - MET

Structurally: the panel writes to `ConfigOverrideStore`, and `BalanceConfigSource` and
`PetAnimationConfigSource` resolve from that same store as observable flows, so there is one
resolution path and no restart is involved. Verified by direct observation on the device - a
config change reached a running overlay with nothing restarted. This is the criterion issue
91 had left satisfied only by inference; it is now satisfied by observation.

### 11. The panel can restart the overlay service - MET (device evidence)

`TuningPanelViewModel.restartOverlay` (lines 124-127) is
`stopService(Intent(appContext, PetOverlayService::class.java))` then `startService` with an
identically shaped `Intent`. That matches `MainActivity.startOverlayService()` exactly -
`startService(Intent(this, PetOverlayService::class.java))`, no extras. No new method appears
on `PetOverlayService`; `feature/overlay` main sources are untouched in the whole diff.

The second scenario ("a restarted overlay picks up deferred changes") is vacuous today: every
registered field is `LIVE`, so no deferred field exists to test. Recorded, not failed.

## Design coherence

| Design decision | State |
|---|---|
| 1 / 1a two tokens, two-sided CI check | Implemented exactly, including the glob and the "exactly one APK" assertion |
| 1b tokens pinned in code | `TuningPanelMarkerTest` asserts the package prefix, the marker literal, and both tokens in `ci.yml` |
| 2 row state is a pure function | `TuningRowState.kt` imports only `ConfigField`, `StoredOverride`, `resolve` - no Android, no Compose, no coroutines |
| 3 current value via `store.override` then `resolve` | Exactly that; `tuningRowOf` calls `resolve` and never re-derives |
| 3a sources injected as an independent witness | Both `*ConfigSource` flows rendered as `in use - ...` lines |
| 4 rejection wording is pure and typed | `TuningRejectionCopy.kt`, no Android import |
| 5 restart via stop/start, no service method | Matches |
| 6 source-set layout, tests in `src/test` | Matches the design's correction section; no `src/testDebug` exists |
| CI step shape | Deviates: `assembleRelease` is a separate step, not appended (WARNING 1) |
| "No Compose UI test" | Deviates, favourably: `TuningPanelWriteReproTest` was added (see below) |

## Post-apply additions

Four changes landed after `apply-progress.md` was written, all from device testing:

1. The HyperOS launcher shortcut (requirement 2 was satisfied in the manifest and unreachable
   on the device).
2. `Modifier.weight(1f)` on the row's text field (`TuningPanelScreen.kt:171`). Without it the
   text field claimed its preferred width and pushed `Reset` past the right edge, where it
   could not be tapped - requirement 8 satisfied in code and unreachable in practice. The
   comment above it records why the weight is load-bearing rather than cosmetic.
3. The `frameIntervalMillis` live note (requirement 6).
4. `TuningPanelWriteReproTest`, which drives the real panel through Compose and asserts what
   reaches the store - the layer the design had deliberately left untested.

Two of those three defects were "satisfied in code, unreachable in practice". That is the
class this verification was asked to hunt for; the WARNINGs below are the closest analogues
still standing.

## Issues

### CRITICAL

None.

### WARNING

1. **`assembleRelease` runs as its own CI step, not appended to the existing invocation.**
   Task 1.16 and the design both specify appending it to the existing Gradle line for a single
   daemon session. The implementation splits it, with an in-file comment explaining the
   diagnostic reason (D8 reporting `Compilation failed to complete` with no indication of
   which variant produced it). The inspection step still runs immediately after, still has no
   `if: always()`, and CI is green, so no spec requirement is affected. A recorded design
   deviation, not a defect.

2. **The panel's eight fields are two hand-written parallel lists, not a fold over `ALL`.**
   `TuningPanelViewModel.rowFlows` and `TuningPanelScreen.TUNING_FIELDS` each enumerate the
   eight fields independently, and `TuningPanelScreen.kt:95` pairs them by index with an
   unchecked cast. They agree today - checked against `BalanceConfig.ALL` and
   `PetAnimationConfig.ALL`, same members, same order - and `TuningRegistryCoverageTest` guards
   `tuningRowOf`, not these lists. Two consequences: a field added to either `ALL` later will
   not appear in the panel while requirement 3 says it must, and reordering one list without
   the other silently writes a typed value to the wrong field. The second is the failure mode
   the device already produced once, and `TuningPanelWriteReproTest` covers only one of the
   eight rows. Suggested follow-up: derive both lists from
   `BalanceConfig.ALL + PetAnimationConfig.ALL`, or add a test asserting
   `TUNING_FIELDS == BalanceConfig.ALL + PetAnimationConfig.ALL`.

3. **`apply-progress.md` no longer describes the tree.** It states the CI change as
   "`assembleRelease` appended to the existing Gradle invocation", reports 809 insertions, and
   lists neither the shortcut resource nor `TuningPanelWriteReproTest`. The actual diff is
   1,093 changed code lines across 16 code files. It is a historical apply record, so this is
   drift rather than error, but it must not be read as the current state.

### SUGGESTION

4. A row's text field is seeded by `remember(row.key) { mutableStateOf(row.currentValue) }`, so
   it does not follow the store if the value changes from outside the panel while it is open.
   Correct for the kept-value-on-rejection rule and harmless on a single-writer instrument;
   worth knowing if a second writer ever exists.

5. The per-field `Reset` handler sets `text = row.shippedDefault` optimistically, before the
   `viewModelScope` reset coroutine completes. The store's own emission corrects it
   immediately, so this is cosmetic only.

## Accepted deviation - review budget

The slice is ~1,093 changed code lines against an 850-line budget. Recorded as an accepted
deviation, not a violation: the maintainer accepted the overrun on the grounds that the
additions are fixes to defects this same PR introduced, and that splitting them would mean
merging known defects.

## Verdict

**PASS WITH WARNINGS.** All eleven spec requirements are met. Nothing blocks archive: the
three WARNINGs are one recorded design deviation, one latent-fragility follow-up, and stale
documentation of a completed phase.
