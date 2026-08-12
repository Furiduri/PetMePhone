# Apply Progress: Slice 3 part A — A pet you can feed, the domain half

## Scope of this batch

Phase 4 / PR 4 (issue #33): tasks 4.1–4.12. Phases 1–3 (PR 1 #29, PR 2 #23, PR 3 #26) were
completed and recorded in prior batches and are preserved below unmodified. This is the last
phase of the change.

## Mode

Standard (no strict TDD configured for this project). Tests were written alongside each
production unit, per the Work Unit Evidence table below.

## Completed Tasks — Phase 1 (PR 1, #29) — from prior batch

- [x] 1.1–1.8 — `BalanceConfig`, `MetricRounding`, Hilt provider, and their tests. See git history
      on `feat/slice-3-balance-configuration` for the commits; unchanged by this batch.

## Completed Tasks — Phase 2 (PR 2, #23) — this batch

- [x] 2.1 `core/domain/.../time/AppClock.kt` — `now(): Instant`, `zone(): ZoneId`, `today():
      LocalDate` default via `now().atZone(zone()).toLocalDate()`.
- [x] 2.2 `core/data/.../time/SystemAppClock.kt` wrapping injected `java.time.Clock`;
      `provideSystemClock()` in `DataModule`; `@Binds AppClock` in `BindingsModule`.
- [x] 2.3 Domain models `TaskId`, `TaskTitle` (model-only shape; validation lands in Phase 3),
      `Task`, `TaskOccurrence`, `TaskRepository`.
- [x] 2.4 `TaskEntity`/`TaskOccurrenceEntity` — nullable `rrule`, `createdAt: Instant`,
      `createdDate: LocalDate`, `isActive`; FK cascade; unique `(taskId, dueDate)` index.
- [x] 2.5 `RoomTypeConverters` — `LocalDate ↔ ISO String`, `Instant ↔ Long`.
- [x] 2.6 `TaskDao`/`TaskOccurrenceDao` — no `@Update`/`@Upsert` on `TaskDao`; narrow
      column-naming update queries only; `@Insert(onConflict = ABORT)`.
- [x] 2.7 `TaskMappers.kt` — `internal` extension functions, entity ↔ domain model.
- [x] 2.8 `TaskRepositoryImpl` — `createOneOff`, `countManuallyCreatedOn`,
      `countRecurringScheduledOn` (returns 0), `occurrencesDueOn` (Flow).
- [x] 2.9 `AppDatabase` version 1→2; entities `[TaskEntity, TaskOccurrenceEntity]`;
      `PlaceholderEntity` removed; converters registered; `fallbackToDestructiveMigration`
      kept with a tracked-removal comment referencing issue #74.
- [x] 2.10 Deleted `PlaceholderEntity.kt`/`PlaceholderDao.kt`.
- [x] 2.11 Removed the `PlaceholderDao` binding; added `@Binds TaskRepository` in
      `BindingsModule.kt`.
- [x] 2.12 `androidx-room-testing` catalog entry, reusing the existing `room` version ref.
- [x] 2.13 `AndroidRoomConventionPlugin` — `testImplementation(libs.room.testing)`,
      `room.schemaDirectory` system property, and (deviation, see below) the `test` source
      set's `assets.srcDirs` wired to the same schema directory.
- [x] 2.14 Ran the schema export task; committed `core/data/schemas/.../2.json`.
- [x] 2.15 `AppDatabaseMigrationTest` — `MigrationTestHelper`-based, exercises version 2's
      schema loading (no real migration exists yet, per spec).
- [x] 2.16 Opened GitHub issue **#74** tracking removal of `fallbackToDestructiveMigration()`
      before first public release; referenced in `AppDatabase.kt` and `DataModule.kt`.
- [x] 2.17 `TaskDaoNoUpdateOrUpsertTest` — source-scan asserting no `@Update`/`@Upsert` and no
      `createdDate`-assigning `@Query`.
- [x] 2.18 Confirmed 2.17 passes against `TaskDao`'s actual shape.
- [x] 2.19 `TaskRepositoryImplTest` — editing a task's title leaves `createdDate` unchanged.
- [x] 2.20 `TaskRepositoryImplTest` — created-today count is correct across a 23:59→00:01
      boundary with no zone drift.
- [x] 2.21 `TaskRepositoryImplTest` — duplicate `(taskId, dueDate)` rejected; delete cascades;
      two concurrent inserts both land.
- [x] 2.22 `TaskRepositoryImplTest` — generated occurrences never move the manual count.
- [x] 2.23 `NoBalanceLiteralInSqlTest` — no balance literal in any `@Query`/SQL string in
      `:core:data`.

## Completed Tasks — Phase 3 (PR 3, #26) — this batch

- [x] 3.1 `core/domain/.../task/TaskTitle.kt` — `@JvmInline value class` with private constructor,
      `MAX_LENGTH = 200` domain validation constant, `of(raw): TaskTitleResult` trims and rejects
      blank/over-length raw input.
- [x] 3.2 `core/domain/.../task/CreateTaskResult.kt` — `Created(id, hungerCapReached)`,
      `Rejected.BlankTitle`, `Rejected.TitleTooLong(length, maxLength)`, plus (deviation, see
      below) `Rejected.PersistenceFailure`.
- [x] 3.3 `core/domain/.../task/CreateOneOffTask.kt` — `operator suspend fun invoke(rawTitle:
      String)`; `clock.now()`/`clock.today()` as the only "today" in the write path;
      `config.standardTaskPoints` passed into `TaskRepository.createOneOff`, never a literal.
- [x] 3.4 `@Provides fun provideCreateOneOffTask(...)` added to `DataModule.kt`, not
      `@Inject`-annotated on the class, per design decision.
- [x] 3.5 `TaskTitleTest` — blank/whitespace-only rejected; leading/trailing whitespace trimmed
      and accepted; 199/200 succeed, 201 rejected with the measured length.
- [x] 3.6 `CreateOneOffTaskTest` — duplicate titles both succeed; a valid title writes a record
      with `createdDate = today` and `points = config.standardTaskPoints` via a fake
      `TaskRepository`.
- [x] 3.7 `CreateOneOffTaskTest` — the eleventh task of the day under `dailyTaskGoal = 10` still
      creates successfully and reports `hungerCapReached = true`; a task created below the goal
      reports `false`.
- [x] 3.8 `CreateOneOffTaskTest` — a fake repository that throws on `createOneOff` yields
      `CreateTaskResult.Rejected.PersistenceFailure`, never a thrown exception.

## Completed Tasks — Phase 4 (PR 4, #33) — this batch

- [x] 4.1 `core/domain/.../balance/Hunger.kt` — `calculateHunger(manual, recurring, config)`,
      `isHungry(...)`, `isHungerPriority(...)`; pure, no Room/Android import; recurring term
      floored and capped, total clamped to the goal via `MetricRounding.percentOf`.
- [x] 4.2 `HungerTest` — `calculateHunger` called twice with fixed inputs returns the same value.
- [x] 4.3 `HungerTest` — all eleven corrected #33 table rows (`calculateHunger`, `isHungry`,
      `isHungerPriority` asserted together per row), including the exclusive 60% boundary and the
      6-manual/6-recurring/80% row where `isHungry` is true and `isHungerPriority` is false.
