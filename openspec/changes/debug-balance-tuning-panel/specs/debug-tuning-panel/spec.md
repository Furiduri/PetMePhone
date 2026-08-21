# debug-tuning-panel Specification

## Purpose

A debug-variant-only instrument, compiled exclusively under `app/src/debug/`, that enumerates the
eight registered config fields (`BalanceConfig.ALL` and `PetAnimationConfig.ALL`), shows each row's
shipped default, current value, overridden state, staleness state, and live/deferred label, writes
and resets exclusively through `ConfigOverrideStore`, and restarts the overlay service. The panel
consumes `config-override-store`'s existing resolution, validation, reset, and observability
guarantees; it does not restate or alter them. No user ever sees this panel, and it is verifiably
absent from the release artifact.

## Requirements

### Requirement: The panel exists only in the debug build and is verifiably absent from release
The panel, its entry point, and every string or resource it uses SHALL be reachable only from a
debug build. A release build SHALL contain no class, string, or resource belonging to the panel,
verified by inspecting the built release artifact rather than by reading source-set boundaries or
build configuration. No runtime check gating the panel's visibility or behaviour on a debug/release
distinction SHALL exist anywhere in this change; the absence itself, not a guard, is the requirement.

#### Scenario: A release build's artifact contains no panel code (machine-verifiable)
- GIVEN a release build of the app has been assembled
- WHEN the built release artifact is inspected for any class, string, or resource belonging to the
  panel
- THEN none is found

#### Scenario: A debug build's artifact contains the panel (machine-verifiable)
- GIVEN a debug build of the app has been assembled
- WHEN the built debug artifact is inspected for the panel's entry-point class
- THEN it is found

#### Scenario: No debug/release runtime check exists in the panel's code (machine-verifiable)
- GIVEN the panel's source files
- WHEN searched for a runtime debug/release distinction (e.g. a build-configuration flag check)
- THEN none is found; the panel's presence depends only on which sources were compiled

### Requirement: A second launcher icon exists on a debug install and not on a release install
A debug install of the app SHALL present two `MAIN`/`LAUNCHER` entries on the device's launcher: the
existing app entry point and the panel's entry point. A release install SHALL present only the
existing app entry point.

#### Scenario: A debug install shows two launcher entries (machine-verifiable)
- GIVEN the app is installed from a debug build
- WHEN the device's launcher entries for the app's package are enumerated
- THEN two `MAIN`/`LAUNCHER` activities are present: the app's main entry point and the panel's
  entry point

#### Scenario: A release install shows one launcher entry (machine-verifiable)
- GIVEN the app is installed from a release build
- WHEN the device's launcher entries for the app's package are enumerated
- THEN exactly one `MAIN`/`LAUNCHER` activity is present, and it is not the panel's entry point

### Requirement: Every registered field is enumerated, including any the settings screen excludes
The panel SHALL enumerate all five fields of `BalanceConfig.ALL` and all three fields of
`PetAnimationConfig.ALL` — eight rows total — regardless of whether any of them is shown, hidden, or
excluded on the user-facing settings screen. The panel SHALL NOT enumerate, reference, or otherwise
mention `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`, and
SHALL show no empty state, note, or placeholder regarding their absence.

#### Scenario: All eight registered fields appear as rows (machine-verifiable)
- GIVEN the panel is opened
- WHEN its rows are enumerated
- THEN exactly one row exists for each of the five `BalanceConfig.ALL` fields and each of the three
  `PetAnimationConfig.ALL` fields, and no other field appears

#### Scenario: A field the settings screen excludes still appears in the panel (machine-verifiable)
- GIVEN a `BalanceConfig.ALL` field that the user-facing settings screen does not expose
- WHEN the panel's rows are enumerated
- THEN a row exists for that field, shown the same as any other row

#### Scenario: The panel names none of the four unreachable configs (machine-verifiable)
- GIVEN the panel is opened
- WHEN its visible text and semantics are inspected
- THEN no reference to `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`, or
  `QuickMenuConfig` — by name, note, or placeholder — is present

### Requirement: Every row shows shipped default, current value, and overridden state, distinctly
Each row SHALL display three separate pieces of information: the field's shipped default read from
code, the field's current resolved value, and whether a stored override exists for that field. These
SHALL be visually or structurally distinguishable from one another, so that a value equal to the
shipped default by coincidence is never indistinguishable from a value equal to the shipped default
because no override was ever written.

