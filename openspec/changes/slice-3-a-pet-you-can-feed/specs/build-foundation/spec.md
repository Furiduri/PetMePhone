# Delta for build-foundation

Delta spec for the change `slice-3-a-pet-you-can-feed` (issue #23). Adds the `room-testing`
dependency to the centralized version-catalog/`ProjectConfig` build surface.

## ADDED Requirements

### Requirement: room-testing is a version-catalog entry, not an ad hoc literal
The `room-testing` artifact SHALL be declared in `gradle/libs.versions.toml`, coupled to the same
Room version already used elsewhere, and consumed by `:core:data`'s test source set through the
catalog reference — never as a hand-written literal coordinate in a module's `build.gradle.kts`.

#### Scenario: room-testing resolves through the catalog
- GIVEN `gradle/libs.versions.toml` after this change
- WHEN it is inspected
- THEN a `room-testing` entry exists referencing the same `version.ref` as the other Room
  artifacts

#### Scenario: No literal room-testing coordinate in a module script
- GIVEN `:core:data`'s `build.gradle.kts`
- WHEN its `dependencies {}` block is inspected
- THEN `room-testing` is referenced via `libs.room.testing` (or equivalent catalog alias), with no
  hardcoded group:artifact:version string
