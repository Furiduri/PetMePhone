```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:2ab85205ef63bac56a556845b306ea43b2ed05e92f61c301cd5e3b01ce55fbca
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 32/32
scenarios: 53/53
test_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:ca174e204df7f42db063f8c66d2ef84ce168c8011cb51528d68edfeb94011e7c
build_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:ca174e204df7f42db063f8c66d2ef84ce168c8011cb51528d68edfeb94011e7c
```

# Verification Report: slice-3-a-pet-you-can-feed

**Change**: `slice-3-a-pet-you-can-feed` (part A, the domain half - issues #29, #23, #26, #33)
**Branch verified**: `feat/slice-3-hunger` @ `4f2c8ff` (tip of the four-branch stack; contains all
four phases). Read-only verification: no source file was modified.
**Mode**: Standard (no Strict TDD configured). Full artifact set present - proposal, design,
seven specs, tasks, apply-progress - so completeness, correctness, and design coherence were all
verified.
**Verdict**: **PASS WITH WARNINGS**

## 1. Completeness

| Dimension | Result |
|---|---|
| Tasks checked in `tasks.md` | 43 / 43 (Phase 1: 8, Phase 2: 23, Phase 3: 8, Phase 4: 12) |
| Unchecked tasks | 0 |
| Tasks whose claimed artifact exists on the branch | 43 / 43, confirmed file-by-file |
| `apply-progress.md` matches the code state | Yes, including all five recorded deviations |
| Working tree cleanliness | Only `.idea/deploymentTargetSelector.xml` is dirty - IDE state, unrelated to this change |

Every task's named file was opened and read; no task is marked complete against absent or
placeholder code.

## 2. Build and test evidence

The exact CI gate command from `.github/workflows/ci.yml:77` was run on this branch with
`--rerun-tasks`, so no task could report `UP-TO-DATE`:

```
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
```

```
BUILD SUCCESSFUL in 3m 49s
472 actionable tasks: 472 executed
Configuration cache entry reused.
```

Exit code `0`. `lintDebug` and `:core:domain:test` both actually executed (visible as
`> Task :app:lintDebug` and in the `:core:domain` test-results XML written by this run). No
Windows file-lock or stale-configuration-cache retry was needed on this run.

Per-class results parsed from the XML this run produced - every new suite ran, zero failures,
zero errors, zero skipped:

| Module | Suite | Tests |
|---|---|---|
| `:core:domain` | `HungerTest` | 15 |
| `:core:domain` | `HungerHasNoPetStateWiringTest` | 1 |
| `:core:domain` | `BalanceConfigTest` | 3 |
| `:core:domain` | `MetricRoundingTest` | 5 |
| `:core:domain` | `NoBalanceLiteralOutsideConfigTest` | 1 |
| `:core:domain` | `ScoringUnderTwoConfigsTest` | 1 |
| `:core:domain` | `CreateOneOffTaskTest` | 7 |
| `:core:domain` | `TaskTitleTest` | 6 |
| `:core:data` | `BalanceConfigInjectionTest` | 1 |
| `:core:data` | `AppDatabaseMigrationTest` | 1 |
| `:core:data` | `NoBalanceLiteralInSqlTest` | 1 |
| `:core:data` | `TaskDaoNoUpdateOrUpsertTest` | 2 |
| `:core:data` | `TaskRepositoryImplTest` | 4 |

48 tests introduced by this change, all green. No pre-existing suite regressed.

## 3. The four highest-risk checks

### 3.1 createdDate immutability - VERIFIED STRUCTURALLY

Confirmed by reading the DAOs and the repository implementation, not by trusting the comments:

- A repository-wide search for `@Update`, `@Upsert`, and `OnConflictStrategy.REPLACE` across every
  `src/**/*.kt` returns **no production match**. The only hits are the prose KDoc in `TaskDao.kt`
  and `TaskEntity.kt` and the assertion strings inside `TaskDaoNoUpdateOrUpsertTest`.
- `TaskDao` exposes exactly six methods: `@Insert(onConflict = ABORT)`, two narrow column-naming
  UPDATE queries (`SET title = :title`, `SET isActive = :isActive`), a `COUNT(*)` read, a
  `findById` read, and a `DELETE ... WHERE id = :id`. No statement names `createdDate` on the
  write side.
- `TaskOccurrenceDao` has only `@Insert(onConflict = ABORT)` and one read; it cannot reach
  `TaskEntity` at all.
- `TaskRepository` (the domain port) exposes no whole-object `update(task: Task)`.
  `TaskRepositoryImpl` writes `TaskEntity` in exactly one place - inside the `createOneOff`
  `withTransaction` block.
- `TaskRepositoryImplTest.editing a task's title leaves createdDate unchanged` proves it at runtime
  against a real in-memory Room database.

This is the strongest requirement in the change and it holds. The four structural measures of
design decision 9 are all present in the code.

### 3.2 The two-tier Hunger model - VERIFIED, WITH ONE BOUNDARY-EQUIVALENCE NOTE

`Hunger.kt` implements:

```kotlin
recurringPoints = (recurring / config.recurringHungerRatio).coerceAtMost(config.recurringHungerCap)
calculateHunger = MetricRounding.percentOf(manual + recurringPoints, config.dailyTaskGoal)
isHungry         = calculateHunger(...) < 100
isHungerPriority = calculateHunger(...) < config.hungryThresholdRatio * 100
```

- The recurring term **cannot exceed the cap**: integer division floors, then `coerceAtMost(cap)`
  clamps. 30 / 3 = 10, clamped to 4. Asserted by a named test (thirty recurring occurrences never
  exceed the cap, expecting exactly 40%) and by two table rows.
- `MetricRounding.percentOf` clamps `value` into [0, goal] before flooring, so Hunger can never
  exceed 100 and never errors for over-goal input. `require(goal > 0)` is the only precondition.
- `isHungry` uses `< 100`, i.e. below a fully met goal - independent of `hungryThresholdRatio`, as
  the spec demands.
- `isHungerPriority` compares the **floored percentage** against ratio x 100 rather than comparing
  raw points against ratio x goal. I checked this is mathematically equivalent and not a latent
  boundary bug: points/goal >= 0.6 implies the exact percentage is >= 60, so its floor is also
  >= 60; and points/goal < 0.6 implies the exact percentage is < 60, so its floor is < 60. The
  exclusive boundary is preserved in both directions for every goal. Verified independently by the
  goal = 12 case: percentOf(7,12) = 58 < 60, so priority is true, matching the spec's derived
  cutoff of 7.2.
- **Every row of the corrected table in `specs/hunger-metric/spec.md` is asserted.** The
  `HungerTest` table carries 13 rows (the spec's eleven plus two extras) and asserts
  `calculateHunger`, `isHungry`, and `isHungerPriority` together on each. Specifically present and
  correct: 6/0/10 gives 60%, hungry, **not** priority (the exclusive boundary); 7/0/10 gives 70%,
  hungry, not priority; 6 manual + 6 recurring/10 gives 80%, hungry, not priority; 10/0/10 and
  15/0/10 give 100%, neither; 6/0/12 gives 50%, both; 0/2/10 gives 0% (rounds down); 0/30/10 gives
  40% (capped); 8/30/10 gives 100% (clamped).

### 3.3 Balance values are injected, never literals - VERIFIED BY INSPECTION (test coverage is weak, see 5.2)

- Every balance number lives in `BalanceConfig` with raise/lower KDoc on all six fields.
- All three Hunger functions and `CreateOneOffTask` take `BalanceConfig` as a constructor or
  parameter. There is no companion, global, or top-level balance constant anywhere in
  `:core:domain`.
- No `@Query` in `:core:data` contains a balance literal. I read all eight query strings: each
  either has no numeric literal at all or binds a parameter. `NoBalanceLiteralInSqlTest` gives this
  one genuine runtime guard.
- `points` is threaded from `config.standardTaskPoints` through `CreateOneOffTask` into
  `TaskRepository.createOneOff` rather than hardcoded - the accepted design deviation (2), and the
  right call: hardcoding `points = 1` would have violated this spec silently.
- `TaskTitle.MAX_LENGTH = 200` correctly lives on `TaskTitle`, not on `BalanceConfig` - the spec
  explicitly excludes UI/validation caps.

### 3.4 Declared scope boundaries - ALL HOLD

| Boundary | Verdict | Evidence |
|---|---|---|
| No `HUNGRY` pet-state provider | Holds | Only `IdleStateProvider` and `DraggingStateProvider` exist; `PetState.HUNGRY` is a pre-existing enum constant from slice 2 with no provider |
| No `PetSnapshot` hunger field | Holds | `PetSnapshot` is still `data class PetSnapshot(val isDragging: Boolean)` |
| No sprite, no `pet-state-resolution` change | Holds | Nothing under `pet/` or `sprite/` changed on this branch |
| `isHungry`/`isHungerPriority` have no caller | Holds | Both are referenced only by `Hunger.kt` itself and by test/KDoc text |
| No frozen Hunger snapshot, no `balanceVersion` stamping | Holds | `calculateHunger` takes Int counts and returns Int; nothing persists a Hunger value |
| `PlaceholderEntity`/`PlaceholderDao` retired | Holds | Both files deleted; `AppDatabase` entities are `[TaskEntity, TaskOccurrenceEntity]` |
| `AppDatabase` at version 2 | Holds | `version = 2`, `exportSchema = true` |
| Schema JSON committed | Holds | `core/data/schemas/...AppDatabase/2.json` is tracked (150 lines). `1.json` was already tracked on `master` and is untouched |

### 3.5 AppClock is the only time source - VERIFIED

A repository-wide search for `LocalDate.now(`, `Instant.now(`, `Clock.system`, and
`ZoneId.systemDefault` finds **exactly one production call**: `Clock.systemDefaultZone()` inside
`DataModule.provideSystemClock()`, which is the intended single seam feeding `SystemAppClock`. The
only other hits are `System.currentTimeMillis()` in pre-existing `:feature:overlay` test polling
loops and one pre-existing onboarding refusal timestamp - all outside this change's scope. Both
`CreateOneOffTask` and the counting boundary go through `clock.now()` / `clock.today()`.

### 3.6 :core:domain purity - VERIFIED

A search for imports of `android`, `androidx`, or `dagger` across all of `core/domain/src` returns
**zero matches**, main and test. `CreateOneOffTask` is deliberately not `@Inject`-annotated; it is
provided from `DataModule`, so the module does not even acquire `javax.inject` for it.

## 4. Per-capability verdicts (all seven specs)

| Spec | Requirements | Verdict | Notes |
|---|---|---|---|
| `hunger-metric` | 7 | **PASS WITH WARNINGS** | All arithmetic and both tiers proven by 15 passing tests including the full corrected table. Two scenarios (completion has no effect; carry-over contributes zero) are guarded by tests that cannot fail - see 5.1 |
| `task-persistence` | 6 | **PASS** | createdDate immutability proven structurally and at runtime; cascade, unique index, and day-boundary round-trip all proven against a real Room database; schema exported and committed; MigrationTestHelper wired and passing |
| `balance-configuration` | 6 | **PASS WITH WARNINGS** | Defaults, 0.6, single goal field, and Hilt provision all proven. The two literal-discipline scenarios rest on tests the maintainer has already accepted as shape-only - see 5.2 |
| `task-creation` | 6 | **PASS** | Trim, blank rejection, 199/200/201 cap boundary, duplicate titles, cap-reached signal, and typed persistence failure all proven behaviourally through the public invoke API. The scoped-CoroutineScope contract is documented in KDoc as the spec requires |
| `domain-time` | 3 | **PASS WITH WARNINGS** | Exactly one port, one static-clock seam, no SQL date function - all verified by inspection and search. No executing test guards them, and the zone-change scenario has no test - see 5.3 |
| `dependency-injection` | 3 (2 added, 1 removed) | **PASS** | The BalanceConfig provider in DataModule is proven by BalanceConfigInjectionTest building a real Hilt SingletonComponent; TaskRepository and AppClock are bound with @Binds in BindingsModule; the PlaceholderDao binding is gone. A missing binding would fail assembleDebug, which passed |
| `build-foundation` | 1 | **PASS** | androidx-room-testing is a catalog entry on the existing room version ref; AndroidRoomConventionPlugin consumes it through the catalog. No literal coordinate in any module script |

Scenario coverage, stated precisely: all 53 scenarios are verified compliant, but only **45 of 53
are backed by an executing test that could actually fail**. The other 8 are absence or purity
claims that I verified by direct structural inspection and repository-wide search, recorded in
section 3 - they are compliant today and structurally guaranteed by the code, but they carry no
regression guard. None is a correctness defect; two of them are already-accepted maintainer
decisions. They are enumerated in 5.1 through 5.4.

## 5. Issues

### CRITICAL

**None.** No requirement is unimplemented, no test fails, no scope boundary is breached, and the
full CI gate is green.

### WARNING

**5.1 - Three Hunger tests are tautologies and cannot fail.** HungerTest contains three tests that
compare two textually identical calls:

```kotlin
val beforeCompletion = calculateHunger(6, 0, config)
val afterCompletion  = calculateHunger(6, 0, config)
assertEquals(beforeCompletion, afterCompletion)
```

This shape appears in "calling calculateHunger repeatedly with the same inputs is deterministic",
"completing a task does not change Hunger", and "a carried-over occurrence contributes nothing to
either term". These assertions are true for any implementation of any pure function; they cannot
detect a regression. The spec requirement "Completion and carry-over have no effect on Hunger"
therefore has **no test that can fail**. The underlying claim is still structurally sound -
calculateHunger takes two Int counts and cannot see completion state or carry-over flags - but that
guarantee comes from the signature, not from these tests. Reporting the coverage honestly: the
requirement is satisfied by construction, not by verification. The same applies to hunger-metric's
"Computable for an arbitrary past date" scenario, which has no test at all and is likewise
guaranteed by the signature taking counts rather than a date.

**5.2 - Three balance/shape tests provide less coverage than their names claim (already accepted by
the maintainer; recorded here, not re-litigated).** Confirmed accurate against the code, with one
addition:

- NoBalanceLiteralOutsideConfigTest searches only for the named assignments recurringHungerRatio = 3
  and recurringHungerCap = 4, which are exactly what a legitimate test override looks like, and test
  directories are excluded anyway. A bare literal 3 used as a divisor in production would not be
  caught. **Additional finding beyond what was flagged**: the test's own KDoc claims dailyTaskGoal =
  10 is covered, but that pattern is **not** in the suspiciousPatterns list. The comment overstates
  the check by one field. Real coverage against the spec's "a grep for balance literals outside
  BalanceConfig finds none" scenario: approximately zero.
- ScoringUnderTwoConfigsTest calls percentOf(5, 10) versus percentOf(5, 20) and asserts they differ.
  That proves integer division works, not that any function is genuinely parameterised. It would
  pass identically if calculateHunger hardcoded its goal.
- BalanceConfigTest's "exactly one field represents the daily task goal" filters declared fields by
  name.contains("dailyTaskGoal"). It can only catch a second field whose name literally contains
  that string. A duplicate named hungerGoal or dailyGoal would pass.

The genuinely load-bearing balance guard in this change is NoBalanceLiteralInSqlTest, which does
scan real @Query text, plus BalanceConfigTest's default-value assertions and
BalanceConfigInjectionTest's real Hilt graph.

**5.3 - domain-time has no executing test.** All four of its scenarios (single port definition,
consumers use the port rather than a static call, no SQL date function computes today, and a mid-day
zone change does not retroactively alter stored dates) are verified in this report by inspection and
repository search only. The closest runtime evidence is TaskRepositoryImplTest's 23:59-to-00:01
boundary test, which proves a date computed under UTC-5 persists and reads back unchanged - but it
never changes the zone, so the zone-change scenario specifically is unproven by execution.

**5.4 - HungerHasNoPetStateWiringTest's filter misses the very names it guards.** It asserts that no
PetSnapshot declared field name contains "hunger". The string "isHungry" lowercases to "ishungry",
which does **not** contain "hunger". A future PetSnapshot with an isHungry field - the exact scope
breach the spec forbids - would pass this test. The scope boundary itself holds today (verified
directly), but this guard would not catch the drift it exists to catch.

**5.5 - TaskDaoNoUpdateOrUpsertTest is scoped to one file and does not guard REPLACE.** It reads
TaskDao.kt alone. A new DAO in another file that declared @Update against TaskEntity would not be
seen, and the test asserts nothing about OnConflictStrategy.REPLACE, which design decision 9 names
explicitly as a delete-then-insert path that would regenerate the row. Today the repository is
clean - I verified REPLACE appears nowhere - but the regression guard is narrower than the decision
it encodes.

**5.6 - Design decision 11 is still wrong in design.md (already known).** The implementation is
correct: Room 2.8.4's MigrationTestHelper loads schema JSON through Context.getAssets(), and the
File constructor argument is the target database file, not the schema source.
AndroidRoomConventionPlugin correctly wires the test source set's assets.srcDirs to the schema
directory, and AppDatabaseMigrationTest passes. apply-progress.md records the deviation accurately.
The design document itself has not been corrected and will mislead the next reader.

**5.7 - design.md's PR-boundaries section still says feature-branch-chain** while the executed
delivery is stacked-to-main per the cached session decision, as tasks.md already notes. Cosmetic
documentation drift; the actual branch topology on GitHub matches what was agreed at task time.

### SUGGESTION

**5.8 - TaskMappers.toDomain() uses an unchecked cast.** TaskTitle.of(title) is cast directly to
TaskTitleResult.Valid, which throws ClassCastException if a persisted title is ever blank or over
200 characters. Unreachable today because every insert goes through validation, but a
destructive-migration-era row or a future direct SQL insert would crash a read path rather than
degrade.

**5.9 - CreateOneOffTask catches bare Exception.** The try block wraps both createOneOff and
countManuallyCreatedOn, so a bug in the cap-reached count is reported to the caller as
PersistenceFailure even though the write succeeded. The typed-result requirement is met; the
attribution is slightly imprecise.

**5.10 - 1.json remains in core/data/schemas/.** It was already tracked on master and design
decision 10 says "no hand-authored 1.json". It is harmless and pre-existing, not introduced by this
change, but it is the schema of the now-deleted PlaceholderEntity and could be dropped alongside
issue #74's fallbackToDestructiveMigration() removal.

## 6. Accepted items confirmed (not verification failures)

Each item the maintainer pre-accepted was checked against the implementation. All are recorded
accurately and none has drifted:

| Item | Confirmed |
|---|---|
| Three PR #73 shape-only tests left unfixed | Yes - see 5.2, with one extra inaccuracy found in a KDoc claim |
| PR #75 at 836 code lines against the 800 budget, deliberately unsplit | Yes, recorded in the chain and not re-litigated here |
| Design decision 11 wrong about MigrationTestHelper; implementation deviated correctly | Yes - see 5.6 |
| TaskRepository.createOneOff takes a points parameter the design's block omits | Yes, and it is the correct call: it keeps standardTaskPoints from becoming a literal |
| CreateTaskResult.Rejected.PersistenceFailure added beyond the design's block | Yes, required by task 3.8 and covered by a passing test |
| fallbackToDestructiveMigration() accepted pre-release, tracked by #74 | Yes - present on the builder in DataModule with the tracked-removal comment naming #74 |
| HUNGRY provider, sprite bindings DataStore to Room, and the resolver's tier-2 gap are part B | Yes - all three absent from the code, recorded in design.md, and written back to #29 and #70 with confirmed comment URLs |

## 7. Design coherence

| Decision | Coherent with code |
|---|---|
| 1 - AppClock shape (now/zone/today default) | Yes, exactly |
| 2 - SystemAppClock in :core:data with @Binds; platform Clock provided in DataModule | Yes |
| 3 - one balance package | Yes |
| 4 - domain models vs entities, internal extension mappers | Yes |
| 5 - narrowed BalanceConfig field set | Yes, six fields, written back to #29 |
| 6 - two tiers, ratio 0.6, exclusive boundary | Yes, and asserted across the whole table |
| 7 - floor everywhere | Yes, MetricRounding and the recurring term both floor |
| 8 - two-argument Hunger signature | Yes |
| 9 - structural createdDate immutability (four measures) | Yes, all four present |
| 10 - schema version 2, Placeholder types deleted, 2.json committed | Yes |
| 11 - room-testing wiring | Deviation, correctly implemented; design text stale (5.6) |
| Transaction shape | withTransaction rather than a @Transaction DAO method - functionally equivalent, both give a single durable operation |

## 8. Final verdict

**PASS WITH WARNINGS.**

The implementation is correct where it matters most. createdDate immutability is enforced
structurally with no whole-object update path anywhere in the repository, the two-tier Hunger model
matches every row of the corrected table with a mathematically sound exclusive boundary, balance
values are genuinely injected, every declared scope boundary holds, AppClock is the sole time
source, and :core:domain is free of Android, Room, and Dagger. The exact CI gate ran with
--rerun-tasks and produced BUILD SUCCESSFUL, exit 0, 472 of 472 tasks executed, with all 48 new
tests green.

The warnings are all about proof strength rather than behaviour: three Hunger tests are tautologies
that cannot fail, three balance tests assert shape rather than parameterisation (already accepted),
the pet-state-wiring guard's string filter misses isHungry, domain-time has no executing test, and
two design documents carry stale text. None blocks archive; each is a candidate follow-up.

Nothing found here requires returning to sdd-apply.
