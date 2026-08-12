# pet-overlay-rendering

Delta spec for `slice-2-movable-and-yours`. Modifies the renderer, previously scoped to the idle
animation only, to draw the state resolved by `pet-state-resolution` and to source sprite sheets
from the active character's folder rather than a hardcoded bundled path.

## MODIFIED Requirements

### Requirement: Only the idle animation is loaded and drawn in this change
The renderer SHALL draw frames from the animation corresponding to the `PetState` currently
emitted by `PetStateResolver` (see `pet-state-resolution`), sourced from the active character's
folder (see `pet-sprite-sheet`). If the active character's folder lacks the file for the resolved
state, the renderer SHALL draw the IDLE animation for that character instead. The renderer SHALL
NOT load or draw an animation belonging to any character other than the currently active one.
(Previously: the renderer drew exclusively from a hardcoded `idle.png`, with all other states out
of scope.)

#### Scenario: Renderer draws the resolved state's animation
- **GIVEN** the resolver currently emits DRAGGING and the active character has a `dragging.png`
  file
- **WHEN** the pet is composed on screen
- **THEN** frames are drawn from `dragging.png`, not from `idle.png`

#### Scenario: A resolved state with no corresponding file falls back to idle
- **GIVEN** the resolver emits a state whose animation file is absent from the active character's
  folder
- **WHEN** the pet is composed on screen
- **THEN** frames are drawn from the active character's `idle.png` instead

#### Scenario: Switching the active character changes what is drawn without a relaunch
- **GIVEN** the pet is rendering one active character
- **WHEN** the active character changes (see `character-import`)
- **THEN** the renderer begins drawing frames from the new active character's folder without an
  app or service relaunch

## Out of Scope

Unchanged from slice 1, narrowed further: `CelebrationTracker`-driven HAPPY playback, TYPING, and
metric-driven HUNGRY/SLEEPING animation selection remain OUT — their providers are not registered
in this slice (see `pet-state-resolution`). Cross-fade/interpolation between states and reactive
animation (squash, drag physics, particles, #38) remain OUT.
