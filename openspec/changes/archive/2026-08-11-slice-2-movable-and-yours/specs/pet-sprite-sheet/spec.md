# pet-sprite-sheet

Delta spec for `slice-2-movable-and-yours`. Adds a filesystem asset source alongside the bundled
`assets/` source, and confirms the fewer-animations-than-states fallback rule for characters
imported at runtime.

## ADDED Requirements

### Requirement: Character folders may be sourced from app-private filesystem storage
In addition to the bundled `assets/pet/<character>/` source, the loader SHALL accept character
folders rooted at `filesDir/characters/<uuid>/` for imported characters. The layout rules (one
image per animation, `idle.png` required, other filenames optional) apply identically to both
sources.

#### Scenario: A filesystem-sourced character loads under the same rules as a bundled one
- **GIVEN** a character folder under `filesDir/characters/<uuid>/` containing a valid `idle.png`
- **WHEN** the character's assets are loaded
- **THEN** the same layout, validation, and decode rules apply as for a bundled character, and
  loading succeeds

### Requirement: A missing animation file at the active source is an ordinary valid absence, not corruption
For a character loaded from either source, an absent optional animation filename SHALL be treated
as a valid, ordinary state — never as sheet corruption or a decode failure — consistent with the
slice-1 rule for the bundled source.

#### Scenario: A filesystem-sourced character missing an optional animation is still valid
- **GIVEN** a `filesDir/characters/<uuid>/` folder containing only `idle.png`
- **WHEN** the character's assets are inspected
- **THEN** the character is valid, and the missing optional animations are modelled as absent, not
  as an error
