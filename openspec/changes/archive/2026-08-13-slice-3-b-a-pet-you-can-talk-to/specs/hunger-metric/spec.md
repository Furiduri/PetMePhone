# Delta for hunger-metric

Delta spec for the change `slice-3-b-a-pet-you-can-talk-to` (issue #17). Adds observable Hunger
plumbing — the `hunger-metric` capability shipped only pure functions with no production consumer
(Part A); this change composes them into a live `Flow` for `overlay-metric-display` to consume.
No existing requirement in the main spec is changed.

## ADDED Requirements

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
</content>
