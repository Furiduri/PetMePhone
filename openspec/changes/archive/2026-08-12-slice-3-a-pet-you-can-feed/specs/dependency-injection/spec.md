# Delta for dependency-injection

Delta spec for the change `slice-3-a-pet-you-can-feed` (issues #29, #23). Adds `BalanceConfig`
provision and the new task-persistence repository bindings; removes the `Placeholder*` binding.

## ADDED Requirements

### Requirement: BalanceConfig is provided in DataModule
`DataModule` SHALL provide `BalanceConfig` via `@Provides`, matching the existing split where
`DataModule` holds real construction and `BindingsModule` holds interface-to-implementation only.

#### Scenario: BalanceConfig provider lives in DataModule
- GIVEN the Hilt object graph after this change
- WHEN `DataModule` is inspected
- THEN it declares an `@Provides` function returning `BalanceConfig`

### Requirement: TaskRepository is bound in BindingsModule
`TaskRepository` (interface in `:core:domain`) SHALL be bound to its `:core:data` implementation
via `@Binds` in `BindingsModule`, following the existing `PetProfileRepository` precedent.

#### Scenario: TaskRepository binding follows the existing split
- GIVEN `TaskRepository` and its implementation
- WHEN `BindingsModule` is inspected
- THEN it declares an `@Binds` function binding the interface to the implementation, with no
  `@Provides` construction logic for it

## REMOVED Requirements

### Requirement: PlaceholderEntity/PlaceholderDao binding
(Reason: `PlaceholderEntity` and `PlaceholderDao` are retired now that a real `Task`/
`TaskOccurrence` schema exists, per the declared decision in `task-persistence`.)
(Migration: No replacement binding is needed; any prior binding referencing `PlaceholderDao` is
deleted along with the entity and DAO.)
