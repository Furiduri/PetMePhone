# pet-sprite-sheet

Delta spec for the change `slice-1-pet-on-screen` (issue #36, PR 1). Defines the sprite sheet
layout contract, header-first validation, safe decoding rules, and the typed decode result. This
is a new capability — no existing spec covers it.
Scope boundary: quality tiers, `inSampleSize` targeting, runtime tier change/re-decode, and
user-facing import warnings are OUT of this change and belong to slice 2 (#39). This spec models
the full six-row grid in its types even though only row 0 is rendered in this change.

## ADDED Requirements

### Requirement: The sheet is a single image with no manifest and no embedded metadata
A sprite sheet SHALL be exactly one image file. The loader SHALL NOT read or require any
companion manifest file, and SHALL NOT read PNG ancillary chunks (`tEXt`/`iTXt` or otherwise) for
layout information. Layout is derived only from pixel dimensions.

#### Scenario: Loading ignores any embedded metadata
- **GIVEN** a sheet whose PNG `tEXt` chunks contain layout data
- **WHEN** the sheet is loaded
- **THEN** the loader never reads chunk data, and layout comes only from image dimensions and the
  fixed row table

### Requirement: The row table is fixed and models all six states
The grid SHALL have exactly six rows in this fixed order: 0 IDLE, 1 DRAGGING, 2 HUNGRY, 3 HAPPY,
4 SLEEPING, 5 TYPING. The domain type representing a decoded sheet SHALL expose all six rows,
even though only row 0 (IDLE) is drawn by this change.

#### Scenario: Decoded sheet type exposes six rows
- **GIVEN** a successfully decoded sheet
- **WHEN** its row collection is inspected
- **THEN** it contains exactly six entries in the fixed IDLE/DRAGGING/HUNGRY/HAPPY/SLEEPING/TYPING
  order, regardless of which rows this change renders

### Requirement: Cell size is uniform and derived only by division
Cell width and height SHALL be derived by dividing total image width by column count and total
image height by six rows. The sheet's pixel dimensions MUST divide evenly by the grid in both
axes; a sheet that does not divide evenly is invalid.

#### Scenario: Evenly divisible sheet is accepted
- **GIVEN** an image whose width and height both divide evenly across the grid
- **WHEN** the sheet is validated
- **THEN** validation succeeds and cell size is derived by division alone

#### Scenario: Non-divisible dimensions are rejected
- **GIVEN** an image whose width or height does not divide evenly by the grid
- **WHEN** the sheet is validated
- **THEN** validation fails with a distinct non-divisible-dimensions failure, before any decode

### Requirement: Oversized sheets are rejected at header read, before any pixel allocation
The loader SHALL read only image bounds (`inJustDecodeBounds`-equivalent, zero-allocation header
read) before deciding whether to decode. Any sheet wider or taller than 2048×2048 SHALL be
rejected at this header stage; no bitmap allocation SHALL occur for a rejected sheet.

#### Scenario: An oversized sheet is rejected without decoding
- **GIVEN** an 8000×8000 image
- **WHEN** the sheet is loaded
- **THEN** the header is read, the sheet is rejected as oversized, and no full-resolution decode
  or bitmap allocation ever occurs

#### Scenario: A sheet at the size boundary is accepted
- **GIVEN** an image exactly 2048×2048
- **WHEN** the sheet is loaded and its dimensions otherwise divide evenly
- **THEN** header validation accepts the size and decoding proceeds

### Requirement: Decoded sheets are ARGB_8888, one resident bitmap, never hardware bitmaps
A successfully decoded sheet SHALL produce exactly one resident `Bitmap` in `ARGB_8888` config.
The decoder MUST NOT produce or accept a `Bitmap.Config.HARDWARE` bitmap.

#### Scenario: Successful decode yields one ARGB_8888 bitmap
- **GIVEN** a valid, in-bounds sheet
- **WHEN** it is decoded
- **THEN** exactly one resident bitmap exists, its config is `ARGB_8888`, and it is never a
  hardware bitmap

### Requirement: Trailing fully-transparent cells clamp a row's frame count
Within a row, a scan for fully-transparent cells from the last cell backward SHALL determine that
row's usable frame count: a trailing run of fully-transparent cells is excluded from the frame
count, and the first non-fully-transparent cell from the end fixes it.

#### Scenario: A row with trailing transparent cells reports fewer frames
- **GIVEN** an 8-cell row where the last 4 cells are fully transparent
- **WHEN** the sheet is decoded
- **THEN** that row's frame count is 4

#### Scenario: A row with no transparent cells uses the full column count
- **GIVEN** a row where no cell is fully transparent
- **WHEN** the sheet is decoded
- **THEN** that row's frame count equals the sheet's column count

### Requirement: A sheet that fails to decode is modelled as an explicit failure, never a blank or missing pet
The sheet-loading result type SHALL be a sum type with a success case (holding the decoded
bitmap and row/frame data) and one or more explicit failure cases (oversized, non-divisible,
corrupt/undecodable). There SHALL be no representation of "no sheet" that silently renders as
blank, empty, or a default built-in character.

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
- Any behavior for rows 1–5 beyond appearing in the fixed row table
