# domain-time

New spec for the change `slice-3-a-pet-you-can-feed` (issue #23, domain half). Defines the single
injected clock/zone abstraction that task creation, Hunger, and later day-close (#34) all share.

## Requirements

### Requirement: A single injected clock/zone port exists in :core:domain
`:core:domain` SHALL define exactly one clock/zone abstraction (port) that yields "today" as a
`LocalDate`. It MUST be defined once and reused by every consumer that needs "today" — no
per-metric or per-feature duplicate abstraction SHALL be introduced.

#### Scenario: Single port definition
- GIVEN the repository source after this change
- WHEN it is searched for a type providing "today" as a `LocalDate`
- THEN exactly one such port exists in `:core:domain`, with no duplicate per-feature equivalent

#### Scenario: Consumers depend on the port, not a static call
- GIVEN task creation and Hunger's counting boundary
- WHEN their implementations are inspected
- THEN both obtain "today" through the injected port, never through `LocalDate.now()`,
  `Clock.systemDefaultZone()`, or an equivalent static call

### Requirement: Today is never derived inside SQL
No SQL statement or Room `@Query` SHALL compute "today" using a date/time SQL function. The date
used to filter or write rows MUST be computed once in `:core:domain` and passed in as a parameter.

#### Scenario: No SQL date function computes today
- GIVEN every `@Query` in `:core:data`
- WHEN their SQL text is inspected
- THEN none derives the current date via a SQL date/time function; each receives `today` as a
  bound parameter

### Requirement: Zone changes do not retroactively alter already-generated dates
Once a date has been computed and persisted for a row, a later change in the device's zone SHALL
NOT retroactively change that row's date.

#### Scenario: Mid-day zone change leaves prior dates unchanged
- GIVEN a row was written with `createdDate = today` under zone A
- WHEN the device's zone changes to B before the day boundary in either zone
- THEN the row's `createdDate` is still the value computed under zone A
