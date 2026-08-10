# Proposal: Slice 2 — A pet you can move, and make yours

Build order slice 2. Issues #37, #15, #16, #39, #12 (#7 and #53 already shipped).

## Intent

The pet renders but cannot be moved, forgets nothing because it stores nothing, is always the
bundled drawing, and reaches the screen only via an `adb` grant. After this slice the user can drag
the pet and let it snap to an edge, it returns to that place after a restart, it can be the user's
own PNG, and there is an honest onboarding path to `SYSTEM_ALERT_WINDOW`.

## Scope

### In Scope
- Priority-ordered state resolution: `PetState`, `PetSnapshot`, `PetStateProvider`, `PetStateResolver`,
  fail-fast duplicate-priority check, central minimum dwell time, `Flow<PetState>` (#37).
- Drag with `View.OnTouchListener` + `getRawX/Y`, `scaledTouchSlop` tap/drag split, one
  `updateViewLayout` per frame via `Choreographer`, spring edge snap, `onTap` contract,
  in-memory `DRAGGING` `StateFlow` (#15).
- Fraction-based position persistence, write-once-at-rest, read-before-`addView` with timeout (#16).
- Character import: Photo Picker, copy to `filesDir/characters/<uuid>.png`, three-tier validation
  with rule-naming rejection messages, preview, library with hard cap, active-character switching
  observed live by the service (#39).
- Overlay permission onboarding screen, copy, passive re-entry affordance (#12).

### Out of Scope
- **TYPING, HAPPY, SLEEPING, HUNGRY providers and `CelebrationTracker`** — slices 3–5, with the
  inputs that feed them.
- Quick menu (consumes `onTap`), IME, cross-fade/squash animation, cutout avoidance, multi-display,
  Play UGC classification, permission mechanics (shipped in #11).

## Decisions

| # | Decision | Reasoning |
|---|---|---|
| 1 | Snap is **horizontal only**; `y` stays where released | Chat-head pattern: predictable, user owns the height |
| 2 | #37 registers **DRAGGING and IDLE providers only** | No provider may read a repository that does not exist; the issue's extension model (new file + DI binding) is exactly how the other four arrive |
| 3 | Replace the pixel keys with fraction keys **outright** | `OverlayPositionRepositoryImpl` persists `intPreferencesKey("overlay_position_x"/"_y")` — absolute pixels, contradicting #16 three times over. Nothing writes to it yet (no drag exists), so this completes an interface scaffolded ahead of schedule, not a shipped defect |
| 4 | Fraction type is **`Float`** | DataStore Preferences has no `Double` key; `Float` precision far exceeds a screen fraction and satisfies #16's `0.0..1.0` |
| 5 | `FLAG_LAYOUT_NO_LIMITS` **deferred to implementation** | Decide inside the #15 work unit against observed clipping during an edge drag; not guessable up front |
| 6 | Render cap becomes a **named `:core:domain` constant**; `OverlayWindowParams` derives its size from it | `PLACEHOLDER_SIZE_PX = 220` happens to satisfy #39's cap, but a guarantee held accidentally by a constant named "placeholder" is not a guarantee |

## Cross-cutting rules this slice can violate locally

- **Absence never renders as zero.** A missing persisted position emits `null` and falls back to a
  computed resting corner — never `(0f, 0f)`, never `(0, 0)`. `restingCorner()` already does this;
  the fraction migration must not introduce a `0f` default on the way through.
- **The foreground service holds no state.** `DRAGGING` and position are domain state the service
  observes; neither becomes a service field.
- **Permission grants are queried live.** The onboarding screen re-queries `OverlayPermissionChecker`
  on return from Settings (HyperOS reverts `adb`-granted `SYSTEM_ALERT_WINDOW`, per #9/#11).
- Frame interval, dwell time, cap and character count are injected config, not literals.

## Capabilities

### New Capabilities
- `pet-state-resolution`: snapshot shape, priority ordering, fail-fast collision, dwell time, IDLE fallback (#37).
- `overlay-drag`: touch ownership, tap/drag split, frame-throttled movement, horizontal edge snap, `onTap` (#15).
- `overlay-position-persistence`: fraction storage, write-at-rest, read-before-`addView`, named default (#16).
- `character-import`: pick, copy, validate, preview, library, cap, active switching (#39).
- `overlay-onboarding-ui`: copy requirements, anti-nagging, re-entry, accessibility (#12).

### Modified Capabilities
- `pet-overlay-rendering`: draws the resolved `PetState` rather than only IDLE, and sources sheets
  from the active character root rather than the hardcoded `pet/default/idle.png`.
- `pet-sprite-sheet`: adds the filesystem source and the fewer-animations-than-states fallback.

Note: slice 1 shipped position code with **no spec capability covering it**, so decision 3 is a
MODIFIED delta against merged *code* but an ADDED requirement at spec level. Stated here so the
next reader finds the contradiction and its resolution in one place.

## Approach

Pure logic (`nearestEdge`, fraction↔pixel, `isDrag`, grid arithmetic, resolver, cap constant) lands
in `:core:domain` and is JVM-tested without Android. Window IPC, touch, `BitmapFactory`, the Photo
Picker and Compose screens stay in `:feature:overlay`; DataStore keys stay in `:core:data`.
`PetOverlayStateHolder` stops decoding once at construction and becomes reactive to an
active-character `Flow` — required by #39 and the largest single rework in the slice.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `core/domain/.../pet/state/` | New | Resolver, snapshot, provider interface, two providers |
| `core/domain/.../overlay/OverlayPosition.kt` | Modified | Fraction type, named default, `restingCorner` semantics |
| `core/data/.../overlay/OverlayPositionRepositoryImpl.kt` | Modified | Pixel keys → `floatPreferencesKey` fractions |
| `feature/overlay/.../service/PetOverlayService.kt`, `OverlayWindowParams.kt` | Modified | Touch wiring, throttling, await-first-emission, cap constant |
| `feature/overlay/.../ComposeOverlayHost.kt` | Modified | Touch listener host, `onTap` |
| `feature/overlay/.../PetOverlay.kt`, `PetOverlayStateHolder.kt` | Modified | Consume `PetState`; reactive active character |
| `feature/overlay/.../character/` (import, library, switching) | New | Pipeline and UI |
| `feature/overlay/.../onboarding/` | New | Screen, copy, re-entry card |

## Delivery

`auto-chain`, 800-line review budget. The exploration's six units hold with two corrections:
unit 4 (`CelebrationTracker`) is removed by decision 2, and #39's three sub-PRs are real PR
boundaries, not one unit. Seven chained PRs:

1. #37 — state types, resolver, dwell time, DRAGGING + IDLE providers.
2. #15 — drag and horizontal snap; feeds `DRAGGING`.
3. #16 — fraction persistence and startup read ordering.
4. #39a — import and validation pipeline (carries most of the slice's risk).
5. #39b — character library and cap.
6. #39c — switching surface and the reactive state holder.
7. #12 — onboarding; independent, last, to spread review load.

PR #1 targets the tracker branch; each later PR targets its predecessor.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `PetOverlayStateHolder` rework — decode-once-at-construction cannot survive character switching | High | Largest risk in the slice. Land the reactive rework in #39c with the switching surface, behind a live active-character `Flow`, so the shape change has an immediate consumer and test |
| Fraction migration silently defaults to `0f` | Medium | Explicit `null`-on-missing test; named default constant in `:core:domain` |
| Service dies mid-drag holding a dead `View` | Medium | Explicit cancellation of frame callbacks and animation coroutines is a task, not an afterthought |
| Blocking window creation on the first DataStore read | Medium | Timeout fallback to the computed corner; measure startup latency |
| OEM touch delivery drops `ACTION_MOVE` | Medium | Real-hardware manual pass; unit tests cannot cover it |
| Overlay phishing via imported pixels | Permanent | Bounded, not closed: render cap, non-focusable window, identity affordance. No copy claims prevention |
| Play UGC classification for image import | Unknown | Flagged, not resolved here. Confirm in Play Console before submitting a build containing #39 |

## Rollback Plan

Each PR is independently revertable and the chain reverts newest-first. #16's fraction keys are new
preference keys, so a revert leaves orphaned entries and reads `null` — the pet falls back to the
computed resting corner, which is the correct first-launch behaviour. #39's imported files persist
under `filesDir/characters/`; a revert leaves them unread, and the active-character pointer falls
back to the built-in. Reverting #15 alone leaves #16 with no writer, so revert #16 first.

## Dependencies

- #11 permission mechanics (shipped) — the only dependency of #12.
- #14 `ComposeOverlayHost` (shipped) — the drag listener host.
- #36 sprite format (shipped, post-#68 one-sheet-per-animation) — the import contract.

## Success Criteria

- [ ] Drag the pet, release, and it springs to the nearest horizontal edge keeping its height.
- [ ] A sub-slop touch invokes `onTap` and never moves the pet.
- [ ] `updateViewLayout` is called at most once per frame during drag, proven by count or trace.
- [ ] Kill and restart the service; the pet returns to its last resting position with no jump.
- [ ] With no stored value, the pet appears at the computed resting corner — never at `(0,0)`.
- [ ] Rotate the device; the pet stays on screen at the equivalent relative position.
- [ ] Import a PNG, see the preview, confirm, and the running service re-renders without relaunch.
- [ ] Every rejection message names the specific rule broken; no generic "invalid image" exists.
- [ ] The resolver fails at construction on a duplicate priority, with a test asserting distinctness.
- [ ] Onboarding states all four copy claims, does not auto-reappear after one refusal, and passes a
      manual TalkBack pass.
