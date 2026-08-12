# task-creation

New spec for the change `slice-3-a-pet-you-can-feed` (issue #26, domain half). Defines the one-off
task creation use case: validation, duplicate policy, typed result, and caller-scope contract. Out
of scope: overlay/IME wiring (part B).

## Requirements

### Requirement: Task creation validates and trims the title
The creation use case SHALL trim the title before validating it, and MUST reject an empty or
whitespace-only title with a typed failure rather than an exception. Title length MUST be capped
by a domain validation constant (not part of `BalanceConfig`), enforced in `:core:domain`.

#### Scenario: Blank title is rejected
- GIVEN a title of only whitespace
- WHEN the creation use case is invoked
- THEN it returns a typed failure result and no `Task` row is written

#### Scenario: Title is trimmed before checking
- GIVEN a title with leading/trailing whitespace around valid text
- WHEN the creation use case is invoked
- THEN the stored title is trimmed and creation succeeds

#### Scenario: Title length cap boundary
- GIVEN titles at, one under, and one over the domain length cap
- WHEN each is submitted
- THEN the at-cap and under-cap titles succeed and the over-cap title is rejected with a typed
  failure

### Requirement: Duplicate titles are allowed
The use case SHALL NOT enforce any uniqueness constraint on task titles.

#### Scenario: Two tasks with the same title both succeed
- GIVEN a task with title "Walk" already exists
- WHEN another task titled "Walk" is created
- THEN both creations succeed and both rows exist

### Requirement: Creation writes a Task and today's occurrence
Creating a one-off task SHALL insert a `Task` row (no `rrule`) and its `TaskOccurrence` for today
in a single durable operation.

#### Scenario: Task and occurrence are both written
- GIVEN a valid title
- WHEN the use case completes successfully
- THEN a `Task` row exists with `createdDate = today` and a `TaskOccurrence` row exists for that
  task with `dueDate = today`

### Requirement: Creation succeeds even after the Hunger cap is reached
Creating a task SHALL always succeed regardless of the current Hunger value; the use case MUST
expose whether the cap was already reached so the caller can acknowledge it, without blocking the
write.

#### Scenario: Eleventh task of the day still creates
- GIVEN ten tasks already created today under a `dailyTaskGoal` of 10
- WHEN an eleventh task is created
- THEN the creation succeeds and the use case reports the cap-reached signal to the caller

### Requirement: Repository writes run in an application- or service-scoped coroutine scope
The use case's persistence call MUST be documented as requiring an application- or
service-scoped `CoroutineScope`, never one bound to a UI composable's lifecycle, so a fast
dismiss cannot lose the write.

#### Scenario: Invocation contract is documented
- GIVEN the use case's public API
- WHEN its documentation is read
- THEN it states that callers MUST invoke it from an application- or service-scoped
  `CoroutineScope`, not a composable-bound one

### Requirement: Failures surface as typed results
Any failure path of the creation use case (validation, persistence) SHALL surface as a typed
result value, never as an uncaught exception propagated to the caller.

#### Scenario: Persistence failure returns a typed result
- GIVEN a simulated persistence failure
- WHEN the use case is invoked with an otherwise valid title
- THEN it returns a typed failure result rather than throwing
