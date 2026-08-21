# config-override-store Specification

## Purpose

A persisted, validated store of per-field overrides for `BalanceConfig` and `PetAnimationConfig`,
sitting behind the same injection seam both configs are already resolved through. The store lets a
field be overridden individually, keeps every other field on its shipped default, rejects
out-of-range writes with a typed reason, flags overrides written under an older config-group
version, and exposes both configs as observable so a change is visible without restarting the app
or the overlay service. No UI, no new tunable fields, and the four snapshot configs
(`PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`, `QuickMenuConfig`) are
explicitly out of scope for this change — see "Not required" below.

## Requirements

### Requirement: Resolution follows a two-state contract per field
Each overridable field SHALL resolve through exactly one of two states: no entry for that field, or
an entry present for that field. No entry SHALL resolve to the shipped default read fresh from code
at resolution time. An entry present SHALL resolve to that entry's stored value. This is the
complete resolution contract; no third state and no partial state exist.

#### Scenario: A field with no entry resolves to the shipped default (machine-verifiable)
- GIVEN a field for which the store holds no entry
- WHEN the field is resolved
- THEN the result equals the value currently declared as that field's shipped default in code

#### Scenario: A field with an entry resolves to the entry's value (machine-verifiable)
- GIVEN a field for which the store holds an entry with a valid, in-range value
- WHEN the field is resolved
- THEN the result equals the stored value, not the shipped default

#### Scenario: Every field resolves independently of every other field's state (machine-verifiable)
- GIVEN a config with at least two overridable fields, one with an entry and one without
- WHEN both fields are resolved
- THEN the field with an entry resolves to its stored value and the field with no entry resolves to
  its shipped default, in the same resolution pass

### Requirement: An override survives an unrelated shipped-default change; a non-overridden field always picks up the new default
When a field's shipped default changes in code, a field that already carries a stored override
SHALL keep resolving to its stored override, and a field that carries no stored override SHALL
resolve to the new shipped default — never to a default value cached, remembered, or written at any
earlier resolution. This holds per field, independent of what happens to any other field's default
in the same code change. This is the criterion an eager-write store — one that persists every field
whenever any field is written — silently fails, because it turns every field into a stored override
the moment one field is written.

#### Scenario: Overriding one field and changing another field's default leaves both correct (machine-verifiable)
- GIVEN field A has a stored override and field B has no stored override
- WHEN field B's shipped default changes in code
- AND both fields are resolved
- THEN field A still resolves to its stored override, unaffected by the default change
- AND field B resolves to the new shipped default, not to any value that existed before the change

#### Scenario: Writing one field's override leaves every other field with no entry (machine-verifiable)
- GIVEN a config with at least two overridable fields, none currently overridden
- WHEN one field is written with a valid, in-range value
- THEN the store holds an entry for that field only; every other field of the same config has no
  entry

#### Scenario: A later default change reaches an install that never overrode that field (machine-verifiable)
- GIVEN an install that has never written an override for a given field
- WHEN the field's shipped default changes in code and the app is relaunched
- THEN the field resolves to the new shipped default

### Requirement: Reset deletes the entry, never rewrites the current default as a value
Resetting a field SHALL delete its entry from the store. Reset MUST NOT write the field's current
resolved value, current shipped default, or any other explicit value in place of deletion. After
reset, the field SHALL resolve exactly as a field that was never overridden resolves: to the shipped
default read fresh from code.

#### Scenario: Reset removes the entry (machine-verifiable)
- GIVEN a field with a stored override
- WHEN the field is reset
- THEN the store holds no entry for that field

#### Scenario: A reset field tracks later shipped-default changes (machine-verifiable)
- GIVEN a field with a stored override that is then reset
- WHEN the field's shipped default subsequently changes in code
- THEN the field resolves to the new shipped default, proving no value was rewritten at reset time

#### Scenario: A reset field is indistinguishable from a never-overridden field (machine-verifiable)
- GIVEN field X was overridden and then reset, and field Y of the same config was never overridden
- WHEN both fields are resolved
- THEN both resolve to their respective shipped defaults, and the store holds no entry for either