- [x] 4.4 `HungerTest` — 30 recurring occurrences at ratio 3 / cap 4 never exceed the cap (named
      test, contribution == 4, Hunger == 40%).
- [x] 4.5 `HungerTest` — 2 recurring occurrences round down to 0; 0 recurring occurrences
      contributes 0.
- [x] 4.6 `HungerTest` — overshoot (20 manual) clamps to exactly 100, no error; manual=8/recurring=30
      clamps to 100%.
- [x] 4.7 `HungerTest` — repeated calls with the same counts are unaffected by completion or
      carry-over, since Hunger takes counts (already filtered by `createdDate`/`dueDate`), never
      entities or completion status.
- [x] 4.8 `HungerTest` — `isHungry` true at 80%, false at exactly 100%, false when clamped over
      100%.
- [x] 4.9 `HungerTest` — `isHungerPriority` at 50% (both tiers true), goal=12 with the
      ratio-derived cutoff of 7.2 (7 tasks → priority true), false at 100% and clamped-over-100%.
- [x] 4.10 `HungerHasNoPetStateWiringTest` — inspects `PetSnapshot`'s actual declared field set and
      asserts no hunger-related field exists; a grep of `:core:domain/src/main` confirms
      `isHungry`/`isHungerPriority` are referenced only inside `Hunger.kt` and `BalanceConfig.kt`'s
      KDoc, called by no provider.