#### Scenario: A never-overridden field shows default and current as equal, with no override marker (machine-verifiable)
- GIVEN a field with no stored override
- WHEN its row is inspected
- THEN the shipped default and current value are shown and are equal, and the row indicates no
  override is present

#### Scenario: An overridden field distinguishes its default from its current value (machine-verifiable)
- GIVEN a field with a stored override differing from its shipped default
- WHEN its row is inspected
- THEN the shipped default shown is the value from code, the current value shown is the stored
  override, and the row indicates an override is present

#### Scenario: An override coincidentally equal to the default is still marked overridden (machine-verifiable)
- GIVEN a field's stored override happens to equal its shipped default value
- WHEN its row is inspected
- THEN the row still indicates an override is present, distinguishing it from a never-overridden
  field showing the same numbers

### Requirement: Staleness renders as exactly one of three distinct states per row
Each row SHALL report its staleness as exactly one of three distinct states: **fresh** (a stored
override exists and its recorded version matches its config group's current version, or no override
exists), **stale** (a stored override exists and its recorded version differs from its config
group's current version), or **not versioned** (the field's config group has no current version to
compare against). A not-versioned row SHALL NOT be reported as fresh, and SHALL NOT be reported as
stale.

#### Scenario: An override under the current version renders as fresh (machine-verifiable)
- GIVEN a `BalanceConfig` field whose stored override was written under the group's current version
- WHEN its row's staleness state is inspected
- THEN it reads fresh

#### Scenario: An override under an older version renders as stale (machine-verifiable)
- GIVEN a `BalanceConfig` field whose stored override was written under a version older than the
  group's current version
- WHEN its row's staleness state is inspected
- THEN it reads stale

#### Scenario: Every PetAnimationConfig row renders as not versioned (machine-verifiable)
- GIVEN any `PetAnimationConfig` field, overridden or not
- WHEN its row's staleness state is inspected
- THEN it reads not versioned, never fresh and never stale

#### Scenario: A not-versioned row is never reported as fresh (machine-verifiable)
- GIVEN a field belonging to a config group with no current version
- WHEN its row's staleness state is computed
- THEN the result is the distinct not-versioned state, and asserting the row reads "fresh" fails

### Requirement: Every row carries a live/deferred label, rendered even when uniform
Each row SHALL display whether a write to that field takes effect immediately (live) or only after
the overlay service restarts (deferred). This label SHALL be computed and rendered per row, even on
a set of rows where every row currently reads the same value.

#### Scenario: Each of the eight rows carries a live/deferred label (machine-verifiable)
- GIVEN the panel is opened
- WHEN each of the eight rows is inspected
- THEN each carries an explicit live-or-deferred label; none is missing the label

#### Scenario: All eight rows currently read live (machine-verifiable)
- GIVEN both `BalanceConfig` and `PetAnimationConfig` are observable as of the config-override-store
  change
- WHEN each row's live/deferred label is inspected
- THEN every row reads live

### Requirement: A write goes through the store and is subject to its declared range
Writing a row's value SHALL call `ConfigOverrideStore.set` for that field and no other write path.
An in-range write SHALL succeed and the row SHALL reflect the new stored value. An out-of-range
write SHALL be rejected by the store; the panel SHALL surface the store's typed rejection reason
(field, declared range, offending value) as its own wording, SHALL NOT persist anything, and SHALL
NOT alter any pre-existing entry for that field.

#### Scenario: An in-range write updates the row (machine-verifiable)
- GIVEN a row for a field with a declared range
- WHEN a value within that range is written through the panel
- THEN `ConfigOverrideStore.set` is called with that field and value, the write succeeds, and the
  row's current value and override state update to reflect it

#### Scenario: An out-of-range write is rejected with the store's typed reason (machine-verifiable)
- GIVEN a row for a field with a declared range
- WHEN a value outside that range is written through the panel
- THEN the store rejects the write, and the panel displays the field, its declared range, and the
  offending value as the rejection reason

#### Scenario: An out-of-range write leaves the typed value on screen instead of reverting (machine-verifiable)
- GIVEN a row showing a current value of X
- WHEN an out-of-range value Y is typed and submitted
- THEN the row continues to display Y, not X, alongside the expected range — the field does not
  revert to the value the app is still using