### Requirement: Absence never resolves to zero, and never to a partially-zeroed config
A failed read, an empty read, or a corrupt read of the store SHALL resolve to the complete shipped
defaults for the affected config — every field at its shipped default. It MUST NOT resolve any field
to zero, to an empty/default-constructed value substituted for a missing or unparsable entry, and it
MUST NOT resolve some fields to their shipped defaults while others fall back to zero or another
substituted value. Absence is absence: it carries no information about what value a field should
take, and MUST NOT be interpreted as that value being the type's zero.

#### Scenario: A failed read resolves to complete shipped defaults (machine-verifiable)
- GIVEN the underlying store read fails (e.g. throws or times out)
- WHEN the config is resolved
- THEN every field of the resolved config equals its shipped default; none equals zero or an
  empty-constructed substitute

#### Scenario: An empty store resolves to complete shipped defaults (machine-verifiable)
- GIVEN the store contains no entries at all for a config's fields
- WHEN the config is resolved
- THEN every field equals its shipped default

#### Scenario: A corrupt entry for one field does not zero that field or any other (machine-verifiable)
- GIVEN one field's stored entry is corrupt or fails to parse, while other fields have valid entries
  or no entries
- WHEN the config is resolved
- THEN the corrupt field resolves to its shipped default, and every other field resolves per its own
  entry/no-entry state — no field resolves to zero as a result of the corruption

### Requirement: Validation rejects an out-of-range write; nothing is clamped and nothing is persisted
Each overridable field SHALL declare a valid range. A write whose value falls outside that field's
declared range SHALL be rejected. The rejection SHALL be a typed domain reason identifying the
field, the declared range, and the offending value — not display copy, and not a generic/untyped
error. On rejection, the store MUST NOT persist the offending value, MUST NOT persist a clamped
value substituted for it, and MUST leave any pre-existing entry for that field unchanged.

This SHALL hold at both boundaries of each declared range (an in-range write at the boundary
succeeds; a write one increment beyond the boundary in either direction is rejected).

#### Scenario: A write below the minimum is rejected (machine-verifiable)
- GIVEN a field with a declared minimum
- WHEN a write is attempted with a value one increment below that minimum
- THEN the write is rejected with a typed reason naming the field, the range, and the offending
  value, and no entry is persisted

#### Scenario: A write above the maximum is rejected (machine-verifiable)
- GIVEN a field with a declared maximum
- WHEN a write is attempted with a value one increment above that maximum
- THEN the write is rejected with a typed reason naming the field, the range, and the offending
  value, and no entry is persisted

#### Scenario: A write exactly at the minimum boundary succeeds (machine-verifiable)
- GIVEN a field with a declared minimum
- WHEN a write is attempted with a value exactly equal to that minimum
- THEN the write succeeds and the field resolves to that value

#### Scenario: A write exactly at the maximum boundary succeeds (machine-verifiable)
- GIVEN a field with a declared maximum
- WHEN a write is attempted with a value exactly equal to that maximum
- THEN the write succeeds and the field resolves to that value

#### Scenario: A rejected write leaves a pre-existing override untouched (machine-verifiable)
- GIVEN a field already holds a valid stored override
- WHEN a subsequent out-of-range write is attempted for that field
- THEN the write is rejected and the field still resolves to its prior stored override, unchanged

#### Scenario: This holds for every declared range in both configs (machine-verifiable)
- GIVEN each overridable field of `BalanceConfig` and `PetAnimationConfig` that declares a range
- WHEN each field's minimum, maximum, one-below-minimum, and one-above-maximum are exercised
- THEN every in-range boundary succeeds and every out-of-range value is rejected with a typed reason

### Requirement: Each field's valid range is declared once, in the domain, beside the field
Every overridable field's valid range SHALL be declared in exactly one place in `:core:domain`,
associated with that field's declaration. Any code path that writes a value for that field —
this store's write API, or any future writer — SHALL enforce that same declared range through the
same validation logic. No second, independently-maintained copy of a range SHALL exist.

#### Scenario: A single declared range governs every write path (machine-verifiable)
- GIVEN a field's declared range
- WHEN a write is attempted through the store's write API
- THEN the same range values used by the resolution/validation logic are the ones enforced — no
  divergent range is applied

### Requirement: Both BalanceConfig and PetAnimationConfig are observable without a restart
Resolved values for `BalanceConfig` and `PetAnimationConfig` SHALL be exposed such that a write or a
reset for either config is visible to already-running consumers without restarting the app process
or the overlay service. Pure domain functions that consume a config SHALL continue to take a plain,
non-observable snapshot value as a parameter; observability SHALL be introduced at the
provider/source level, not by changing pure function signatures.

