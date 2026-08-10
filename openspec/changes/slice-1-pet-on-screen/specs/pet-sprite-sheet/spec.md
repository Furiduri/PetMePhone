# pet-sprite-sheet

Delta spec for the change `slice-1-pet-on-screen` (issue #36, PR 1), corrected by
`feat/sheet-per-animation` to the one-sheet-per-animation contract. Defines the sprite sheet
layout contract, header-first validation, safe decoding rules, and the typed decode result. This
is a new capability — no existing spec covers it.
Scope boundary: quality tiers, `inSampleSize` targeting, runtime tier change/re-decode, and
user-facing import warnings are OUT of this change and belong to slice 2 (#39). On-demand loading
of non-IDLE animations, an LRU cache, eviction, and falling back to IDLE for a missing animation
are also OUT of this change and belong to state resolution (#37, slice 2): today the app has only
one state, so there is nothing to load on demand yet.

## ADDED Requirements

### Requirement: One image is one animation — no row grid, no manifest, no embedded metadata
A sprite sheet SHALL be exactly one image file representing exactly one animation: a single row of
square frames. The loader SHALL NOT read or require any companion manifest file, and SHALL NOT
read PNG ancillary chunks (`tEXt`/`iTXt` or otherwise) for layout information. Layout is derived
only from the image's own pixel dimensions — no fixed row count, no `6`.

#### Scenario: Loading ignores any embedded metadata
- **GIVEN** a sheet whose PNG `tEXt` chunks contain layout data
- **WHEN** the sheet is loaded
- **THEN** the loader never reads chunk data, and layout comes only from the image's own width and
  height

### Requirement: Layout on disk is a folder per character with fixed animation filenames
Assets SHALL be laid out as one folder per character, containing fixed animation filenames inside
it (for example `pet/<character>/idle.png`). The filename identifies the animation; identity comes
from folder position, not from a token embedded in the name. `idle.png` SHALL be required for a
character; every other animation filename SHALL be optional, and its absence SHALL be modelled as
an ordinary, valid state — never an error and never treated as sheet corruption.

#### Scenario: A character with only idle.png is valid
- **GIVEN** a character folder containing only `idle.png`
- **WHEN** the character's assets are inspected
- **THEN** the character is valid; the missing optional animations are absent, not an error

### Requirement: Cell size is uniform and derived only by division
Cell width and height SHALL be square and derived by the image's own height: cell side = image
height. Frame count SHALL be derived by dividing image width by image height. The sheet's pixel
width MUST divide evenly by its height; a sheet that does not divide evenly is invalid. A
remainder is never truncated to a whole frame count — silently dropping partial-column pixels is
exactly the mis-animation this rule exists to prevent.

#### Scenario: Evenly divisible sheet is accepted
- **GIVEN** an image whose width divides evenly by its own height
- **WHEN** the sheet is validated
- **THEN** validation succeeds and cell size and frame count are derived by division alone

#### Scenario: Non-divisible dimensions are rejected
- **GIVEN** an image whose width does not divide evenly by its own height
- **WHEN** the sheet is validated
- **THEN** validation fails with a distinct non-divisible-dimensions failure, before any decode

### Requirement: Oversized sheets are rejected at header read, before any pixel allocation
The loader SHALL read only image bounds (`inJustDecodeBounds`-equivalent, zero-allocation header
read) before deciding whether to decode. Any sheet wider or taller than the injected maximum
dimension (2048px today) SHALL be rejected at this header stage; no bitmap allocation SHALL occur
for a rejected sheet. A non-positive height SHALL also be rejected at this stage, before any
division is attempted.

#### Scenario: An oversized sheet is rejected without decoding
- **GIVEN** an 8000×8000 image
- **WHEN** the sheet is loaded
- **THEN** the header is read, the sheet is rejected as oversized, and no full-resolution decode
  or bitmap allocation ever occurs

#### Scenario: A sheet at the size boundary is accepted
- **GIVEN** an image exactly 2048px tall or narrower, whose width divides evenly by its height
- **WHEN** the sheet is loaded
- **THEN** header validation accepts the size and decoding proceeds

### Requirement: Decoded sheets are ARGB_8888, one resident bitmap, never hardware bitmaps
A successfully decoded sheet SHALL produce exactly one resident `Bitmap` in `ARGB_8888` config.
The decoder MUST NOT produce or accept a `Bitmap.Config.HARDWARE` bitmap.

#### Scenario: Successful decode yields one ARGB_8888 bitmap
- **GIVEN** a valid, in-bounds sheet
- **WHEN** it is decoded
- **THEN** exactly one resident bitmap exists, its config is `ARGB_8888`, and it is never a
  hardware bitmap

### Requirement: Trailing fully-transparent cells clamp the frame count
A scan for fully-transparent cells from the sheet's last cell backward SHALL determine the sheet's
usable frame count: a trailing run of fully-transparent cells is excluded from the frame count,
and the first non-fully-transparent cell from the end fixes it.

#### Scenario: A sheet with trailing transparent cells reports fewer frames
- **GIVEN** an 8-cell sheet where the last 4 cells are fully transparent
- **WHEN** the sheet is decoded
- **THEN** its frame count is 4

#### Scenario: A sheet with no transparent cells uses the full column count
- **GIVEN** a sheet where no cell is fully transparent
- **WHEN** the sheet is decoded
- **THEN** its frame count equals the sheet's column count

### Requirement: A sheet that fails to decode is modelled as an explicit failure, never a blank or missing pet
The sheet-loading result type SHALL be a sum type with a success case (holding the decoded bitmap
and frame data) and one or more explicit failure cases (oversized, non-divisible, corrupt/
undecodable, empty after the transparent-cell clamp). There SHALL be no representation of "no
sheet" that silently renders as blank, empty, or a default built-in character.

#### Scenario: A corrupt PNG produces an explicit failure result
- **GIVEN** a byte stream that is not a valid PNG
- **WHEN** the sheet is loaded
- **THEN** the result is the explicit failure case, never null, never an empty/blank success, and
  never a silent substitution of a built-in sheet

## Out of Scope

The following are explicitly not covered by this spec and are deferred to slice 2:
- Quality tier selection and `inSampleSize`-based downscale targeting
- Runtime tier change triggering re-decode
- User-facing import warning messages (including the no-alpha-channel case)
- On-demand loading of any animation other than idle, an LRU cache, eviction, and falling back to
  idle for a missing animation (state resolution is #37)