- [x] 4.11 Wrote back to GitHub issue #29: `BalanceConfig` ships a narrower field set (no Energy
      fields, no Happiness-specific fields) than #29's snippet, deferred to the slices that give
      each field a function and a test.
- [x] 4.12 Wrote back to GitHub issue #70: slice 2's decision 16 (sprite bindings DataStore→Room)
      is not fulfilled by part A, and the `PetStateProvider`/`PetStateResolver` one-state-or-null
      shape cannot express tier 2 today — recorded as the same applicable-state-set problem
      tap-to-browse needs, to be solved once.

## Files Changed (this batch)

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../balance/Hunger.kt` | Created | `calculateHunger`, `isHungry`, `isHungerPriority` |
| `core/domain/src/test/.../balance/HungerTest.kt` | Created | Tasks 4.2–4.9, all table rows and named tests |
| `core/domain/src/test/.../balance/HungerHasNoPetStateWiringTest.kt` | Created | Task 4.10 |

## Files Changed (Phase 3 batch)

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../task/TaskTitle.kt` | Modified | Private constructor, `MAX_LENGTH`, `of()` validation, `TaskTitleResult` |
| `core/domain/.../task/CreateTaskResult.kt` | Created | `Created`/`Rejected.BlankTitle`/`Rejected.TitleTooLong`/`Rejected.PersistenceFailure` |
| `core/domain/.../task/CreateOneOffTask.kt` | Created | The use case: validate, read "today", write, report cap-reached |
| `core/data/.../di/DataModule.kt` | Modified | `@Provides fun provideCreateOneOffTask(...)` |
| `core/data/.../local/task/TaskMappers.kt` | Modified | `toDomain()` goes through `TaskTitle.of()`, matching the now-private constructor |
| `core/data/src/test/.../repository/TaskRepositoryImplTest.kt` | Modified | Fixtures go through `TaskTitle.of()` via a local `validTaskTitle()` helper |
| `core/domain/src/test/.../task/TaskTitleTest.kt` | Created | Task 3.5 |
| `core/domain/src/test/.../task/CreateOneOffTaskTest.kt` | Created | Tasks 3.6–3.8, plus valid-title and below-goal coverage |

## Files Changed (Phase 2 batch)