#### Scenario: An out-of-range write does not touch a pre-existing override (machine-verifiable)
- GIVEN a field already holds a valid stored override
- WHEN an out-of-range write is attempted for that field through the panel
- THEN the store's entry for that field is unchanged, and the app continues resolving the field to
  the pre-existing override

### Requirement: Per-field reset deletes the entry without confirmation
Resetting a single row SHALL call `ConfigOverrideStore.reset` for that field and no other path,
without a confirmation prompt. After reset, the field SHALL resolve, and the row SHALL display,
exactly as a field that was never overridden does.

#### Scenario: Per-field reset removes the entry immediately (machine-verifiable)
- GIVEN a row for a field with a stored override
- WHEN the row's reset control is activated
- THEN no confirmation prompt is shown, `ConfigOverrideStore.reset` is called for that field, and
  the entry is deleted

#### Scenario: A reset row is indistinguishable from a never-overridden row (machine-verifiable)
- GIVEN field X was overridden and then reset through the panel, and field Y of the same config was
  never overridden
- WHEN both rows are inspected
- THEN both show their shipped default as the current value and both indicate no override present

### Requirement: Reset-all deletes every entry and requires confirmation first
A reset-all control SHALL exist. Activating it SHALL first require an explicit confirmation step;
only on confirmation SHALL it call `ConfigOverrideStore.reset` for every one of the eight enumerated
fields that currently holds an entry. Declining the confirmation SHALL leave every entry unchanged.

#### Scenario: Reset-all asks for confirmation before deleting anything (machine-verifiable)
- GIVEN one or more of the eight fields hold stored overrides
- WHEN the reset-all control is activated
- THEN a confirmation step is presented and no entry is deleted before it is answered

#### Scenario: Confirming reset-all deletes every override (machine-verifiable)
- GIVEN several of the eight fields hold stored overrides
- WHEN reset-all is activated and the confirmation is accepted
- THEN every one of those fields' entries is deleted, and each resolves to its shipped default
  afterward

#### Scenario: Declining reset-all's confirmation changes nothing (machine-verifiable)
- GIVEN one or more of the eight fields hold stored overrides
- WHEN reset-all is activated and the confirmation is declined
- THEN every entry that existed before remains unchanged

### Requirement: A live field's change is visible on the running overlay without leaving the panel
When a live-labelled field is written or reset through the panel, the running overlay SHALL reflect
the new resolved value without the panel needing to be closed and without the overlay service
restarting.

#### Scenario: Writing a live field updates the running overlay immediately (machine-verifiable)
- GIVEN the overlay service is running and observing a live-labelled field
- WHEN that field is written to a new in-range value through the panel, and the panel remains open
- THEN the running overlay observes the updated value without the overlay service restarting

#### Scenario: Resetting a live field updates the running overlay immediately (machine-verifiable)
- GIVEN the overlay service is running and observing a live-labelled field that currently holds an
  override
- WHEN that field is reset through the panel, and the panel remains open
- THEN the running overlay observes the field return to its shipped default without the overlay
  service restarting

### Requirement: The panel can restart the overlay service
A restart control SHALL exist that stops and then starts the overlay service, using the same launch
intent shape the app's own overlay-start path uses. This SHALL add no new lifecycle method to the
overlay service; the panel drives the service's existing start/stop entry points.

#### Scenario: Activating restart stops then starts the overlay service (machine-verifiable)
- GIVEN the overlay service is running
- WHEN the panel's restart control is activated
- THEN the overlay service is stopped and then started again, and it is running afterward

#### Scenario: A restarted overlay picks up deferred changes (machine-verifiable)
- GIVEN a deferred-labelled field was changed while the overlay service was running
- WHEN the panel's restart control is activated
- THEN the restarted overlay resolves that field to its newly written value

## Not required (explicitly out of scope for this change)

- No `BuildConfig.DEBUG` runtime check anywhere, including as additional safety alongside the source
  set gate.
- No product flavor and no custom build type.
- No feature flags; the panel tunes values, it does not switch behaviour on and off.
- No data seeding or fake task generation.
- No metric override — the panel writes only the values that produce a metric, never the metric
  itself.
- No entry point, string, or resource of the panel in `app/src/main` or any shared/release source.
- No extension of `ConfigOverrideStore`, `ConfigField`, or `ConfigResolution` to `PetStateConfig`,
  `CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`; those remain unreachable
  from this panel.
- No second write path, no raw persistence, and no validation logic duplicated outside
  `ConfigOverrideStore`.
