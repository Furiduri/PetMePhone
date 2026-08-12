# pet-state-resolution

New capability for `slice-2-movable-and-yours` (#37, PR 1). Defines the priority-ordered pet
state resolver, its snapshot input, the provider extension point, and dwell time.
Scope boundary: this change registers only the **DRAGGING** and **IDLE** providers. HAPPY,
TYPING, SLEEPING, HUNGRY and `CelebrationTracker` are OUT — their inputs (`TaskCompletedEvent`,
IME state, energy ledger, task schema) do not exist until slices 3–5. No requirement below may be
satisfied by a provider reading a repository that does not exist in this slice.

## Requirements

### Requirement: PetSnapshot carries only present-tense values
`PetSnapshot` SHALL contain only fields whose truth holds at evaluation time. It SHALL NOT contain
an event list, a timestamp, or a `Clock` reference.

#### Scenario: Snapshot shape has no time-aware field
- **GIVEN** the `PetSnapshot` type definition
- **WHEN** its fields are inspected
- **THEN** none of them is an `Instant`, a duration, or an event list

### Requirement: PetStateProvider is a pure, priority-ordered evaluator
`PetStateProvider` SHALL expose an `Int` priority and `evaluate(snapshot): PetState?`. A provider
MUST NOT read a repository or collect a `Flow` inside `evaluate`.

#### Scenario: Provider evaluation is a pure function of its input snapshot
- **GIVEN** a `PetStateProvider` and a fixed `PetSnapshot`
- **WHEN** `evaluate` is called any number of times with that same snapshot
- **THEN** the result is identical every time, with no side effect

### Requirement: The resolver sorts by priority and falls back to IDLE
`PetStateResolver` SHALL evaluate providers in descending priority order and return the first
non-null result. When every provider returns null, the resolver SHALL return IDLE.

#### Scenario: Providers in scrambled injection order still resolve identically
- **GIVEN** the same provider set supplied in two different orders via `Set<PetStateProvider>`
- **WHEN** the resolver evaluates the same snapshot against each ordering
- **THEN** the resolved state is identical in both cases

#### Scenario: No provider matches
- **GIVEN** DRAGGING is false and no other registered provider matches
- **WHEN** the resolver evaluates
- **THEN** the resolved state is IDLE

### Requirement: The resolver fails fast on a duplicate priority
`PetStateResolver` SHALL fail at construction, not at evaluation time, if two registered providers
share the same priority value.

#### Scenario: Two providers registered with the same priority
- **GIVEN** a provider set containing two providers with an identical priority value
- **WHEN** the resolver is constructed
- **THEN** construction throws, and the failure occurs before any `evaluate` call

#### Scenario: A test asserts distinct priorities across the full registered set
- **GIVEN** the full DI-registered provider set for this slice (DRAGGING, IDLE)
- **WHEN** a test inspects their priorities
- **THEN** all priorities are strictly distinct

### Requirement: DRAGGING outranks IDLE
DRAGGING SHALL be registered at a higher priority than IDLE, so that while dragging is active the
resolver reports DRAGGING regardless of any other state that may exist in a future slice.

#### Scenario: Dragging overrides IDLE
- **GIVEN** `isDragging` is true in the snapshot
- **WHEN** the resolver evaluates
- **THEN** the resolved state is DRAGGING

### Requirement: State resolution emits a Flow with a centrally enforced minimum dwell time
The resolver SHALL expose `Flow<PetState>`. A state change SHALL NOT be emitted again within an
injected minimum dwell duration of the previous emission, even if the underlying snapshot flaps.
The dwell duration MUST be injected configuration, never a literal in the resolver's own code.

#### Scenario: A flapping input does not flicker the emitted state
- **GIVEN** the snapshot alternates rapidly across the dwell window
- **WHEN** the resolver's output `Flow` is observed on virtual time
- **THEN** no two distinct emissions occur closer together than the injected dwell duration

### Requirement: State resolution continues while the screen is off
Resolution SHALL NOT be gated by screen-on/off state. Only rendering (owned by
`pet-overlay-rendering`) is gated by that signal.

#### Scenario: Resolver keeps evaluating with the screen off
- **GIVEN** the screen-on signal reports off
- **WHEN** the snapshot changes
- **THEN** the resolver's `Flow<PetState>` still emits the newly resolved state
