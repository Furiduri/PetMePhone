# Design: Slice 3 part A — A pet you can feed, the domain half (#29, #23, #26, #33)

## Technical approach

The same split slices 1 and 2 use. `:core:domain` owns every tuned number and every calculation —
`BalanceConfig`, the one rounding convention, the three Hunger functions, title validation, the
task models and the ports — and stays a pure JVM module with no Android, Room or Dagger import.
`:core:data` owns the Room entities, the DAOs, the converters, the mapping and the two Hilt modules.
Hunger takes `Int` counts, never a query, so every row of #33's table is a plain parameterised JUnit
case with no Room and no Robolectric.

Satisfies `balance-configuration`, `domain-time`, `task-persistence`, `task-creation`,
`hunger-metric`, and the `dependency-injection` / `build-foundation` deltas.

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | Clock port shape | `AppClock` interface in `:core:domain/time/`, exposing `now(): Instant`, `zone(): ZoneId`, `today(): LocalDate` | inject raw `java.time.Clock`; a `TodayProvider` returning only `LocalDate` | A raw `Clock` leaves `instant → LocalDate` as a call-site expression each consumer rewrites, and #23 requires that conversion to be one domain function. `TodayProvider` is too narrow: `Task.createdAt` is an `Instant` and #34's boundary detection needs instant granularity, so it would grow the second method within one slice. `AppClock` is the narrow interface *and* general enough |
| 2 | Clock implementation site | `SystemAppClock(clock: java.time.Clock)` in `:core:data/time/`, `@Binds` in `BindingsModule`; `Clock.systemDefaultZone()` `@Provides`d in `DataModule` | implement it in `:core:domain` | Ports live in domain, implementations in data — `PetProfileRepository` precedent exactly. It also lands the module split on the right side: `@Provides` for real construction (the platform `Clock`), `@Binds` for port→impl |
| 3 | Package placement | One `:core:domain/balance/` package: `BalanceConfig.kt`, `MetricRounding.kt`, `Hunger.kt`; Happiness adds `Happiness.kt` there in slice 4 | per-metric packages (`hunger/`, `happiness/`) | `dailyTaskGoal` is one field serving both metrics (#29). Per-metric packages force `happiness` to import `hunger.BalanceConfig`, an inverted dependency between peers. `pet/state/` already holds `PetStateConfig` beside its consumers — same shape |
| 4 | Domain models vs entities | `Task`/`TaskOccurrence` in `:core:domain/task/`; `TaskEntity`/`TaskOccurrenceEntity` in `:core:data/local/task/`; mapping by `internal` extension functions in `TaskMappers.kt` | one Room-annotated type crossing the boundary; a `Mapper<A,B>` abstraction | Confirms the existing hexagonal practice. Extension functions match how `CharacterRepositoryImpl` already maps: no interface, no reflection, one file that a reviewer reads top to bottom |
| 5 | `BalanceConfig` field set | Only fields with a consumer in this slice: `dailyTaskGoal`, `hungryThresholdRatio`, `recurringHungerRatio`, `recurringHungerCap`, `standardTaskPoints`, `version` | #29's full table, including the Energy and Happiness fields | Same reasoning as slice 2's decision 1. #29 requires every field to document its effect when raised and lowered *and* a test running a scenario at two values; a field with no function behind it can satisfy neither, and an undefendable default is precisely the number nobody dares change. Energy fields arrive with the ledger. Written back to #29 |
| 6 | Hunger is two tiers, not one boolean | `isHungry` = below 100% of `dailyTaskGoal`; `isHungerPriority` = below `hungryThresholdRatio`, now **0.6**, boundary **exclusive** (exactly 60% is hungry but not priority). Three functions: `calculateHunger`, `isHungry`, `isHungerPriority` | a single boolean at one threshold, inclusive or exclusive | The design phase flagged #33's table as self-contradicting (`5/0/10 → false` vs `6/0/12 → true`, both exactly 50%). The maintainer's resolution changed the model rather than the operator: the contradiction existed because one boolean was being asked to answer two different questions. Tier 1 answers "is `hungry` an *applicable* animation?", which #70's tap-to-browse needs to be true at 80% too; tier 2 answers "does hunger claim the screen unprompted?". The threshold stays a ratio of `dailyTaskGoal`, never an absolute. #33's body is corrected upstream |
| 7 | Rounding convention | Floor everywhere: `MetricRounding.percentOf(value, goal) = value * 100 / goal` after clamping, and the recurring term floors too | round-half-up | Flooring never claims a target reached that is not: 9.6/10 must not display a satisfied pet. It also already matches #33's `0 manual / 2 recurring → 0%, rounds down` row, so one rule covers both terms. Defined once and shared with Happiness in slice 4 |
| 8 | Hunger signature | `calculateHunger(manualTasksCreatedToday, recurringOccurrencesScheduledToday, config)` | #33's one-argument signature | #33's own formula block has both terms; the signature block predates the recurring rule. The term is zero until slice 4, but adding an argument later means touching every call site of a function everything trusts |
| 9 | `createdDate` immutability | Structural, not conventional — see below | a `val` plus a code comment | A `val` in a data class survives `@Update`, `@Upsert` and `REPLACE`, all of which rewrite the whole row |
| 10 | Schema version | `AppDatabase` 1 → 2, entities `[TaskEntity, TaskOccurrenceEntity]`, `Placeholder*` deleted; `2.json` committed, no hand-authored `1.json`, no migration | keep version 1 and lean on the destructive fallback | The bump is the declared consequence the proposal asks for. `fallbackToDestructiveMigration(dropAllTables = true)` moves onto the builder with the tracked-removal comment #23 requires |
| 11 | `room-testing` wiring | Catalog entry reusing the existing `room` version ref, added as `testImplementation` inside `AndroidRoomConventionPlugin`, which also sets a `room.schemaDirectory` test system property | declare it in `core/data/build.gradle.kts` | The plugin already owns `room.schemaLocation`; the test-side path is the same fact. No version literal and no path literal outside `build-logic` |

### How `createdDate` becomes un-updatable (decision 9)

Four structural measures, no discipline required:

1. `TaskDao` declares **no `@Update` and no `@Upsert`**. Edits are narrow column-naming queries
   (`UPDATE Task SET title = :title WHERE id = :id`), which cannot touch a column nobody named.
2. `@Insert(onConflict = ABORT)`, never `REPLACE` — `REPLACE` is delete-then-insert, which
   regenerates the row and is exactly the carry-over farm path #33 names.
3. `TaskRepository` exposes no whole-object `update(task: Task)`, so no `Task.copy()` has a route
   back to the database.
4. Carry-over (slice 4) writes `TaskOccurrence` rows only; it has no `Task` write path at all.

Guarded by two tests: a source-scan unit test asserting `TaskDao` declares no `@Update`/`@Upsert`
and no `@Query` whose SQL assigns `createdDate`, and a Robolectric test that edits a title and
asserts `createdDate` is unchanged.

## Data flow

```
[create]  (part B UI) ──► CreateOneOffTask(clock, taskRepository, config)     [:core:domain]
              TaskTitle.of(raw)  trim → blank? → >200?  ──► Rejected(...)
              clock.now() / clock.today()                 ← the only "today" in the write path
              ──► TaskRepository.createOneOff(title, createdAt, createdDate)
                      TaskDao @Transaction: insert Task + its TaskOccurrence(dueDate = today,
                      points = config.standardTaskPoints)          [:core:data]
              ──► Created(id, hungerCapReached)      invoked from an application-scoped scope

[hunger]  TaskRepository.countManuallyCreatedOn(date) ──► Int   SELECT COUNT(*) WHERE createdDate = :date
          TaskRepository.countRecurringScheduledOn(date) ──► Int  (0 until slice 4)
              ──► calculateHunger(manual, recurring, config): Int   [:core:domain, pure]
                      recurringPoints = min(recurring / ratio, cap)          floored
                      percentOf(min(manual + recurringPoints, goal), goal)
              ──► isHungry(manual, recurring, config): Boolean          points < goal
              ──► isHungerPriority(manual, recurring, config): Boolean  points < goal * ratio
```

All three ship as pure functions only. No `PetSnapshot` field, no `HUNGRY` provider, no sprite —
that is part B's decision to make when it has a source for it.

### Recorded constraint: the resolver cannot express tier 2 today

`PetStateProvider.evaluate(snapshot)` returns **one `PetState` or `null`**, and `PetStateResolver`
takes the first non-null by descending priority. That shape has exactly two outcomes per provider:
claiming the screen, or being absent. There is no third value for *"applicable, but yielding the
foreground"*, which is precisely what tier 2 splits `isHungry` from `isHungerPriority` to express.

**This gap is deliberately not closed in this change.** Closing it means changing the resolver's
return type from one state to a set of applicable states plus one resolved state — and that is the
*same* set tap-to-browse needs, since slice 2's decision 16 defines a tap as browsing "everything
currently applicable". Building a bespoke tier-2 mechanism here and a browse set in part B would be
the same widening done twice, with the second one obliged to unpick the first.

So part A ships the two booleans as pure functions with no consumer, and **part B builds the
widened resolver once**, serving hunger's two tiers and #70's browse from one representation.
Recorded here as the constraint that decides part B's shape, not as work in this change.

## Interfaces

```kotlin
// :core:domain/time/
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()   // the one conversion, per #23
}

// :core:domain/balance/
data class BalanceConfig(          // every field documents its effect raised and lowered
    val dailyTaskGoal: Int = 10,           // one field: Hunger's goal AND Happiness's floor
    val hungryThresholdRatio: Double = 0.6,   // tier 2 only: below this, hunger claims the screen
    val recurringHungerRatio: Int = 3,
    val recurringHungerCap: Int = 4,
    val standardTaskPoints: Int = 1,
    val version: Int = 1,                  // increments whenever a default above changes
)
object MetricRounding { fun percentOf(value: Int, goal: Int): Int }   // floor; goal > 0
fun calculateHunger(manualTasksCreatedToday: Int, recurringOccurrencesScheduledToday: Int, config: BalanceConfig): Int
/** Tier 1 — is `hungry` an *applicable* animation? True below a fully met goal, so true at 80%. */
fun isHungry(manualTasksCreatedToday: Int, recurringOccurrencesScheduledToday: Int, config: BalanceConfig): Boolean
/** Tier 2 — does hunger claim the screen unprompted? Exclusive: exactly the ratio is hungry, not priority. */
fun isHungerPriority(manualTasksCreatedToday: Int, recurringOccurrencesScheduledToday: Int, config: BalanceConfig): Boolean

// :core:domain/task/
@JvmInline value class TaskId(val value: Long)
@JvmInline value class TaskTitle private constructor(val value: String) {
    companion object { const val MAX_LENGTH = 200; fun of(raw: String): TaskTitleResult }
}
data class Task(val id: TaskId, val title: TaskTitle, val rrule: String?, val createdAt: Instant,
                val createdDate: LocalDate, val isActive: Boolean)
data class TaskOccurrence(val id: Long, val taskId: TaskId, val dueDate: LocalDate,
                          val originDate: LocalDate?, val points: Int, val isCompleted: Boolean,
                          val isCarriedOver: Boolean, val isMandatoryMakeup: Boolean, val createdAt: Instant)

sealed interface CreateTaskResult {
    data class Created(val id: TaskId, val hungerCapReached: Boolean) : CreateTaskResult
    sealed interface Rejected : CreateTaskResult {                 // measured values, as CharacterImportRejection does
        data object BlankTitle : Rejected
        data class TitleTooLong(val length: Int, val maxLength: Int) : Rejected
    }
}

interface TaskRepository {                       // impl + Room in :core:data, @Binds in BindingsModule
    suspend fun createOneOff(title: TaskTitle, createdAt: Instant, createdDate: LocalDate): TaskId
    suspend fun countManuallyCreatedOn(date: LocalDate): Int
    suspend fun countRecurringScheduledOn(date: LocalDate): Int
    fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>>   // Flow for reads, suspend for writes
}

class CreateOneOffTask(private val clock: AppClock, private val tasks: TaskRepository,
                       private val config: BalanceConfig) {
    /** MUST be invoked from an application- or service-scoped CoroutineScope, never
     *  rememberCoroutineScope(): a card dismissed the instant after submit must not lose the write. */
    suspend operator fun invoke(rawTitle: String): CreateTaskResult
}
```

Balance values are never literals: all three Hunger functions and `CreateOneOffTask` take
`BalanceConfig` as a parameter, and no `@Query` string contains one.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `core/domain/.../time/AppClock.kt` | Create | Decision 1, #23 |
| `core/domain/.../balance/{BalanceConfig,MetricRounding,Hunger}.kt` | Create | #29, #33 |
| `core/domain/.../task/{TaskId,TaskTitle,Task,TaskOccurrence,TaskRepository,CreateTaskResult,CreateOneOffTask}.kt` | Create | #23 models, #26 |
| `core/data/.../time/SystemAppClock.kt` | Create | Decision 2 |
| `core/data/.../local/RoomTypeConverters.kt` | Create | `LocalDate ↔ ISO String`, `Instant ↔ Long` |
| `core/data/.../local/task/{TaskEntity,TaskOccurrenceEntity,TaskDao,TaskOccurrenceDao,TaskMappers}.kt` | Create | #23 schema, decisions 4 and 9 |
| `core/data/.../repository/TaskRepositoryImpl.kt` | Create | Port impl, `@Transaction` create |
| `core/data/.../local/AppDatabase.kt` | Modify | Version 2, new entities, converters (decision 10) |
| `core/data/.../local/{PlaceholderEntity,PlaceholderDao}.kt` | Delete | Retired as a declared decision |
| `core/data/.../di/DataModule.kt` | Modify | `@Provides` `BalanceConfig`, `java.time.Clock`, `CreateOneOffTask`; destructive-fallback builder |
| `core/data/.../di/BindingsModule.kt` | Modify | `@Binds` `TaskRepository`, `AppClock` |
| `core/data/schemas/…/2.json` | Create | Committed export |
| `gradle/libs.versions.toml` | Modify | `androidx-room-testing`, existing `room` version ref |
| `build-logic/.../AndroidRoomConventionPlugin.kt` | Modify | `testImplementation` + `room.schemaDirectory` test property |

`CreateOneOffTask` is `@Provides`d in `DataModule` rather than `@Inject`-annotated, so `:core:domain`
gains no `javax.inject` dependency it does not have today. Its consumer's scope is part B's wiring.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit `:core:domain` | All eleven #33 table rows, incl. 30 recurring never exceeding the cap (named test); both tiers across the same rows, incl. the exclusive tier-2 boundary at exactly 60% and a row where `isHungry` is true while `isHungerPriority` is false; defaults equal the PRD; the same scenario at `dailyTaskGoal = 10` and `= 20`; `percentOf` at 7 and 12; trim, blank, and the 199/200/201 cap boundary; duplicate titles both succeed | JUnit4, pure Kotlin, parameterised; a hand-written `AppClock` fake |
| Unit `:core:data` (Robolectric, in-memory Room) | Created-today count across a 23:59 → 00:01 boundary; generated occurrences never move the manual count; duplicate `(taskId, dueDate)` rejected; delete cascades; `LocalDate` round-trip with no zone drift; a title edit leaves `createdDate` unchanged; two concurrent inserts both land | `runTest` + `Room.inMemoryDatabaseBuilder` |
| Unit `:core:data` (source scan) | `TaskDao` has no `@Update`/`@Upsert` and no SQL assigning `createdDate`; no balance literal appears in any `@Query` | Source-text scan over `core/data/src/main` |
| Migration | Version 2 schema loads and the database opens | `MigrationTestHelper` via the `room.schemaDirectory` property — wired now so the first real migration is a test, not an infrastructure project |

## Threat matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The adjacent hazard is untrusted user text, bounded by `TaskTitle`'s
trim and 200-character cap enforced in the domain rather than in layout.

## Migration / rollout

No migration is written. `AppDatabase` goes to version 2 with
`fallbackToDestructiveMigration(dropAllTables = true)` and the tracked follow-up to remove it before
first public release stays open, per #23. The database is pre-release, so a revert of the schema PR
destroys task rows rather than corrupting them. Revert order is newest-first: #33, #26, #23, #29.

## PR boundaries

| PR | Content | Depends on | Est. changed lines |
|---|---|---|---|
| 1 | #29 — `BalanceConfig`, `MetricRounding`, `DataModule` provider, defaults test | tracker branch | ~180 |
| 2 | #23 — `AppClock`, entities, DAOs, converters, mappers, `TaskRepository`, schema JSON, `room-testing` | 1 | ~460 |
| 3 | #26 — `TaskTitle`, `CreateTaskResult`, `CreateOneOffTask`, caller-scope contract | 2 | ~260 |
| 4 | #33 — `calculateHunger`/`isHungry`/`isHungerPriority`, recurring term, both tiers over the full table | 3 | ~230 |

Each slice is under the 800-line budget on its own; the chain is not. PR 1 targets the tracker
branch, each later PR targets its predecessor (`feature-branch-chain`).

## Open questions

- [x] **Resolved by the maintainer.** The contradiction this design flagged in #33's test table was
      real, and the resolution changed the model rather than an operator: hunger became two tiers
      (decision 6), `hungryThresholdRatio` moved to `0.6`, and the boundary is exclusive. #33's body
      is corrected upstream. The constraint this creates in `PetStateResolver` is recorded above and
      is part B's work, not this change's.
- [ ] Decision 5 ships fewer `BalanceConfig` fields than #29's snippet — `dailyTaskGoal`,
      `hungryThresholdRatio`, `recurringHungerRatio`, `recurringHungerCap`, `standardTaskPoints` and
      `version`, with the Energy and Happiness fields deferred to the slices that give them a
      function and a test. A deliberate narrowing, written back to #29, not an omission.
- [ ] Slice 2's decision 16 states that imported characters' sprite bindings migrate from DataStore
      to Room "in slice 3 as part of #23". **This change does not fulfil that commitment**, and by
      the maintainer's ruling it stays out of part A: folding a third table into the task schema PR
      would blur two unrelated review surfaces. It is not carried as silent debt — it gets resolved
      explicitly against #70, whose widened applicable-state work is the same surface it belongs to.
