# hunger-metric

New spec for the change `slice-3-a-pet-you-can-feed` (issue #33, domain half). Defines the live
Hunger calculation and its two supporting boolean tiers, `isHungry` and `isHungerPriority`. Out of
scope: freeze-at-close, the `HUNGRY` pet-state provider/`PetSnapshot` field/sprite, and any
`pet-state-resolution` change (all part B); day-close backfill (#34).

**Noted architectural constraint (not addressed by this change):** `PetStateProvider.evaluate`
currently returns one `PetState` or `null`, so the resolver cannot express "applicable but not
claiming the screen" — the gap between `isHungry` (tier 1, applicability) and `isHungerPriority`
(tier 2, foreground claim). Closing that gap is part B's problem, shared with tap-to-browse (#70).

## Requirements

### Requirement: Hunger is a pure, live-only calculation
`calculateHunger(manualTasksCreatedToday: Int, recurringOccurrencesToday: Int, config:
BalanceConfig): Int` SHALL be a pure function in `:core:domain` with no Room or Android reference,
computable for any given date's counts, including past dates. No frozen snapshot or
`balanceVersion` stamping is performed by this function.

#### Scenario: Same inputs always produce the same result
- GIVEN fixed counts and a fixed `BalanceConfig`
- WHEN `calculateHunger` is called repeatedly
- THEN it returns the same value every time, with no I/O performed

#### Scenario: Computable for an arbitrary past date
- GIVEN the manual and recurring counts recorded for a date in the past
- WHEN `calculateHunger` is called with those counts
- THEN it returns a result with no dependency on the current system date

### Requirement: Manual creations count 1:1, filtered by createdDate
Hunger's manual component SHALL count `Task` rows whose `createdDate` equals the target date, and
MUST NOT count rows filtered by `dueDate`.

#### Scenario: Created-today counts, due-later does not double count
- GIVEN a task created today with a due date five days from now
- WHEN Hunger is calculated for today
- THEN it counts the task for today, and it does not count again when its due date arrives

### Requirement: Recurring occurrences count at a capped ratio
Recurring occurrences scheduled for the target date SHALL contribute
`min(recurringOccurrencesToday / recurringHungerRatio, recurringHungerCap)` points, floored, using
`recurringHungerRatio` and `recurringHungerCap` from `BalanceConfig`. No recurring tasks exist
until slice 4, so this term is zero in practice today but MUST be implemented and tested now.

#### Scenario: Thirty recurring occurrences never exceed the cap
- GIVEN `recurringHungerRatio = 3`, `recurringHungerCap = 4`, and 30 recurring occurrences today
- WHEN Hunger is calculated
- THEN the recurring contribution equals exactly the cap (4), never more

#### Scenario: Below-ratio recurring count rounds down to zero
- GIVEN `recurringHungerRatio = 3` and 2 recurring occurrences today
- WHEN Hunger is calculated
- THEN the recurring contribution is 0

#### Scenario: Zero recurring occurrences today (current reality)
- GIVEN 0 recurring occurrences today
- WHEN Hunger is calculated
- THEN the recurring contribution is 0 and Hunger equals the manual contribution alone

### Requirement: Hunger percentage is capped at 100 and rounds with the shared convention
`hunger% = min(manualPoints + recurringPoints, dailyTaskGoal) / dailyTaskGoal × 100`, using the
same rounding convention shared with Happiness. The result MUST never exceed 100 and MUST never
error for over-goal input.

#### Scenario: Overshoot never exceeds 100
- GIVEN manual and recurring points summing above `dailyTaskGoal`
- WHEN Hunger is calculated
- THEN the result is exactly 100, not an error and not above 100

#### Scenario: Manual plus capped recurring, clamped to the goal
- GIVEN `manual = 8`, `recurringOccurrencesToday = 30`, `dailyTaskGoal = 10`,
  `recurringHungerRatio = 3`, `recurringHungerCap = 4`
- WHEN Hunger is calculated
- THEN the recurring contribution is capped at 4, the sum (12) is clamped to the goal, and the
  result is 100%

### Requirement: Completion and carry-over have no effect on Hunger
Completing a task or occurrence SHALL NOT affect Hunger. Carried-over occurrences SHALL contribute
nothing to Hunger.

#### Scenario: Completing a task changes nothing
- GIVEN a manual task counted toward today's Hunger
- WHEN it is marked complete
- THEN Hunger is unchanged

#### Scenario: Carried-over occurrence contributes zero
- GIVEN an occurrence carried over from a previous day
- WHEN Hunger is calculated for today
- THEN the carried-over occurrence contributes nothing to either the manual or recurring term

### Requirement: isHungry (tier 1) is true below a fully met goal
`isHungry(manualTasksCreatedToday: Int, recurringOccurrencesToday: Int, config: BalanceConfig):
Boolean` SHALL return `true` whenever Hunger is below 100% of `dailyTaskGoal`, and `false` only
when the goal is fully met (100%). This is the applicability check for the `hungry` animation,
reachable by tap-to-browse (#70) even at high Hunger percentages such as 80%. `isHungry` ships as
a pure function only in this change; it is NOT wired into any `PetSnapshot` field, sprite, or pet-
state provider.

#### Scenario: Below 100% is hungry
- GIVEN `dailyTaskGoal = 10` and 8 manual tasks created today (80%)
- WHEN `isHungry` is evaluated
- THEN it returns `true`

#### Scenario: Exactly 100% is not hungry
- GIVEN `dailyTaskGoal = 10` and 10 manual tasks created today (100%)
- WHEN `isHungry` is evaluated
- THEN it returns `false`

#### Scenario: Clamped-over-100% is not hungry
- GIVEN inputs whose raw sum exceeds `dailyTaskGoal` (Hunger clamps to 100%)
- WHEN `isHungry` is evaluated
- THEN it returns `false`

#### Scenario: isHungry has no pet-state wiring in this change
- GIVEN the `isHungry` function and `PetSnapshot`
- WHEN `PetSnapshot`'s fields are inspected
- THEN no `hungry`/`HUNGRY`-related field exists; `isHungry` is called by no pet-state provider

### Requirement: isHungerPriority (tier 2) claims the foreground below the ratio, exclusive
`isHungerPriority(manualTasksCreatedToday: Int, recurringOccurrencesToday: Int, config:
BalanceConfig): Boolean` SHALL return `true` only when Hunger is strictly below
`hungryThresholdRatio × dailyTaskGoal`. The boundary is exclusive: a value exactly at the ratio is
hungry (tier 1) but does NOT claim priority. At or above the ratio, `isHungerPriority` MUST return
`false`, leaving room for another state provider to claim the foreground on its first-time trigger.
The threshold MUST remain a ratio of `dailyTaskGoal`, never an independent absolute field.
`isHungerPriority` ships as a pure function only in this change; it is NOT wired into any
`PetSnapshot` field, sprite, or pet-state provider.

#### Scenario: Exactly at the ratio is hungry but not priority
- GIVEN `dailyTaskGoal = 10`, `hungryThresholdRatio = 0.6`, and 6 manual tasks created today (60%)
- WHEN `isHungry` and `isHungerPriority` are evaluated
- THEN `isHungry` returns `true` and `isHungerPriority` returns `false` (the boundary is exclusive)

#### Scenario: Just above the ratio is hungry but not priority
- GIVEN `dailyTaskGoal = 10`, `hungryThresholdRatio = 0.6`, and 7 manual tasks created today (70%)
- WHEN `isHungry` and `isHungerPriority` are evaluated
- THEN `isHungry` returns `true` and `isHungerPriority` returns `false`

#### Scenario: Below the ratio is both hungry and priority
- GIVEN `dailyTaskGoal = 10`, `hungryThresholdRatio = 0.6`, and 5 manual tasks created today (50%)
- WHEN `isHungry` and `isHungerPriority` are evaluated
- THEN both return `true`

#### Scenario: Mid-tier value (80%) is hungry but never priority
- GIVEN `dailyTaskGoal = 10`, `hungryThresholdRatio = 0.6`, 6 manual and 6 recurring occurrences
  today under `recurringHungerRatio = 3`, `recurringHungerCap = 4` (manual 6 + recurring 2 = 80%)
- WHEN `isHungry` and `isHungerPriority` are evaluated
- THEN `isHungry` returns `true` and `isHungerPriority` returns `false`

#### Scenario: Ratio moves the trigger with the goal
- GIVEN `dailyTaskGoal = 12`, `hungryThresholdRatio = 0.6`, and 7 manual tasks created today
  (below the derived cutoff of 7.2)
- WHEN `isHungerPriority` is evaluated
- THEN it returns `true`, because the cutoff is derived from the ratio, not a fixed number

#### Scenario: 100% and clamped-over-100% are never priority
- GIVEN Hunger at exactly 100% or clamped to 100% from an over-goal sum
- WHEN `isHungry` and `isHungerPriority` are evaluated
- THEN `isHungry` returns `false` and `isHungerPriority` returns `false`

#### Scenario: isHungerPriority has no pet-state wiring in this change
- GIVEN the `isHungerPriority` function and `PetSnapshot`
- WHEN `PetSnapshot`'s fields are inspected
- THEN no priority-related field exists; `isHungerPriority` is called by no pet-state provider

## ADDED Requirements (slice-3-b)

### Requirement: An observable Hunger `Flow` composes counts, clock, and config
The system SHALL expose a `Flow<Int>` (or an equivalent observable) that emits the current
Hunger percentage, composed from `TaskRepository`'s manual and recurring counts for "today" (as
resolved by `AppClock`), `BalanceConfig`, and the existing pure `calculateHunger` function. No
count SHALL be cached anywhere in the overlay layer.

#### Scenario: Hunger flow emits from repository counts (machine-verifiable)
- GIVEN a `TaskRepository` with a known count of tasks created today
- WHEN the Hunger flow is collected
- THEN it emits the value `calculateHunger` produces for those counts and the injected
  `BalanceConfig`

#### Scenario: Hunger flow updates when a new task is created today (machine-verifiable)
- GIVEN the Hunger flow is being collected
- WHEN a new task with today's `createdDate` is inserted
- THEN the flow emits an updated Hunger value reflecting the new count

#### Scenario: "Today" is resolved through AppClock, not the system clock directly (machine-verifiable)
- GIVEN the Hunger flow composition
- WHEN its implementation is inspected
- THEN "today" is derived from `AppClock`, not a direct `System.currentTimeMillis()` or
  `LocalDate.now()` call

#### Scenario: No Hunger count is cached in the overlay layer (machine-verifiable)
- GIVEN the overlay module's state holder
- WHEN searched for cached Hunger count fields
- THEN none exists; the displayed value always derives from the live flow
