# Apply progress: A balance tuning panel compiled only into the debug variant (#92)

Status: all 19 tasks in `tasks.md` done. Single PR, no split needed.

## Files created

- `app/src/debug/java/com/gcatcode/petmephone/debug/tuning/TuningRowState.kt` — `Staleness`,
  `ValueApplication`, `TuningRow`, `TUNING_PANEL_MARKER` const, `tuningRowOf`.
- `app/src/debug/java/com/gcatcode/petmephone/debug/tuning/TuningRejectionCopy.kt` — `ParsedInput`,
  `parseTypedValue`, `rejectionMessage`, `unparseableMessage`.
- `app/src/debug/java/com/gcatcode/petmephone/debug/tuning/TuningPanelViewModel.kt` — `@HiltViewModel`,
  eight-row `StateFlow`, `set`/`reset`/`resetAll`/`restartOverlay`.
- `app/src/debug/java/com/gcatcode/petmephone/debug/tuning/TuningPanelScreen.kt` — Compose panel,
  `safeDrawing` insets, reset-all confirm dialog, in-use readout.
- `app/src/debug/java/com/gcatcode/petmephone/debug/tuning/TuningPanelActivity.kt` — `@AndroidEntryPoint`
  launcher activity.
- `app/src/debug/res/values/strings.xml` — marker label string (token T2).
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningRowStateTest.kt`
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningRegistryCoverageTest.kt`
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningRejectionCopyTest.kt`
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningPanelNoLeakCodeTest.kt`
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningPanelNoDebugFlagCodeTest.kt`
- `app/src/test/java/com/gcatcode/petmephone/debug/tuning/TuningPanelMarkerTest.kt`

## Files modified

- `app/src/debug/AndroidManifest.xml` — second `MAIN`/`LAUNCHER` `<activity>` for `TuningPanelActivity`.
- `.github/workflows/ci.yml` — `assembleRelease` appended to the existing Gradle invocation, plus a
  new two-sided artifact-inspection step immediately after it.

## Verification

`./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug
--stacktrace --rerun-tasks` — BUILD SUCCESSFUL (one transient `lintAnalyzeDebugUnitTest` failure in
`:feature:overlay` on the first `--rerun-tasks` run, unrelated to this change — a Kotlin-lint
backup-file caching bug against a pre-existing generated Hilt file; the retry succeeded cleanly).

`./gradlew assembleRelease --stacktrace --rerun-tasks` — BUILD SUCCESSFUL. Local two-sided check
against the real artifacts: both tokens present (2 files each) in `app-debug.apk`, both absent (0
files each) from `app-release-unsigned.apk`.

Changed-line count (code only, `git diff --stat master`): 809 insertions / 1 deletion across 14
files (11 new debug/test Kotlin+resource files, `app/src/debug/AndroidManifest.xml`, `.github/workflows/ci.yml`) — inside the 850-line review budget.

## Deviations from design

None. `hiltViewModel()` (androidx.hilt.navigation.compose) is not a dependency of `:app`, so
`TuningPanelViewModel` is obtained via `by viewModels()` in `TuningPanelActivity` (Hilt's
`ComponentActivity` factory) and passed down as an explicit parameter, rather than resolved inside
`TuningPanelScreen` — same DI guarantee, no new dependency added. Not a deviation from any tasks.md
line, just an implementation detail task 1.8/1.9 left open.
