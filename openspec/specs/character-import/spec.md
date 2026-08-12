# character-import

New capability for `slice-2-movable-and-yours` (#39). Defines picking, private-storage copy,
three-tier validation, preview, the character library with its cap, and active-character
switching observed live by the running service.

## Requirements

### Requirement: Picking uses Photo Picker and requires no storage permission
Character selection SHALL use `PickVisualMedia` (Photo Picker). No storage permission SHALL be
requested on any supported OS version.

#### Scenario: Picking a photo prompts no storage permission dialog
- **GIVEN** a user opens the character picker on any supported OS version
- **WHEN** they select an image
- **THEN** no storage-permission dialog is shown at any point in the flow

### Requirement: The picked file is copied to app-private storage before further processing
The selected image SHALL be copied to `filesDir/characters/<uuid>/idle.png`, keyed by a generated
UUID, before any validation step runs. The character is a folder, not a file: one folder per
character with one file per animation, matching the layout `pet-sprite-sheet` requires for bundled
characters. A flat `<uuid>.png` cannot hold a second animation and is not permitted. The source URI
MUST NOT be retained or re-read afterward.

#### Scenario: Source URI is discarded after copy
- **GIVEN** an image has been copied into app-private storage
- **WHEN** the original source is deleted, moved, or renamed afterward
- **THEN** the import pipeline is unaffected because it never reads the source URI again

### Requirement: Validation runs in three tiers, in order, stopping at the first failure
Validation SHALL run: (1) header-only checks (PNG magic bytes, file size ceiling); (2)
bounds-only checks (dimensions, oversize, non-divisibility) with no pixel buffer allocated; (3)
full decode and trailing-transparent-cell scan. A later tier MUST NOT run if an earlier tier fails.

#### Scenario: An oversized image is rejected without a full decode
- **GIVEN** an image whose header-declared bounds exceed the maximum dimension
- **WHEN** it is validated
- **THEN** rejection occurs at the bounds tier and no full-resolution pixel buffer is ever allocated

#### Scenario: A corrupt file is rejected at the header tier
- **GIVEN** a byte stream that is not a valid PNG
- **WHEN** it is validated
- **THEN** rejection occurs at the first tier with a distinct corrupt-file message, never a crash

### Requirement: Every rejection names the specific rule it broke
Rejection messages SHALL state the concrete measured value and the rule violated. A generic
"invalid image" message MUST NOT exist in the codebase.

#### Scenario: An oversized image's message states actual and maximum size
- **GIVEN** a 2200×2200 image against a 2048×2048 maximum
- **WHEN** it is rejected
- **THEN** the message states both the actual dimensions and the maximum allowed

#### Scenario: A non-divisible image's message names the specific dimension problem
- **GIVEN** an image whose width does not divide evenly by its height
- **WHEN** it is rejected
- **THEN** the message states the actual width and that it does not divide evenly

### Requirement: A sheet with fewer animation rows than current states loads, missing states fall back to IDLE
A valid sheet folder missing one or more optional animation files SHALL load successfully. Any
pet state whose animation file is absent SHALL render using the IDLE animation instead of failing
or blocking import.

#### Scenario: A character with only idle.png imports successfully
- **GIVEN** an import producing only `idle.png` in the character's folder
- **WHEN** the character is later used to render a state with no corresponding file
- **THEN** rendering falls back to the IDLE animation rather than failing

### Requirement: The user sees a preview with grid, per-row playback, and row-to-state mapping before confirming
Before an import is finalized, the user SHALL see a preview showing the detected grid, each row
animating, and which state each row maps to.

#### Scenario: Preview is shown before the import commits
- **GIVEN** a sheet has passed all three validation tiers
- **WHEN** the import reaches the confirmation step
- **THEN** the preview renders the grid, per-row playback, and the row-to-state mapping, and the
  import does not commit until the user confirms

### Requirement: Slow validation shows a loading state, never an apparently frozen UI
The full-decode-and-scan tier SHALL surface a loading indicator to the user while it runs.

#### Scenario: A large valid sheet shows a loading indicator during full decode
- **GIVEN** a sheet that passed the bounds tier and requires full decode
- **WHEN** the decode-and-scan tier runs
- **THEN** a loading state is visible for its duration

### Requirement: Built-in and imported characters share one model and one render path
Downstream of validation, an imported character SHALL be indistinguishable from a built-in one to
the renderer and the state resolver, except that built-ins cannot be deleted and do not count
against the character cap.

#### Scenario: An imported character renders through the same path as a built-in
- **GIVEN** one built-in character and one successfully imported character
- **WHEN** each is set active in turn
- **THEN** the rendering and state-resolution code paths exercised are identical for both

### Requirement: A hard cap on character count is enforced with a clear message
The library SHALL enforce a maximum character count, injected as configuration. Attempting to
import beyond the cap SHALL be rejected with a message instructing the user to delete one first.

#### Scenario: Importing at the cap is rejected with guidance
- **GIVEN** the library already holds the maximum configured character count
- **WHEN** the user attempts one more import
- **THEN** the import is rejected with a message naming the cap and instructing deletion of an
  existing character first

### Requirement: Deleting the active character falls back to a built-in without an app relaunch
Deleting the currently active character SHALL switch the active pointer to a built-in character,
and the running foreground service SHALL re-render with the new active character without
requiring an app or service relaunch.

#### Scenario: Deleting the active character updates the live overlay
- **GIVEN** the service is running with an imported character active
- **WHEN** that character is deleted
- **THEN** the active pointer falls back to a built-in and the overlay re-renders that built-in
  without a relaunch

### Requirement: A missing character file at load renders a visibly-broken state, never invisibly
If the active character's file is missing at load time, the renderer SHALL show the visibly-broken
placeholder (per `pet-overlay-rendering`) rather than rendering nothing or crashing.

#### Scenario: Active character file deleted outside the app
- **GIVEN** an active character whose backing file is removed from `filesDir` outside the app
- **WHEN** the service attempts to load it
- **THEN** the visibly-broken placeholder renders; the pet is never invisible and the app never
  crashes

### Requirement: Render size is capped in code, independent of sheet content
The rendered pet's maximum on-screen size SHALL be bounded by a named `:core:domain` constant that
`OverlayWindowParams` derives its window size from. No character's declared dimensions SHALL be
able to increase the rendered size beyond that cap.

#### Scenario: A maximally sized valid sheet still renders within the cap
- **GIVEN** a sheet at the maximum accepted source dimensions
- **WHEN** it is rendered
- **THEN** the on-screen rendered size does not exceed the named render-size cap constant

### Requirement: No character surface is focusable or accepts text input outside the pet's own card
The rendered character SHALL NOT be focusable and SHALL NOT accept text input anywhere outside the
pet's own identity-card UI.

#### Scenario: Overlay window remains non-focusable with any active character
- **GIVEN** any active character, built-in or imported
- **WHEN** the overlay window's `LayoutParams` are inspected
- **THEN** `FLAG_NOT_FOCUSABLE` is set

### Requirement: A persistent identity affordance is always visible
The pet SHALL always display a persistent, non-spoofable identity affordance (badge or name)
regardless of active character.

#### Scenario: Identity affordance renders with every character
- **GIVEN** any active character
- **WHEN** the pet is rendered
- **THEN** the identity affordance is visible

### Requirement: User-facing copy never claims deceptive characters are prevented
No copy in the import or library flow SHALL claim that deceptive or malicious imported content is
prevented or detected.

#### Scenario: Import and library copy is audited for overclaiming
- **GIVEN** all user-facing strings in the import and library flow
- **WHEN** they are reviewed
- **THEN** none of them claims prevention or detection of deceptive imported content
