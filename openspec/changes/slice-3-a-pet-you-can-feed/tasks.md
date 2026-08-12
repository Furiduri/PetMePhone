# Tasks: Slice 3 part A — A pet you can feed, the domain half

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1130 total (180 + 460 + 260 + 230), code-only |
| 400-line budget risk | High (chain total); each slice individually Low-Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (#29) → PR 2 (#23) → PR 3 (#26) → PR 4 (#33) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Note: `design.md`'s PR-boundaries section names `feature-branch-chain` (each PR targeting its
predecessor). The orchestrator's cached session decision is `stacked-to-main`, matching this
repo's actual history (slice 1's four PRs and slice 2's one PR all targeted `master`). This
breakdown follows the cached `stacked-to-main` decision; each PR below targets `master` directly
and merges before the next starts, so `TaskRepository`/`AppClock` etc. are real on `master` by the
time the following PR opens.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `BalanceConfig`, `MetricRounding`, Hilt provider (#29) | PR 1 | `:core:domain:test`, `:core:data:testDebugUnitTest` | N/A — pure JVM + Hilt graph build only | Revert PR 1 alone; nothing else depends on it yet |
| 2 | `AppClock`, Task/TaskOccurrence schema, DAOs, converters, mappers, repository, room-testing (#23) | PR 2 | `:core:data:testDebugUnitTest` (Robolectric) | N/A — Room in-memory DB test harness | Revert PR 2; also strands PR 3/4, revert those first if merged |
| 3 | `TaskTitle`, `CreateTaskResult`, `CreateOneOffTask` (#26) | PR 3 | `:core:domain:test` | N/A — pure JVM use-case test | Revert PR 3 alone; PR 4 depends on it |
| 4 | `calculateHunger`/`isHungry`/`isHungerPriority`, full table (#33) | PR 4 | `:core:domain:test` | N/A — pure JVM parameterised test | Revert PR 4 alone; no other slice depends on it |

## Phase 1: PR 1 — Balance configuration (#29)

- [x] 1.1 Create `core/domain/.../balance/BalanceConfig.kt`: `dailyTaskGoal=10`, `hungryThresholdRatio=0.6`, `recurringHungerRatio=3`, `recurringHungerCap=4`, `standardTaskPoints=1`, `version=1`, each field KDoc'd with raise/lower effect. Satisfies `balance-configuration`.
- [x] 1.2 Create `core/domain/.../balance/MetricRounding.kt`: `percentOf(value, goal)` — floor, clamp, `goal > 0` precondition.
- [x] 1.3 Add `@Provides fun provideBalanceConfig(): BalanceConfig` to `core/data/.../di/DataModule.kt`.
- [x] 1.4 Write `BalanceConfigTest`: defaults equal PRD values; `hungryThresholdRatio == 0.6`; exactly one field represents the daily task goal.
- [x] 1.5 Write `MetricRoundingTest`: `percentOf` at 7 and 12 against a goal, asserting floor behavior.
- [x] 1.6 Write a scoring test that runs the same scenario under `BalanceConfig(dailyTaskGoal=10)` and `dailyTaskGoal=20`, asserting the results differ.
- [x] 1.7 Add a source-scan/grep test (or documented manual check) asserting no numeric literal from `BalanceConfig`'s defaults appears outside `BalanceConfig.kt` and its tests.
- [x] 1.8 Verify `BalanceConfig` resolves through Hilt injection with no manual construction at a call site (compile-time/DI graph test).

## Phase 2: PR 2 — Task schema and persistence (#23)

- [ ] 2.1 Create `core/domain/.../time/AppClock.kt`: `now(): Instant`, `zone(): ZoneId`, `today(): LocalDate` default via `now().atZone(zone()).toLocalDate()`. Satisfies `domain-time`.
- [ ] 2.2 Create `core/data/.../time/SystemAppClock.kt` wrapping injected `java.time.Clock`; add `@Provides fun provideSystemClock(): Clock = Clock.systemDefaultZone()` to `DataModule`; `@Binds AppClock` in `BindingsModule`.
- [ ] 2.3 Create domain models `core/domain/.../task/{TaskId,TaskTitle,Task,TaskOccurrence,TaskRepository}.kt` per design's interfaces block (models only; `TaskTitle`/`CreateOneOffTask` validation lands in Phase 3).
- [ ] 2.4 Create `core/data/.../local/task/TaskEntity.kt` and `TaskOccurrenceEntity.kt`: nullable `rrule`, `createdAt: Instant`, `createdDate: LocalDate`, `isActive`; FK cascade, unique index on `(taskId, dueDate)`.
- [ ] 2.5 Create `core/data/.../local/RoomTypeConverters.kt`: `LocalDate ↔ ISO String`, `Instant ↔ Long`.
- [ ] 2.6 Create `core/data/.../local/task/TaskDao.kt` and `TaskOccurrenceDao.kt`: no `@Update`/`@Upsert` on `TaskDao`; narrow column-naming update queries only; `@Insert(onConflict = ABORT)`.
- [ ] 2.7 Create `core/data/.../local/task/TaskMappers.kt`: `internal` extension functions mapping entity ↔ domain model.
- [ ] 2.8 Create `core/data/.../repository/TaskRepositoryImpl.kt`: `createOneOff`, `countManuallyCreatedOn`, `countRecurringScheduledOn` (returns 0), `occurrencesDueOn` (Flow).
- [ ] 2.9 Modify `core/data/.../local/AppDatabase.kt`: version 1→2, entity list adds `TaskEntity`/`TaskOccurrenceEntity`, removes `PlaceholderEntity`; register converters; keep `fallbackToDestructiveMigration(dropAllTables = true)` with a tracked-removal comment referencing the open GitHub issue from task 2.16.
- [ ] 2.10 Delete `core/data/.../local/PlaceholderEntity.kt` and `PlaceholderDao.kt`.
- [ ] 2.11 Remove `PlaceholderDao` binding/provider from `DataModule.kt`/`BindingsModule.kt`; add `@Binds TaskRepository` in `BindingsModule.kt`.
- [ ] 2.12 Add `androidx-room-testing` to `gradle/libs.versions.toml` under the existing `room` version ref.
- [ ] 2.13 Modify `build-logic/.../AndroidRoomConventionPlugin.kt`: add `testImplementation(libs.room.testing)` and set the `room.schemaDirectory` test system property; no literal outside build-logic.
- [ ] 2.14 Run the schema export task and commit the generated `core/data/schemas/.../2.json`.
- [ ] 2.15 Create a `MigrationTestHelper`-based test class in `:core:data` using the `room.schemaDirectory` property (exercises no real migration yet, per spec).
- [ ] 2.16 Open a GitHub issue tracking removal of `fallbackToDestructiveMigration()` before release (verified none exists today); reference it in the AppDatabase comment from task 2.9 and in the PR description.
- [ ] 2.17 RED test: source-scan asserting `TaskDao` declares no `@Update`/`@Upsert` and no `@Query` whose SQL assigns `createdDate`.
- [ ] 2.18 GREEN: confirm 2.17 passes given `TaskDao`'s shape from task 2.6.
- [ ] 2.19 Robolectric test: editing a task's title leaves `createdDate` unchanged.
- [ ] 2.20 Robolectric test: created-today count is correct across a 23:59→00:01 boundary (no zone drift).
- [ ] 2.21 Robolectric test: duplicate `(taskId, dueDate)` insert is rejected; deleting a `Task` cascades its `TaskOccurrence` rows; two concurrent inserts both land.
- [ ] 2.22 Robolectric test: generated/recurring occurrences never move the manual count (`countManuallyCreatedOn` filters by `createdDate`, not `dueDate`).
- [ ] 2.23 Source-scan test: no balance literal appears inside any `@Query`/SQL string in `:core:data`.

## Phase 3: PR 3 — Task creation use case (#26)

- [ ] 3.1 Create `core/domain/.../task/TaskTitle.kt`: `@JvmInline value class` with private constructor, `MAX_LENGTH = 200` domain validation constant (not `BalanceConfig`), `of(raw): TaskTitleResult` — trims, rejects blank.
- [ ] 3.2 Create `core/domain/.../task/CreateTaskResult.kt`: `Created(id, hungerCapReached)`, `Rejected.BlankTitle`, `Rejected.TitleTooLong(length, maxLength)`.
- [ ] 3.3 Create `core/domain/.../task/CreateOneOffTask.kt`: `operator suspend fun invoke(rawTitle: String)`, KDoc documenting the application/service-scoped `CoroutineScope` requirement; uses `clock.now()`/`clock.today()` as the only "today" in the write path.
- [ ] 3.4 Add `@Provides fun provideCreateOneOffTask(...)` to `DataModule.kt` (not `@Inject`-annotated, per design decision).
- [ ] 3.5 Write `TaskTitleTest`: blank/whitespace-only rejected; leading/trailing whitespace trimmed and accepted; 199/200/201-character boundary (199 and 200 succeed, 201 rejected).
- [ ] 3.6 Write `CreateOneOffTaskTest`: duplicate titles both succeed; a valid title writes both a `Task` (`createdDate = today`) and its `TaskOccurrence` (`dueDate = today`) via a fake `TaskRepository`.
- [ ] 3.7 Write `CreateOneOffTaskTest`: eleventh task of the day (ten already created under `dailyTaskGoal = 10`) still creates successfully and reports the cap-reached signal.
- [ ] 3.8 Write `CreateOneOffTaskTest`: a simulated persistence failure returns a typed failure result, not a thrown exception.

## Phase 4: PR 4 — Hunger metric (#33)

- [ ] 4.1 Create `core/domain/.../balance/Hunger.kt`: `calculateHunger(manual, recurring, config)`, `isHungry(...)`, `isHungerPriority(...)` per the design's interfaces block; pure, no Room/Android import.
- [ ] 4.2 Parameterised test: same fixed inputs produce the same result on repeated calls (no I/O).
- [ ] 4.3 Parameterised test: all eleven corrected #33 table rows, including the tier-2 exclusive boundary at exactly 60% and a row where `isHungry` is true while `isHungerPriority` is false.
- [ ] 4.4 Test: 30 recurring occurrences at `recurringHungerRatio=3`/`recurringHungerCap=4` never exceed the cap (contribution == 4), named test.
- [ ] 4.5 Test: below-ratio recurring count (2 occurrences, ratio 3) rounds down to 0; zero recurring occurrences contributes 0.
- [ ] 4.6 Test: overshoot clamps to exactly 100, never above and never errors; manual=8/recurring=30/goal=10 example clamps to 100%.
- [ ] 4.7 Test: completing a task/occurrence does not change Hunger; a carried-over occurrence contributes 0 to either term.
- [ ] 4.8 Test: `isHungry` true at 80% (8/10), false at exactly 100%, false when clamped over 100%.
- [ ] 4.9 Test: `isHungerPriority` — 60% exclusive (hungry, not priority), 70% (hungry, not priority), 50% (both true), goal=12 with ratio-derived cutoff of 7.2 (7 tasks → priority true), 100%/clamped-over-100% (neither true).
- [ ] 4.10 Confirm (no production code needed) that neither `isHungry` nor `isHungerPriority` is referenced by any `PetSnapshot` field or `PetStateProvider` — assert via inspection/comment, not a new provider.
- [ ] 4.11 Write back to GitHub issue #29 noting `BalanceConfig` ships a narrower field set than its snippet (no Energy/Happiness fields), per design decision 5.
- [ ] 4.12 Write back to GitHub issue #70 noting slice 2's decision 16 (sprite bindings DataStore→Room) is NOT fulfilled by part A and is resolved explicitly against #70's widened applicable-state work.