| File | Action | What Was Done |
|------|--------|---------------|
| `gradle/libs.versions.toml` | Modified | `androidx-room-testing` catalog entry |
| `build-logic/.../AndroidRoomConventionPlugin.kt` | Modified | `room-testing` test dependency, schema directory system property, and `test` source set assets wiring |
| `core/domain/.../time/AppClock.kt` | Created | Shared clock/zone port |
| `core/data/.../time/SystemAppClock.kt` | Created | `Clock`-backed `AppClock` implementation |
| `core/domain/.../task/{TaskId,TaskTitle,Task,TaskOccurrence,TaskRepository}.kt` | Created | Domain models and port |
| `core/data/.../local/task/{TaskEntity,TaskOccurrenceEntity}.kt` | Created | Room entities |
| `core/data/.../local/RoomTypeConverters.kt` | Created | `LocalDate`/`Instant` converters |
| `core/data/.../local/task/{TaskDao,TaskOccurrenceDao}.kt` | Created | DAOs, no `@Update`/`@Upsert` on `TaskDao` |
| `core/data/.../local/task/TaskMappers.kt` | Created | Entity ↔ domain mapping |
| `core/data/.../repository/TaskRepositoryImpl.kt` | Created | `@Transaction` create, counts, Flow reads |
| `core/data/.../local/AppDatabase.kt` | Modified | Version 2, new entities, `Placeholder*` removed |
| `core/data/.../local/{PlaceholderEntity,PlaceholderDao}.kt` | Deleted | Retired per declared decision |
| `core/data/.../di/DataModule.kt` | Modified | `Clock` provider, `fallbackToDestructiveMigration` with tracked-removal comment |
| `core/data/.../di/BindingsModule.kt` | Modified | `@Binds TaskRepository`, `@Binds AppClock` |
| `core/data/schemas/.../2.json` | Created | Committed schema export |
| `core/data/src/test/.../local/AppDatabaseMigrationTest.kt` | Created | `MigrationTestHelper` wiring test |
| `core/data/src/test/.../local/task/TaskDaoNoUpdateOrUpsertTest.kt` | Created | Source-scan (task 2.17) |
| `core/data/src/test/.../local/task/NoBalanceLiteralInSqlTest.kt` | Created | Source-scan (task 2.23) |
| `core/data/src/test/.../repository/TaskRepositoryImplTest.kt` | Created | Robolectric in-memory DB (tasks 2.19–2.22) |

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :core:data:testDebugUnitTest :core:domain:test --rerun-tasks` — all tests pass (31 tests in `:core:data`, including the 5 new/updated `AppDatabaseMigrationTest`, `TaskDaoNoUpdateOrUpsertTest` (2), `NoBalanceLiteralInSqlTest`, and `TaskRepositoryImplTest` (4)) |
| Runtime harness command/scenario and exact result | `TaskRepositoryImplTest` and `AppDatabaseMigrationTest` run under Robolectric with a real in-memory/temp-file Room database and `AndroidSQLiteDriver` — the closest available runtime harness for a Room-backed repository with no UI surface in this slice |
| Rollback boundary | `git revert` the 5 work-unit commits on `feat/slice-3-task-schema` (`chore(build): wire room-testing...`, `feat(domain): add AppClock...`, `feat(data): implement AppClock...`, `feat(domain): add Task/TaskOccurrence models...`, `feat(data): add Task/TaskOccurrence Room schema...`) reverts PR 2 alone. PR 3 and PR 4 depend on this PR's `Task`/`TaskOccurrence`/`TaskRepository`/`AppClock` types; reverting stranded PRs first if already merged, per design.md's PR-boundaries note |

## Full CI Gate

### Phase 2 batch

Ran the exact gate command from `.github/workflows/ci.yml:77`:

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 4m 49s`, `476 actionable tasks: 476 executed` (forced with
`--rerun-tasks`, so nothing was reported `UP-TO-DATE`; every task, including `lintDebug` and
`:core:domain:test`, actually ran). One transient run failed first with a Windows file-lock error
on an unrelated `:feature:overlay` build artifact (`bundleLibCompileToJarDebug`); `./gradlew
--stop` to clear stale daemons and a clean rerun produced the green result above — not a code
issue in this change.

### Phase 3 batch

Focused command first: `./gradlew :core:domain:test :core:data:testDebugUnitTest --rerun-tasks` —
`BUILD SUCCESSFUL`; new suites all green (`TaskTitleTest` 6/6, `CreateOneOffTaskTest` 7/7,
`TaskRepositoryImplTest` 4/4, `TaskDaoNoUpdateOrUpsertTest` and `NoBalanceLiteralInSqlTest`
unaffected). One earlier attempt failed with a KSP `PROCESSING_ERROR` off a stale configuration
cache entry; a clean rerun (no source change) succeeded, so this was not a code issue.

Then the exact full gate command:

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 2m 56s`, `472 actionable tasks: 472 executed` (forced with
`--rerun-tasks`; nothing `UP-TO-DATE`, `lintDebug` and `:core:domain:test` both actually ran).

### Phase 4 batch

Focused command first: `./gradlew :core:domain:test --rerun-tasks --stacktrace` — `BUILD
SUCCESSFUL`; `HungerTest` and `HungerHasNoPetStateWiringTest` both green.

Then the exact full gate command from `.github/workflows/ci.yml:77`, with `--stop` and a clean
rerun ahead of it (this change's Windows daemon-lock caveat, not a code issue):

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 3m 50s`, `472 actionable tasks: 472 executed` (forced with
`--rerun-tasks`; nothing `UP-TO-DATE`, `lintDebug` and `:core:domain:test` both actually ran).

