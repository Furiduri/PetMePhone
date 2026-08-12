# Apply Progress: Slice 3 part A — A pet you can feed, the domain half

## Scope of this batch

Phase 1 / PR 1 only (issue #29): tasks 1.1–1.8. Phases 2–4 are untouched.

## Mode

Standard (no strict TDD configured for this project). Tests were written alongside each
production unit, per the Work Unit Evidence table below.

## Completed Tasks

- [x] 1.1 `core/domain/.../balance/BalanceConfig.kt` — `dailyTaskGoal=10`,
      `hungryThresholdRatio=0.6`, `recurringHungerRatio=3`, `recurringHungerCap=4`,
      `standardTaskPoints=1`, `version=1`, each field KDoc'd with raise/lower effect.
- [x] 1.2 `core/domain/.../balance/MetricRounding.kt` — `percentOf(value, goal)`, floor, clamp,
      `goal > 0` precondition.
- [x] 1.3 `@Provides fun provideBalanceConfig(): BalanceConfig` added to
      `core/data/.../di/DataModule.kt`.
- [x] 1.4 `BalanceConfigTest` — defaults, `hungryThresholdRatio == 0.6`, exactly one
      daily-task-goal field.
- [x] 1.5 `MetricRoundingTest` — `percentOf` at 7 and 12 (plus 9, negative, and `goal == 0`
      precondition).
- [x] 1.6 `ScoringUnderTwoConfigsTest` — same manual-task input scored under `dailyTaskGoal = 10`
      and `= 20`, asserting the results differ.
- [x] 1.7 `NoBalanceLiteralOutsideConfigTest` — greps every `.kt` file in the repo (excluding
      `BalanceConfig.kt` itself and test sourcesets) for the `recurringHungerRatio`/
      `recurringHungerCap` literals.
- [x] 1.8 `BalanceConfigInjectionTest` (`:core:data`) — Robolectric-hosted Hilt `SingletonComponent`
      resolves `BalanceConfig` through an `@EntryPoint`, proving no manual construction at any
      call site.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../balance/BalanceConfig.kt` | Created | Value object; narrower field set than #29's snippet per design decision 5 |
| `core/domain/.../balance/MetricRounding.kt` | Created | `percentOf`, floor/clamp convention |
| `core/domain/.../balance/BalanceConfigTest.kt` | Created | Defaults, ratio, single-goal-field tests |
| `core/domain/.../balance/MetricRoundingTest.kt` | Created | Floor/clamp/precondition tests |
| `core/domain/.../balance/ScoringUnderTwoConfigsTest.kt` | Created | Proves genuine parameterisation |
| `core/domain/.../balance/NoBalanceLiteralOutsideConfigTest.kt` | Created | Repo-wide grep for leaked balance literals |
| `core/data/.../di/DataModule.kt` | Modified | Added `@Provides fun provideBalanceConfig()` |
| `core/data/build.gradle.kts` | Modified | Added `hilt-android-testing` / `kspTest(hilt-android-compiler)` for the DI graph test |
| `core/data/.../di/BalanceConfigInjectionTest.kt` | Created | Hilt entry-point DI graph test |

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :core:domain:test :core:data:testDebugUnitTest --rerun-tasks` — all new tests pass: `BalanceConfigTest` (3/3), `MetricRoundingTest` (5/5), `ScoringUnderTwoConfigsTest` (1/1), `NoBalanceLiteralOutsideConfigTest` (1/1), `BalanceConfigInjectionTest` (1/1) |
| Runtime harness command/scenario and exact result | N/A — pure JVM domain code plus a Hilt graph-build check under Robolectric; no Android runtime UI surface in this slice |
| Rollback boundary | `git revert` the two work-unit commits (`feat(domain): add BalanceConfig and MetricRounding`, `feat(data): provide BalanceConfig through Hilt's DataModule`) reverts PR 1 alone; nothing else in the repo depends on `BalanceConfig` yet |

## Full CI Gate

Ran the exact gate command from `.github/workflows/ci.yml:77`:

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 4m 24s`, `476 actionable tasks: 476 executed` (forced with
`--rerun-tasks`, so nothing was reported `UP-TO-DATE`; every task, including `lintDebug` and
`:core:domain:test`, actually ran).

## Deviations from Design

None — implementation matches design.md's decision 5 field set and decision 7 rounding rule.

One implementation choice not spelled out in design.md: task 1.8's "compile-time/DI graph test"
was built as a Robolectric-hosted `@HiltAndroidTest` in `:core:data` with an `@EntryPoint`
resolving `BalanceConfig`, mirroring the existing `PetOverlayServiceTest` pattern in
`:feature:overlay`. This required adding `hilt-android-testing` (`testImplementation`) and
`kspTest(hilt-android-compiler)` to `core/data/build.gradle.kts` — both already existed in the
version catalog, so no new catalog entries were needed.

## Issues Found

None.

## Remaining Tasks

- [ ] Phase 2 (PR 2, #23) — `AppClock`, Task/TaskOccurrence schema, DAOs, converters, mappers,
      repository, room-testing.
- [ ] Phase 3 (PR 3, #26) — `TaskTitle`, `CreateTaskResult`, `CreateOneOffTask`.
- [ ] Phase 4 (PR 4, #33) — `calculateHunger`/`isHungry`/`isHungerPriority`, full table.

## Workload / PR Boundary

- Mode: chained PR slice (`auto-chain`, `stacked-to-main`)
- Current work unit: Unit 1 — `BalanceConfig`, `MetricRounding`, Hilt provider (#29), PR 1
- Boundary: starts from an empty `balance/` package, ends with `BalanceConfig` fully injectable
  and grep-guarded; PR 2 depends on nothing from this PR
- Estimated review budget impact: ~291 changed lines (well under the 400-line PR budget; design.md
  estimated ~180 for production-only, tests bring it to ~291)

## Status

8/8 tasks in Phase 1 complete. Ready for next batch (Phase 2) or `sdd-verify` on this slice.
