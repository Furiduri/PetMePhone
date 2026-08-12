# Exploration — slice-3-a-pet-you-can-feed, part A (#29, #23, #26, #33)

Scope: the domain half of build-order slice 3, "A pet you can feed". The overlay half (#17 quick
menu card shell, #18 focusable text input / IME, #27 wire submit to task creation) is a deliberate
separate change, part B, because #18 is gated on the M1 IME spike and this half is not.

Investigation only. Every statement below was verified against the code, not assumed.

## Existing patterns to reuse — do not invent new ones

- **`PetStateConfig(minimumDwellMillis: Long)`**, constructor-injected into `PetStateResolver`, is
  the exact precedent for `BalanceConfig`. "Inject, don't reference" is already house style, so #29
  is a second instance of an established pattern rather than a new one.
- **`:core:domain`** holds pure value objects, ports and pure logic. It has no Android, Room or
  Dagger imports beyond `javax.inject`. `BalanceConfig`, the task/occurrence models, the repository
  ports and `calculateHunger`/`isHungry` all belong here.
- **`:core:data`** holds Room entities, DAOs and repository implementations, plus two Hilt modules
  with a strictly enforced split: `DataModule` (`@Provides`, real construction — the Room database
  and the `DataStore<Preferences>` instance) and `BindingsModule` (`@Binds`, interface→implementation
  only). `BalanceConfig`'s provider goes in `DataModule`; the new repository bindings go in
  `BindingsModule`. The `dependency-injection` spec already makes that split a requirement.
- **`PetProfileRepository` / `PetProfileRepositoryImpl`** is the precedent for `TaskRepository`:
  interface in `:core:domain`, implementation in `:core:data`, bound in `BindingsModule`.
- **`AndroidRoomConventionPlugin`** already sets `room.schemaLocation`. #23's schema-export criterion
  is therefore infrastructurally satisfied already; only the JSON needs committing once real
  entities exist.
- **`ProjectConfig.minSdk = 26`**, single-owned in `build-logic`, confirms `java.time` needs no core
  library desugaring — exactly what #23 asks to be confirmed rather than assumed. No build literal
  may live outside `build-logic`.

## New infrastructure this change must ORIGINATE, not extend

- **An injected "today" abstraction** (`Clock` / zone provider) in `:core:domain`. There is currently
  zero usage of `java.time`, `kotlinx.datetime` or any `Clock` anywhere in the repository. Its shape
  must be defined ONCE: task creation, Hunger and the later day-close (#34) all depend on it
  identically, and #23 explicitly forbids deriving "today" inside a SQL date function.
- **`room-testing` / `MigrationTestHelper` wiring.** No version-catalog entry exists. This is new
  build-graph surface and must respect the centralized `ProjectConfig`/version-catalog convention
  rather than adding literals ad hoc.

## Decisions already made by the maintainer — carried, not re-opened

1. **Hunger is live-only in this change.** #29 mentions freeze-at-close treatment for all three
   metrics, but day close is #34 (slice 4) and #33 is written as a *consumer* of it. No frozen
   snapshot table and no `balanceVersion` stamping for Hunger lands here. The boundary is written
   down so the change does not over-build.
2. **`PlaceholderEntity` is retired in this change**, now that a real entity lands. This is a
   declared decision with its own schema-version consequence, never an incidental diff.
3. **The `Clock`/zone abstraction is defined once**, shared across metrics, not per-metric.
4. **Recurring occurrences count at 3:1 capped at 4** per the build order. No recurring tasks exist
   until slice 4, so the term is simply zero now. Build it anyway: retrofitting it after Hunger
   ships means touching the metric everything else already trusts.

## Open design questions — for the design phase, not decided here

1. **Exact shape of the `Clock`/zone port**: a wrapped `java.time.Clock` versus a custom
   `TodayProvider` returning `LocalDate`. The second is narrower and easier to fake in tests; the
   first is more general and is what #23's text names.
2. **Package placement of `calculateHunger`/`isHungry` and `BalanceConfig`**: a dedicated `balance`
   package versus per-metric packages. #29 makes the config explicitly shared across metrics
   (Happiness's denominator floor *is* Hunger's goal, one field), which argues for a separate
   `balance` package to avoid circular coupling when Happiness lands in slice 4.
3. **Domain `Task` / `TaskOccurrence` models kept distinct from `TaskEntity` / `TaskOccurrenceEntity`**
   with mapping in `:core:data`, consistent with existing hexagonal practice here.

## Risks

- **A balance literal slipping into a Room `@Query`** would defeat the whole injection guarantee, and
  nothing would fail loudly. #29 already asks for a grep-based check; it deserves to be a criterion.
- **`createdDate` must have no update path from the start.** Carry-over is slice 4 (#24), but
  designing the field mutable now forces a later migration and, worse, makes Hunger permanently
  farmable if a carry-over upsert ever regenerates the row.
- **New build-graph surface (`room-testing`)** risks drifting from the centralized `ProjectConfig`
  convention if added carelessly.

## Standing project conventions in force

Balance values are injected configuration, never literals. Absence never renders as zero.
Clean/Hexagonal architecture with `:core:domain` free of framework imports.
