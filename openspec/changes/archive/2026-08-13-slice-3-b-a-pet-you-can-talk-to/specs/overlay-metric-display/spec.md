# overlay-metric-display Specification

## Purpose

Per-metric loading / available / unavailable state consumed by the quick-menu card, so absence
never renders as zero and unproducable metrics never render as a permanent spinner.

## Requirements

### Requirement: Each metric is a sealed loading/available/unavailable state
Each of Hunger, Happiness, and Energy SHALL be represented by a sealed type with exactly three
states: loading (value not yet resolved), available (a real value), and unavailable (no producer
exists for this metric in this change). No metric SHALL ever be represented by a bare numeric
default such as `0`.

#### Scenario: No metric collapses to zero anywhere in the pipeline (machine-verifiable)
- GIVEN the metric state types exposed by `PetOverlayStateHolder`
- WHEN their definitions are inspected
- THEN no state carries an implicit or default numeric value; a value only exists in the
  `available` state

### Requirement: Hunger renders as an available real value
Hunger SHALL resolve to the `available` state carrying the live Hunger percentage, sourced from
the new Hunger `Flow`.

#### Scenario: Hunger shows a real number (machine-verifiable)
- GIVEN the Hunger flow has emitted a value
- WHEN the card renders the Hunger metric
- THEN it displays the emitted percentage, not a loading indicator

### Requirement: Happiness and Energy render as an explicit unavailable state, not a spinner
Happiness and Energy SHALL resolve to the `unavailable` state, since no producer exists for either
in this change. This state MUST render as an explicit "not yet available" indication, and MUST
NOT render as an indeterminate loading spinner and MUST NOT render as `0`.

#### Scenario: Happiness shows explicit unavailable text (machine-verifiable)
- GIVEN no Happiness producer exists
- WHEN the card renders the Happiness metric
- THEN it displays an explicit "not yet available" label, not a spinner and not `0`

#### Scenario: Energy shows explicit unavailable text (machine-verifiable)
- GIVEN no Energy producer exists
- WHEN the card renders the Energy metric
- THEN it displays an explicit "not yet available" label, not a spinner and not `0`

### Requirement: The loading state is transient and distinct from unavailable
The loading state SHALL only be used while a metric's flow has not yet emitted its first value. It
MUST NOT be used as a permanent stand-in for a metric with no producer.

#### Scenario: Loading resolves to available once Hunger emits (machine-verifiable)
- GIVEN the Hunger metric is in the loading state before first emission
- WHEN the Hunger flow emits its first value
- THEN the state transitions to available and never reverts to loading while the flow is active

#### Scenario: Happiness never enters the loading state (machine-verifiable)
- GIVEN Happiness has no producer
- WHEN the card's metric state is inspected at any point after initialization
- THEN Happiness is in the unavailable state, never the loading state
</content>