Both GitHub write-backs confirmed posted:
`https://github.com/Furiduri/PetMePhone/issues/29#issuecomment-5262851621` and
`https://github.com/Furiduri/PetMePhone/issues/70#issuecomment-5262851807`.

## Deviations from Design

1. **`room.schemaDirectory` system property was insufficient; test-source-set `assets` wiring was
   added.** Design decision 11 assumed `MigrationTestHelper`'s driver-based `File` constructor
   argument would be read directly off the filesystem. Confirmed against Room 2.8.4's actual
   bytecode (decompiled with `javap`) that this is not the case: `MigrationTestHelper`'s Android
   target always loads schema JSON via `Instrumentation`'s `Context.getAssets()`, keyed by the
   database class's qualified name, regardless of which constructor overload is used. The `file`
   constructor argument is instead the *target database file* the helper opens (a directory there
   throws `SQLITE_CANTOPEN_ISDIR`). Fixed by also wiring the `test` source set's `assets.srcDirs`
   to the schema directory in `AndroidRoomConventionPlugin` (the standard Room migration-test
   setup) and passing a real cache file as the `file` argument. The `room.schemaDirectory` system
   property is kept (harmless, and literally satisfies task 2.13's wording) but is not what makes
   the migration test pass. Design.md's decision 11 should be corrected to reflect this on the
   next design touch to this file.
2. **`TaskRepository.createOneOff` gained a `points: Int` parameter not in design's literal
   interfaces block.** The block's data-flow diagram says the transaction inserts
   `TaskOccurrence(dueDate = today, points = config.standardTaskPoints)`, but the listed method
   signature has no `points` parameter — an internal inconsistency in the design. Hardcoding
   `points = 1` in the repository would violate the "balance values are never literals" rule
   (`balance-configuration` spec) and silently disconnect `standardTaskPoints` from ever being
   read. Added `points: Int` as a parameter instead, to be supplied by `CreateOneOffTask` in Phase
   3 from `config.standardTaskPoints`.
3. **`MigrationTestHelper` requires `@Config(sdk = [36])`.** Robolectric 4.16.1's max supported
   SDK is 36; the app's `targetSdk` is 37. Matches the existing pattern already used by
   `BalanceConfigInjectionTest` from Phase 1 — not a new convention, just applied to the two new
   Robolectric-hosted test classes in this batch.
4. Added a narrow `TaskDao.delete(id: Long)` `@Query` (not in design's file-changes list) so the
   cascade-delete test (task 2.21) has a DAO method to call instead of raw SQL. It is a plain
   `DELETE` query, not `@Update`/`@Upsert`, so it does not weaken decision 9's `createdDate`
   immutability guarantee.
5. **`CreateTaskResult.Rejected.PersistenceFailure` added (Phase 3, #26).** Design's literal
   `CreateTaskResult` block lists only `Created`, `Rejected.BlankTitle`, and
   `Rejected.TitleTooLong`, but task 3.8 requires a persistence failure to surface as a typed
   result, never a thrown exception. `CreateOneOffTask` catches the repository write and maps it
   to this new case rather than letting the exception propagate to the caller.

## Issues Found

None. No deviations in Phase 4: `Hunger.kt`'s signature and both booleans match design's literal
interfaces block exactly.

## Remaining Tasks

None. This is the last phase of the change.

## Workload / PR Boundary

- Mode: chained PR slice (`auto-chain`, `stacked-to-main`)
- Current work unit: Unit 4 — `calculateHunger`/`isHungry`/`isHungerPriority` (#33), PR 4
- Boundary: pure functions only, taking `BalanceConfig` (already available since PR 1) and plain
  `Int` counts — no dependency on PR 2's Room schema or PR 3's `CreateOneOffTask` beyond both being
  present on the branch. No other slice depends on this PR; it is the terminal node of the chain.
- Estimated review budget impact: one commit, 2 files created (~230 raw lines including tests) —
  well under the 800-line gate and matching design's ~230-line PR 4 estimate

## Status

43/43 tasks across Phases 1–4 complete (12/12 in Phase 4). This is the last phase of the change.
Ready for `sdd-verify` on this slice.
