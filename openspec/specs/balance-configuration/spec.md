# balance-configuration

New spec for the change `slice-3-a-pet-you-can-feed` (issue #29, domain half). Defines the
injected `BalanceConfig` value object and the injection discipline every tuned number in the app
must follow. Out of scope: freeze-at-close/`balanceVersion` stamping (day close is #34, slice 4);
runtime configuration, settings UI, remote config; UI/validation caps (e.g. title length).

## Requirements

### Requirement: BalanceConfig is a single documented value object
`BalanceConfig` SHALL exist in `:core:domain` as a data class carrying at minimum `dailyTaskGoal`,
`hungryThresholdRatio`, `recurringHungerRatio`, `recurringHungerCap`, and an integer `version`
field. Each field's KDoc/comment MUST state its effect when raised and when lowered. Defaults
MUST match the PRD values.

#### Scenario: Defaults match the PRD
- GIVEN `BalanceConfig()` constructed with no arguments
- WHEN its default field values are inspected
- THEN they equal the documented PRD defaults, asserted by a test

#### Scenario: Every field documents raise/lower effect
- GIVEN the `BalanceConfig` source
- WHEN each field's comment is read
- THEN it states what happens when the value is raised and what happens when it is lowered

### Requirement: hungryThresholdRatio defaults to 0.6 and gates Hunger's priority tier
`hungryThresholdRatio` SHALL default to `0.6`. It gates only the priority tier
(`isHungerPriority` in `hunger-metric`) — the applicability tier (`isHungry`) is independent of
this field and is derived only from whether `dailyTaskGoal` is fully met. The boundary at exactly
the ratio is exclusive: it does not claim priority.

#### Scenario: Default ratio is 0.6
- GIVEN `BalanceConfig()` constructed with no arguments
- WHEN `hungryThresholdRatio` is inspected
- THEN it equals `0.6`

### Requirement: dailyTaskGoal is a single shared field
The Hunger goal SHALL be represented by exactly one `dailyTaskGoal` field, with no independent
field duplicating its value for another metric.

#### Scenario: One field serves the Hunger goal
- GIVEN `BalanceConfig`
- WHEN its fields are enumerated
- THEN exactly one field represents the daily task goal, referenced by Hunger's calculation

### Requirement: Balance values are injected, never referenced globally
Every domain function that uses a tuned number MUST take `BalanceConfig` (or a specific field of
it) as a parameter. No domain function SHALL read a balance value from a global, companion, or
top-level constant. No balance literal SHALL appear inside any Room `@Query`, SQL string, or
formatter/UI code outside `BalanceConfig`.

#### Scenario: A grep for balance literals outside BalanceConfig finds none
- GIVEN the full repository source after this change
- WHEN it is searched for the numeric literals used by `BalanceConfig`'s defaults outside the
  `BalanceConfig` file and its tests
- THEN no domain function, `@Query`, SQL string, or formatter references the value directly

#### Scenario: Same scenario scored under two configs disagrees
- GIVEN a fixed set of task/occurrence inputs
- WHEN a scoring function is run once with `BalanceConfig(dailyTaskGoal = 10)` and once with
  `BalanceConfig(dailyTaskGoal = 20)`
- THEN the two results differ, proving the function is genuinely parameterised rather than
  hardcoded

### Requirement: BalanceConfig is Hilt-provided
`BalanceConfig` SHALL be provided through Hilt's `DataModule` via `@Provides`, so a future
DataStore-backed override changes no call site.

#### Scenario: BalanceConfig resolves through injection
- GIVEN a Hilt-injected class that declares a `BalanceConfig` dependency
- WHEN the object graph is built
- THEN `DataModule` supplies a `BalanceConfig` instance with no manual construction at the call
  site

### Requirement: BalanceConfig excludes UI/validation caps
Layout and validation constants (e.g. a title length cap) SHALL NOT be added to `BalanceConfig`;
they are declared elsewhere as domain validation constants.

#### Scenario: Title length cap is absent from BalanceConfig
- GIVEN `BalanceConfig`'s field list
- WHEN it is inspected
- THEN no title-length or other UI/validation-cap field is present
