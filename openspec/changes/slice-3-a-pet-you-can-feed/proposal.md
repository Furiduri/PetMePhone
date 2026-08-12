# Proposal: Slice 3 (part A) — A pet you can feed, the domain half

Build order slice 3, issues #29, #23, #26, #33. Part B (#17, #18, #27) is a separate change,
split because #18 is gated on the M1 IME spike and this half is not.

## Intent

The pet moves and is yours, but nothing feeds it. There is no task, no persistence for one, no
metric, and every tuned number in the PRD is still unwritten. This change lands the domain and data
half of the first real loop: a documented injected balance configuration, a task/occurrence schema,
a task creation use case, and a live Hunger metric — all shippable regardless of how the IME spike
concludes.

## Scope

### In Scope
- `BalanceConfig` in `:core:domain`: documented defaults (each field stating its effect raised and
  lowered), one `dailyTaskGoal` serving both the Hunger goal and the future Happiness floor,
  `hungryThresholdRatio` as a ratio, `recurringHungerRatio`/`recurringHungerCap`, a version integer,
  Hilt provision in `DataModule` (#29).
- A shared injected clock/zone port in `:core:domain`, defined **once** (#23).
- Room `Task` (nullable `rrule`, `createdAt: Instant`, denormalised immutable `createdDate:
  LocalDate`, `isActive`) and `TaskOccurrence` (FK cascade, unique `(taskId, dueDate)`, indices),
  TypeConverters, committed schema JSON, `room-testing`/`MigrationTestHelper` wiring (#23).
- One-off task creation use case: trim, reject blank, cap title length in domain, duplicates allowed,
  typed result, application-scoped invocation contract (#26).
- `calculateHunger` / `isHungry`: pure, `BalanceConfig`-parameterised, computable for any past date,
  recurring term at 3:1 capped at 4 (zero in practice until slice 4), one shared rounding convention
  (#33).
- Retire `PlaceholderEntity` and its DAO as a declared decision with a schema-version consequence.

### Out of Scope
- Day close, frozen snapshots and `balanceVersion` stamping (#34, slice 4). **Hunger is live-only
  here**; #33 is written as a consumer of a mechanism that does not exist yet.
- Happiness, Energy, the ledger, RRULE expansion, occurrence generation, carry-over (slices 4–5).
- All UI: quick menu card, IME text entry, submit wiring (part B).
- A `HUNGRY` pet-state provider. `isHungry` ships as a pure function; wiring it into
  `PetStateResolver` needs a new `PetSnapshot` field and a sprite, and belongs with the visual half.
- Runtime/user-facing balance configuration, settings screen, remote config.

## Capabilities

### New Capabilities
- `balance-configuration`: the value object, documented defaults, single-field sharing, injection
  rule, no-literal rule.
- `domain-time`: the injected clock/zone port; "today" computed explicitly, never in SQL.
- `task-persistence`: entity shapes, converters, indices, cascade, idempotence by unique index,
  schema export, migration-test wiring.
- `task-creation`: validation rules, duplicate allowance, typed failure, caller scope contract.
- `hunger-metric`: the live-count model, recurring weighting and cap, `createdDate` immutability,
  the `HUNGRY` ratio, rounding.

### Modified Capabilities
- `dependency-injection`: `BalanceConfig` provided in `DataModule`, new repository bindings in
  `BindingsModule`, `PlaceholderEntity`/`PlaceholderDao` removed from the database.
- `build-foundation`: `room-testing` added through the version catalog and `ProjectConfig`, never
  as an ad hoc literal.

## Approach

Everything tuned or calculated is pure and parameterised in `:core:domain`; Room, converters and the
count query live in `:core:data` behind ports (`TaskRepository`, `countManuallyCreatedOn(date)`).
Hunger takes `Int` counts, not a query, so every boundary case is a plain parameterised JUnit test
with no Room and no Robolectric. Order follows the build order: config, then schema, then use case,
then metric — each a chained PR.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `core/domain/.../balance/` | New | `BalanceConfig`, Hunger functions, rounding |
| `core/domain/.../time/` | New | Clock/zone port |
| `core/domain/.../task/` | New | `Task`, `TaskOccurrence`, use case, `TaskRepository` port |
| `core/data/.../local/` | New/Removed | Task entities, DAOs, converters; `Placeholder*` deleted |
| `core/data/.../local/AppDatabase.kt` | Modified | Entity list, version, converters |
| `core/data/.../di/DataModule.kt`, `BindingsModule.kt` | Modified | Config provider, new bindings |
| `core/data/schemas/` | New | Committed schema JSON |
| `gradle/libs.versions.toml`, `build-logic` | Modified | `room-testing` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A balance literal survives in a `@Query`, SQL or formatter — nothing fails loudly | Medium | Grep-based check as an acceptance criterion, per #29 |
| `createdDate` gains an update path, making Hunger permanently farmable | Medium | Immutable by construction from insert; a test asserts no write path touches it |
| Hunger's exclusion is no longer structural once two tables are read | High | Calculation lives in exactly one place, with named tests for the cap and the recurring ratio |
| `room-testing` added ad hoc, drifting from centralized config | Medium | Version catalog entry, reviewed as build-graph surface |
| Retiring `PlaceholderEntity` silently changes schema version | Low | Declared decision with an explicit version bump and committed schema JSON |
| Clock port shape chosen per-metric and duplicated later | Medium | Defined once in this change; #34 and Happiness consume the same port |

## Rollback Plan

Each issue is an independently revertable PR; the chain reverts newest-first (#33, #26, #23, #29).
The database is pre-release and uses `fallbackToDestructiveMigration()`, so reverting the schema PR
destroys task rows rather than corrupting them — acceptable pre-release, and the tracked follow-up
to remove that fallback stays open. Reverting #29 alone would strand #33's parameters, so revert
#33 and #26 first.

## Dependencies

- None outside the repository. #37's resolver and `:core:data`'s Room/Hilt scaffolding are shipped.
- Part B (#17/#18/#27) depends on this change and on the M1 IME spike outcome.

## Success Criteria

- [ ] No domain function reads a balance value from a global or companion constant; a grep finds no
      balance literal outside `BalanceConfig`.
- [ ] At least one scoring test runs the same scenario under two different `BalanceConfig` values.
- [ ] `dailyTaskGoal` is one field; no independent absolute `HUNGRY` threshold exists.
- [ ] Thirty recurring occurrences never exceed the cap, proven by a named test.
- [ ] Creating a task with a blank or whitespace-only title fails with a typed result, not an
      exception; duplicate titles both succeed.
- [ ] The eleventh task of the day still creates successfully and the cap-reached signal is exposed.
- [ ] Inserting the same `(taskId, dueDate)` twice is rejected by the unique index; deleting a task
      cascades its occurrences.
- [ ] `LocalDate` round-trips through the converter with no zone drift; a task created at 23:59 and
      read at 00:01 counts for the correct day.
- [ ] Hunger is computable for an arbitrary past date, with no Room or Android reference in the
      calculation.
- [ ] Schema JSON is committed and `MigrationTestHelper` is wired even though no migration exists.
