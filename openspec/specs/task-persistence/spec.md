# task-persistence

New spec for the change `slice-3-a-pet-you-can-feed` (issue #23, schema half). Defines the Room
entity shapes, converters, indices, cascade behaviour, and migration-test wiring for tasks and
their occurrences. Out of scope: RRULE expansion, occurrence generation, carry-over (later
slices).

## Requirements

### Requirement: Task and TaskOccurrence entity shapes
Room SHALL define a `Task` entity with a nullable `rrule: String?`, `createdAt: Instant`, a
denormalised immutable `createdDate: LocalDate`, and `isActive: Boolean`; and a `TaskOccurrence`
entity with a foreign key to `Task.id` cascading on delete, `dueDate: LocalDate`, and the fields
needed to record points and completion state.

#### Scenario: Entity fields present
- GIVEN the `Task` and `TaskOccurrence` Room entities after this change
- WHEN their column definitions are inspected
- THEN `Task` carries nullable `rrule`, `createdAt: Instant`, `createdDate: LocalDate`, and
  `isActive`; `TaskOccurrence` carries a `taskId` foreign key and `dueDate: LocalDate`

### Requirement: createdDate is immutable after insert
No write path SHALL update `Task.createdDate` after the row is inserted. Carrying a task forward,
editing its title, or any other mutation MUST leave `createdDate` unchanged.

#### Scenario: No DAO method updates createdDate
- GIVEN the `TaskDao`/repository write surface
- WHEN every update/upsert method is inspected
- THEN none accepts or writes a new value for `createdDate` on an existing row

#### Scenario: Editing a task leaves createdDate unchanged
- GIVEN a `Task` row inserted with `createdDate = D`
- WHEN any other field of that row is updated
- THEN a subsequent read still reports `createdDate = D`

### Requirement: Cascade delete and unique occurrence index
Deleting a `Task` SHALL cascade-delete its `TaskOccurrence` rows. Inserting two `TaskOccurrence`
rows with the same `(taskId, dueDate)` SHALL be rejected by a unique index.

#### Scenario: Deleting a task cascades its occurrences
- GIVEN a `Task` with one or more `TaskOccurrence` rows
- WHEN the `Task` row is deleted
- THEN its `TaskOccurrence` rows are also removed

#### Scenario: Duplicate (taskId, dueDate) is rejected
- GIVEN a `TaskOccurrence` already exists for `(taskId, dueDate)`
- WHEN a second insert is attempted for the same pair
- THEN the unique index rejects it

### Requirement: LocalDate round-trips with no zone drift
`LocalDate` values SHALL be persisted as ISO-8601 strings via a `TypeConverter` and MUST round-trip
with no zone-dependent drift, including across a day boundary.

#### Scenario: Round-trip across a day boundary
- GIVEN a task created at 23:59 on day D
- WHEN it is persisted and read back at 00:01 on day D+1
- THEN its `createdDate` is still D, counted for day D

### Requirement: Schema is exported and migration-tested even with no migration yet
`room.schemaLocation` SHALL be configured and the resulting schema JSON SHALL be committed under
`:core:data/schemas/`. `room-testing` and `MigrationTestHelper` SHALL be wired even though no
migration exists yet in this change.

#### Scenario: Schema JSON is committed
- GIVEN the entities in this change
- WHEN the schema export task runs
- THEN a JSON schema file is produced and committed under `:core:data/schemas/`

#### Scenario: MigrationTestHelper is wired
- GIVEN the module's test source set
- WHEN it is inspected
- THEN `room-testing` is a declared test dependency and a `MigrationTestHelper`-based test class
  exists, even though it currently exercises no real migration

### Requirement: PlaceholderEntity is retired
`PlaceholderEntity` and its DAO SHALL be removed from the database, as a declared decision that
bumps the schema version.

#### Scenario: Placeholder entity is gone
- GIVEN the `AppDatabase` entity list after this change
- WHEN it is inspected
- THEN `PlaceholderEntity` and `PlaceholderDao` are absent, and the database version has been
  incremented to reflect the schema change
