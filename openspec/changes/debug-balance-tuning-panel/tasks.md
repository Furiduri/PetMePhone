# Tasks: A balance tuning panel compiled only into the debug variant (#92)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines (code only, docs excluded) | ~600–780 |
| 400-line budget risk | High — every plausible task grouping exceeds 400 on its own |
| 800-line budget risk | Medium — top of the range sits close to 800; little margin for scope creep during `sdd-apply` |
| Chained PRs recommended | No — the total fits inside the 800-line budget as a single slice, unlike #91 |
| Decision needed before apply | Yes — confirm the single-PR shape before `sdd-apply` begins, given how little margin sits under 800; if actual line count runs over during apply, the seam is tasks 1.1–1.6 (pure logic + tests, no Android) ahead of 1.7–1.19 (Compose surface, manifest, CI) |
| PR boundary | None planned. If a split becomes necessary: PR 1 = tasks 1.1–1.6 (`TuningRowState.kt`, `TuningRejectionCopy.kt`, their JVM tests). PR 2 = tasks 1.7–1.19 (ViewModel, Compose screen, activity, manifest, source-scan tests, marker test, CI) |

## Phase 1: the panel, compiled only into `app/src/debug/` (single PR)

- [ ] 1.1 RED: write `TuningRowStateTest` (`app/src/test/java/.../debug/tuning/`) — the row-state
      matrix over `tuningRowOf(field, stored)`: absent → not overridden, current = shipped default;
      present in range → overridden, current = stored value; present out of range →
      `SHIPPED_DEFAULT_RANGE_NARROWED`, current = default, `overridden = true`; present under an
      older `BalanceConfig` version → `Staleness.Stale(v)`; present under the current version →
      `Staleness.Fresh`; every `PetAnimationConfig.ALL` field, overridden or not → `Staleness.NotVersioned`,
      never `Fresh`. (Satisfies spec: "Every row shows shipped default, current value, and overridden
      state, distinctly"; "Staleness renders as exactly one of three distinct states per row".)
- [ ] 1.2 GREEN: create `app/src/debug/java/.../debug/tuning/TuningRowState.kt` — `Staleness` (`Fresh`,
      `Stale(writtenUnderVersion)`, `NotVersioned`), `ValueApplication` (`LIVE`, `NEXT_SERVICE_START`),
      `TuningRow`, `const val TUNING_PANEL_MARKER = "PETMEPHONE_DEBUG_TUNING_PANEL"`, and
      `fun <T : Comparable<T>> tuningRowOf(field: ConfigField<T>, stored: StoredOverride<T>): TuningRow`
      calling `:core:domain`'s `resolve` — it never re-implements resolution. No Android import, no
      Compose import, no coroutines. This is the first file under `com/gcatcode/petmephone/debug/tuning`
      (grep token T1). (Design decisions 1, 2, 3; satisfies the same requirements as 1.1, plus "Every
      row carries a live/deferred label, rendered even when uniform" via `ValueApplication`.)
- [ ] 1.3 RED: write `TuningRegistryCoverageTest` — `tuningRowOf` over `BalanceConfig.ALL` (5) and
      `PetAnimationConfig.ALL` (3) produces exactly eight rows with unique keys, one per field, no
      other field appearing; `Staleness.NotVersioned` occurs if and only if the field's
      `group.currentVersion == null`. (Satisfies spec: "Every registered field is enumerated, including
      any the settings screen excludes"; "A not-versioned row is never reported as fresh".)
- [ ] 1.4 GREEN: confirm 1.3 passes against 1.2's `tuningRowOf`; no production change expected unless
      the registry walk reveals a gap.
- [ ] 1.5 RED: write `TuningRejectionCopyTest` — `parseTypedValue(field, text)` returns `Unparseable`
      for blank, `"abc"`, `"1e9"` into an `IntField`, and `Valid(v)` for a well-formed value including
      `"0.6"` into a `DoubleField`; `rejectionMessage(rejection)` names the field, its declared min and
      max, and the offending value as typed data, using no display copy borrowed from `:core:domain`.
      (Satisfies spec: "A write goes through the store and is subject to its declared range" — the
      typed-rejection-wording scenario.)
- [ ] 1.6 GREEN: create `app/src/debug/java/.../debug/tuning/TuningRejectionCopy.kt` — pure
      `ParsedInput<T>` (`Valid<T>`, `Unparseable`), `parseTypedValue`, `rejectionMessage`,
      `unparseableMessage`. No Android import. (Design decision 4.)
- [ ] 1.7 GREEN: create `app/src/debug/java/.../debug/tuning/TuningPanelViewModel.kt` —
      `@HiltViewModel`, combines `ConfigOverrideStore.override(field)` for every field of
      `BalanceConfig.ALL` + `PetAnimationConfig.ALL` into rows via `tuningRowOf`; exposes `set` and
      `reset` per field routed exclusively through `ConfigOverrideStore.set`/`.reset`; exposes a
      reset-all action that calls `store.reset` for every field currently holding an entry, invoked
      only after the caller confirms; injects `BalanceConfigSource`/`PetAnimationConfigSource` for the
      "in use" readout; exposes a restart action doing `stopService(Intent(ctx, PetOverlayService::class.java))`
      then `startService` with the same `Intent` shape, adding no method to `PetOverlayService`.
      (Design decisions 3, 3a, 5; satisfies spec: "A write goes through the store and is subject to its
      declared range", "Per-field reset deletes the entry without confirmation", "Reset-all deletes
      every entry and requires confirmation first", "A live field's change is visible on the running
      overlay without leaving the panel", "The panel can restart the overlay service".)
- [ ] 1.8 GREEN: create `app/src/debug/java/.../debug/tuning/TuningPanelScreen.kt` — Compose panel
      under `PetMePhoneTheme` with `safeDrawing` insets; renders all eight rows (default, current,
      overridden marker, staleness label, live/deferred label); renders no reference to `PetStateConfig`,
      `CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`; an out-of-range submission
      keeps the typed value on screen beside the expected range instead of reverting; per-field reset
      has no confirmation dialog; reset-all shows a confirm dialog before calling the ViewModel action;
      a restart control triggers the ViewModel's restart action. (Satisfies spec: "Every field the
      settings screen excludes still appears", "The panel names none of the four unreachable configs",
      "An out-of-range write leaves the typed value on screen instead of reverting", "Reset-all asks for
      confirmation before deleting anything".)
- [ ] 1.9 GREEN: create `app/src/debug/java/.../debug/tuning/TuningPanelActivity.kt` —
      `@AndroidEntryPoint ComponentActivity` hosting `TuningPanelScreen`. No `BuildConfig.DEBUG` check
      anywhere in this file or any file under `debug/tuning/`. (Design decision 6; satisfies spec: "The
      panel exists only in the debug build and is verifiably absent from release" — the no-runtime-check
      half.)
- [ ] 1.10 GREEN: create `app/src/debug/res/values/strings.xml` — the marker label string, value equal
      to `TUNING_PANEL_MARKER` (token T2), used as the debug launcher activity's `android:label`.
- [ ] 1.11 GREEN: modify `app/src/debug/AndroidManifest.xml` — add a second `MAIN`/`LAUNCHER`
      `<activity>` for `TuningPanelActivity`, `android:label="@string/..."` from 1.10, beside the
      existing `exported` override. (Satisfies spec: "A second launcher icon exists on a debug install
      and not on a release install".)
- [ ] 1.12 RED: write `TuningPanelNoLeakCodeTest` (`app/src/test/java/.../debug/tuning/`,
      `File.walkTopDown()`, the `ConfigStoreNoBulkWriteCodeTest` shape) — no file under `app/src/main`,
      `core/`, or `feature/` contains the string `debug.tuning` or `TUNING_PANEL_MARKER`. This is the
      in-repo mirror of the CI artifact check.
- [ ] 1.13 GREEN: confirm 1.12 passes against the current tree; no production change expected — this
      is a structural guard against a future leak, not a fix for one that exists today.
- [ ] 1.14 RED: write `TuningPanelNoDebugFlagCodeTest` (same source-scan shape) — `BuildConfig.DEBUG`
      appears nowhere under `app/src/debug/`. (Satisfies spec: "No runtime check gating the panel's
      visibility or behaviour on a debug/release distinction SHALL exist anywhere in this change".)
- [ ] 1.15 GREEN: confirm 1.14 passes against the current tree; no production change expected.
- [ ] 1.16 GREEN: modify `.github/workflows/ci.yml` — append `assembleRelease` to the existing Gradle
      invocation, then add a new step immediately after it, before any upload step, with no
      `if: always()`: extract the whole debug APK and the whole release APK (glob, not a filename, so
      `app-release-unsigned.apk` matches), grep both tokens T1 and T2 in each; fail the job if either
      token is absent from the debug APK (positive control) or present in the release APK. (Design
      decisions 1, 1a; satisfies spec: "A release build's artifact contains no panel code" and "A debug
      build's artifact contains the panel" — both machine-verifiable scenarios.)
- [ ] 1.17 RED: write `TuningPanelMarkerTest` (`app/src/test/java/.../debug/tuning/`) —
      `TuningPanelActivity::class.java.name.startsWith("com.gcatcode.petmephone.debug.tuning.")`; the
      `TUNING_PANEL_MARKER` const equals the exact literal `"PETMEPHONE_DEBUG_TUNING_PANEL"`; reads
      `.github/workflows/ci.yml` from disk and asserts both token literals appear in it. (Design decision
      1b.)
- [ ] 1.18 GREEN: confirm 1.17 passes — it requires 1.2's marker const, 1.9's activity package, and
      1.16's CI edit to all already exist; no further production change expected.
- [ ] 1.19 Run `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest
      lintDebug --stacktrace --rerun-tasks`. `--rerun-tasks` is required whenever this run is cited as
      evidence — an `UP-TO-DATE` report has already produced a false pass in this repo. This is the
      real CI gate command; a partial command is not the gate.

## Not in scope for this task list

No extension of `ConfigOverrideStore`, `ConfigField`, or `ConfigResolution` to `PetStateConfig`,
`CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`. No feature flags, no data
seeding, no metric override. No `BuildConfig.DEBUG` anywhere in this change. No product flavor and no
custom build type. No `src/testDebug` source set — unit tests live in `app/src/test/java`, per the
design's correction section.
