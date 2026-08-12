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

**Not started**: PR 2 (spike), PR 3+ (card work). Out of scope for this run per explicit
instruction.

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
