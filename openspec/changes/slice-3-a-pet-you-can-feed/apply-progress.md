# Apply Progress: Slice 3 part A — A pet you can feed, the domain half

## Scope of this batch

Phase 2 / PR 2 (issue #23): tasks 2.1–2.23. Phase 1 (PR 1, #29) was completed and recorded in a
prior batch and is preserved below unmodified. Phases 3–4 are untouched.

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

## Files Changed (this batch)

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

## Issues Found

None beyond the deviations above, which are all resolved in this batch.

## Remaining Tasks

- [ ] Phase 3 (PR 3, #26) — `TaskTitle` validation, `CreateTaskResult`, `CreateOneOffTask`.
- [ ] Phase 4 (PR 4, #33) — `calculateHunger`/`isHungry`/`isHungerPriority`, full table.

## Workload / PR Boundary

- Mode: chained PR slice (`auto-chain`, `stacked-to-main`)
- Current work unit: Unit 2 — `AppClock`, Task/TaskOccurrence schema, DAOs, room-testing (#23), PR 2
- Boundary: starts from PR 1's merged `BalanceConfig`/`MetricRounding`; ends with a fully
  Room-backed `TaskRepository` injectable through Hilt, `AppDatabase` at version 2, and
  `PlaceholderEntity` retired. PR 3 depends on this PR's `Task`/`TaskTitle`/`TaskRepository` types.
- Estimated review budget impact: ~956 changed lines total on this branch vs. PR 1's branch
  (design estimated ~460 for production-only; tests, the committed schema JSON, and the DI wiring
  bring the raw diff higher — still one deliverable, reviewable PR per the chain's own boundary)

## Status

23/23 tasks in Phase 2 complete (31/31 across Phases 1–2). Ready for `sdd-apply` on Phase 3 (PR 3,
#26) or `sdd-verify` on this slice.
