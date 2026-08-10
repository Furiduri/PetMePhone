# Proposal: Slice 1 — A pet on screen (#36, IDLE row only)

## Intent

The overlay window works and Compose provably renders in it, but it draws a magenta placeholder.
Slice 1 is only closed when a real pet idles over other apps. This change delivers the smallest
honest cut of #36: the sprite sheet format contract, a safe decoder, and a zero-allocation renderer
driven by a manual clock — for **row 0 (IDLE) only**.

## Scope

### In Scope
- Sheet format contract: one image, no manifest, no PNG chunk metadata; fixed row order; uniform
  cells derived by division; trailing fully-transparent cells clamp the row's frame count.
- Header-first validation via `inJustDecodeBounds`: reject >2048×2048 and non-divisible dimensions
  **before** any allocation. Decode to `ARGB_8888`, one resident bitmap, no hardware bitmaps.
- `SpriteSheet` result type with an explicit failure case; a visibly-broken placeholder rendered on
  failure (never blank, never a silent built-in fallback).
- IDLE renderer: `drawImage` with source/destination rects against the resident bitmap, zero bitmap
  allocations per frame.
- Manual interval clock in a `LaunchedEffect`, frame interval injected (not a literal), **suspended**
  by a `StateFlow<Boolean>` screen-on signal owned in `:feature:overlay`.
- **Spike first**: the screen-off frame-clock question `docs/build-order.md` schedules "during
  slice 1" is resolved here, empirically on the emulator, before the clock design is committed.
  The finding is written back to #36.

### Out of Scope (deferred to slice 2)
- Quality tiers, `inSampleSize` targeting, runtime tier change and re-decode.
- Rows 1–5 (`DRAGGING`, `HUNGRY`, `HAPPY`, `SLEEPING`, `TYPING`) and state resolution (#37).
- User character import and its import-time warnings, including the no-alpha case (#39).
- Reactive animation — squash, drag physics, particles (#38).
- Character-authoring preview tool.
- The virtual-time heap-growth test (kept as an acceptance criterion for the full grid work).

## Capabilities

### New Capabilities
- `pet-sprite-sheet`: the public sheet layout contract, header validation, decode rules, and the
  typed failure result.
- `pet-overlay-rendering`: IDLE frame drawing, the manual animation clock, and screen-off suspension.

### Modified Capabilities
- None.

## Approach

Split by Android dependency, not by feature. Grid arithmetic, the row table, divisibility rules,
frame-count clamping and the `SpriteSheet` result type are pure Kotlin and live in `:core:domain`,
so they are JVM-testable with no Robolectric. `BitmapFactory` decoding, pixel scanning for
transparent cells, the screen-state receiver and the renderer are Android-specific and live in
`:feature:overlay` — not `:core:data`, which owns persistence, and not the service, which holds no
state. The renderer consumes an `@Inject`ed state holder, never `hiltViewModel()`. When import
(#39) lands in slice 2 and gains a second consumer, the decoder relocates then, on evidence.

## Sequencing and PR boundaries

Issue #36 mandates two PRs because the decoder alone "produces a bitmap nobody draws". That reason
survives the IDLE-only cut: the decoder plus its fixture-based JVM tests is already substantial, and
the renderer's own risk (allocation churn, screen-off behaviour) is orthogonal review work.

| PR | Content | Est. changed lines |
|---|---|---|
| 0 | Screen-off clock spike — findings written to #36 and `design.md` | ~0 code |
| 1 | `:core:domain` geometry + result type; `:feature:overlay` decoder; JVM tests + fixtures | ~400–500 |
| 2 | Renderer, manual clock, screen signal, wiring, broken placeholder; instrumented tests | ~350–450 |

`delivery_strategy: ask-on-risk` against the 800-line budget: combined this is ~750–950 lines, at or
over budget, and the honest answer is that a single PR is not safe. **Chained PRs recommended: Yes**
under `chain_strategy: feature-branch-chain` — PR 1 targets the slice tracker branch, PR 2 targets
PR 1. Each PR stays under the budget on its own.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `core/domain/.../pet/sprite/` | New | Grid arithmetic, row table, `SpriteSheet` result type |
| `feature/overlay/.../sprite/` | New | `BitmapFactory` decoder, transparent-cell scan |
| `feature/overlay/.../ui/PetRenderer.kt` | New | IDLE drawing + manual clock |
| `feature/overlay/.../system/` | New | Screen-on `StateFlow` receiver |
| `feature/overlay/.../service/PetOverlayService.kt` | Modified | Magenta placeholder replaced by the pet |
| `feature/overlay/.../di/OverlayModule.kt` | Modified | Frame interval and decoder bindings |
| `app/src/main/res/` or assets | New | One built-in IDLE sheet + the broken placeholder asset |

## Recorded decisions

The proposal phase raised four product questions. All four are now answered, so nothing below is
an open assumption.

| Question | Decision | Reasoning |
|---|---|---|
| Which IDLE sheet ships? | **Programmer art**, generated as part of this change | It proves the pipeline end to end. The real drawing replaces the asset later without touching a line of code, which is the whole point of treating the sheet layout as a public contract. |
| What does a failed sheet render as? | A **drawn shape**, not an asset | An asset used to signal "decoding failed" can itself fail to decode. A shape drawn in code cannot. This is the only failure indicator that is guaranteed to work in the case it exists for. |
| How far does the failure path go now? | Visible broken state only; **no user-facing message** | The message belongs with import (#39, slice 2). Slice 1 has no surface to show it on, and inventing one would be scope this slice did not ask for. |
| If the screen-off clock spike goes badly? | Ship anyway, animation **suspended** at frame 0 while the screen is off | A pet that stops animating while nobody is looking at it is indistinguishable from one that keeps animating, except in battery cost. Suspension is the better failure. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Overlay frame clock behaves unexpectedly with the screen off | High | PR 0 spike before the clock is coded; suspend, never merely slow |
| Per-frame allocation churn invisible in short testing | Medium | Explicit zero-allocation acceptance criterion; no `BitmapPainter`/sub-bitmap per frame |
| Transparent-cell convention has no enforcement | Medium | Documented in code and authoring docs; preview tool deferred with the risk recorded |
| IDLE-only decoder shape hardens badly for the full grid | Medium | The domain type models all six rows now; only rendering is limited to row 0 |
| Emulator-only verification of overlay compositing | Medium | Accepted for slice 1; device pass belongs with slice 2 |

## Rollback Plan

Both PRs are additive. Reverting PR 2 restores the magenta placeholder composable with the overlay
service and `ComposeOverlayHost` intact; reverting PR 1 removes the new domain and decoder packages.
No persisted data, no schema, no migration.

## Dependencies

- #13 `PetOverlayService` and #14 `ComposeOverlayHost` — done and verified.
- Module-script rule from `slice-1-foundation`: `android {}` carries `namespace` and nothing else.
- One built-in IDLE sheet asset must exist before PR 2 can be verified visually.

## Success Criteria

- [ ] A pet renders and animates its IDLE row over another app on the emulator.
- [ ] Oversized and non-divisible sheets are rejected at header read, with no full decode.
- [ ] A failed sheet renders a visibly-broken placeholder — never blank, never a silent fallback.
- [ ] Exactly one `ARGB_8888` bitmap is resident; no hardware bitmaps.
- [ ] The frame loop performs zero bitmap allocations per frame and is suspended when the screen is off.
- [ ] The screen-off clock finding is recorded in #36 and in this change's design.
- [ ] Frame interval and all tunables are injected, never literals.