#### Scenario: A write to BalanceConfig is visible without a restart (machine-verifiable)
- GIVEN a running consumer observing `BalanceConfig`
- WHEN a field is written with a new valid value
- THEN the consumer observes the updated resolved config without the app or the overlay service
  restarting

#### Scenario: A write to PetAnimationConfig is visible without a restart (machine-verifiable)
- GIVEN a running consumer observing `PetAnimationConfig`
- WHEN a field is written with a new valid value
- THEN the consumer observes the updated resolved config without the app or the overlay service
  restarting

#### Scenario: A reset is visible without a restart (machine-verifiable)
- GIVEN a running consumer observing a config with a field currently overridden
- WHEN that field is reset
- THEN the consumer observes the field return to its shipped default without a restart

#### Scenario: Pure domain functions keep taking a plain snapshot parameter (machine-verifiable)
- GIVEN a pure domain function that already consumes `BalanceConfig` or `PetAnimationConfig`
- WHEN this change is applied
- THEN the function's parameter type is unchanged — a plain snapshot value, not an observable
  wrapper

### Requirement: Version staleness is tracked per override group, never globally
Each override SHALL record the exact version of its own config group at the time it was written —
not merely a boolean indicating staleness, and not a single global version shared across config
groups. `BalanceConfig` and `PetAnimationConfig` SHALL each carry their own, independent versioning
notion; a version bump belonging to one group SHALL NOT affect the staleness of overrides belonging
to the other group.

An override whose recorded version differs from its group's current version SHALL be flagged as
stale. A stale override SHALL be kept and SHALL continue to resolve normally (per the two-state
resolution contract above) — staleness MUST NOT cause the override to be discarded, and MUST NOT
cause it to be silently treated as current with no marker distinguishing it.

#### Scenario: An override records the version it was written under (machine-verifiable)
- GIVEN a field is written while its config group is at a given version
- WHEN the resulting entry is inspected
- THEN it records that exact version, not merely a stale/fresh boolean

#### Scenario: A group version bump flags only that group's overrides as stale (machine-verifiable)
- GIVEN overrides exist for both `BalanceConfig` and `PetAnimationConfig`
- WHEN `BalanceConfig`'s version is bumped
- THEN the `BalanceConfig` overrides are flagged stale relative to the new version
- AND the `PetAnimationConfig` overrides remain unflagged, unaffected by the unrelated bump

#### Scenario: A stale override is kept and keeps resolving, not discarded (machine-verifiable)
- GIVEN a field's stored override was written under an older version than the config's current
  version
- WHEN the field is resolved
- THEN the result equals the stored override's value, and the entry still exists in the store
  afterward

#### Scenario: A stale override is distinguishable from a fresh one (machine-verifiable)
- GIVEN one override written under the current version and another written under an older version
  of the same config group
- WHEN both are inspected
- THEN the older one is identifiable as stale and the current one is not, without discarding either

### Requirement: No whole-config write exists; writing one field never touches another
The store's write surface SHALL only accept a single field's identity and value per write
operation. No API SHALL accept and persist an entire config object in one call. Writing one field
SHALL NOT create, modify, or delete the entry of any other field, in the same config or in a
different config.

#### Scenario: Writing a field does not create entries for untouched fields (machine-verifiable)
- GIVEN a config with several overridable fields, all currently without entries
- WHEN one field is written
- THEN only that field has an entry afterward; every other field still has none

#### Scenario: Writing a field in one config does not touch the other config (machine-verifiable)
- GIVEN both `BalanceConfig` and `PetAnimationConfig` have no overrides
- WHEN a field in `BalanceConfig` is written
- THEN `PetAnimationConfig` still has no overrides

## Not required (explicitly out of scope for this change)

- No UI of any kind — no settings row, no tuning panel, no debug entry point.
- No new tunable/overridable fields beyond the ones `BalanceConfig` and `PetAnimationConfig`
  already declare.
- No migration of the existing `private const val` declarations into a new constants object; they
  remain the shipped defaults, unmoved.
- No remote or file-based configuration source; persistence is local only.
- The four snapshot configs — `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`,
  `QuickMenuConfig` — are not routed through the store by this change. They keep their current bare
  providers. This is a tracked, named deviation from issue #91's original criterion, with the
  follow-up work recorded in the proposal.
