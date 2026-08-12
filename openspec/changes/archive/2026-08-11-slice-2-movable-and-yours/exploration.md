# Exploration — slice-2-movable-and-yours (#37, #15, #16, #39, #12)

Scope: slice 2 of `docs/build-order.md`, "A pet you can move, and make yours". Issues #7 and
#53 are listed in that slice but already shipped and closed, so they are excluded.

Investigation only. No proposal, no implementation.

## Current state

Slice 1 shipped more slice-2-adjacent infrastructure than the build order credits it for. All of
the following was verified in code, not assumed:

- **Service** (`feature/overlay/.../service/PetOverlayService.kt`): stateless by design,
  `TYPE_APPLICATION_OVERLAY` window via `OverlayWindowParams.create()` —
  `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`, `PixelFormat.TRANSLUCENT`, no
  `FLAG_LAYOUT_NO_LIMITS`. It already collects `OverlayPositionRepository.position` and calls
  `windowManager.updateViewLayout()` on emission, or `addOverlayWindow()` on first attach.
  `restingCorner()` is computed against live `usableBoundsPx()` (system bars and cutout insets
  subtracted) whenever `position` emits `null`.
- **Compose host** (`ComposeOverlayHost.kt`): `AbstractComposeView` with a self-owned
  `LifecycleRegistry`/`SavedStateRegistry`, no `ViewModelStoreOwner`. It is an ordinary `View`, so
  `setOnTouchListener` is available directly; nothing attaches one today.
- **Position persistence** (`core/domain/.../overlay/OverlayPosition.kt`,
  `OverlayPositionRepository.kt`, `core/data/.../OverlayPositionRepositoryImpl.kt`): already
  implemented, storing absolute pixel `x`/`y` as two `intPreferencesKey`s in the shared
  `DataStore<Preferences>`, emitting `null` — never a stand-in coordinate — when either key is
  missing. `OverlayPosition.restingCorner(screenWidthPx, screenHeightPx, overlaySizePx)` computes a
  corner from live bounds; there is deliberately no fixed `DEFAULT` constant.
- **Overlay permission mechanics** (#11, shipped, not part of this slice):
  `OverlayPermissionChecker`/`OverlayPermissionCheckerImpl` (live `Settings.canDrawOverlays`, never
  cached, re-queried on every `onStartCommand`, plus an `AppOpsManager.OnOpChangedListener`
  revocation watcher), and `OverlayOnboardingHistory`/`OverlayOnboardingPolicy`/
  `OverlayOnboardingRepository`/`OverlayOnboardingRepositoryImpl` (interaction history only:
  `hasSeenOnboarding`, `refusalCount`, `lastRefusalAtEpochMillis`, all DataStore-backed).
  `OverlayOnboardingPolicy.shouldAutoShowOnboarding` already encodes "do not auto-show after one
  refusal". This is exactly the mechanics dependency #12 declares, and it is complete.
- **Sprite sheet format** (post-#68 refactor): one image is one animation is one row of square
  frames; `columns = width / height`; folder per character with fixed filenames
  (`pet/<character>/idle.png`). `idle.png` is required, every other animation is optional and
  absent-is-valid. `SpriteGrid.of()`, `SpriteLayout` and `SpriteSheetFailure` (`Oversized`,
  `NotDivisible`, `Undecodable`, `EmptySheet`) live in `:core:domain`; `SpriteSheetDecoder`,
  `TransparentCellScanner` and `BitmapDecoding` live in `:feature:overlay`. Today
  `PetOverlayStateHolder` loads exactly one hardcoded asset path (`"pet/default/idle.png"`) from
  `context.assets`. There is no notion of multiple characters, multiple animations, or a
  filesystem source.
- **DataStore**: one shared `DataStore<Preferences>` (`core/data/di/DataModule.kt`, key
  `"petmephone_prefs"`) already backs both position and onboarding history. This is the persistence
  infrastructure #16 extends. No Room schema exists for pet position anywhere.
- **Renderer scope** (`pet-overlay-rendering` spec, `PetOverlay.kt`): draws only `idle.png`. No
  state resolution exists — `PetState`, `PetSnapshot` and any resolver have zero occurrences in the
  codebase. #37 is fully greenfield.

## Affected areas

| Area | Issues |
|---|---|
| `core/domain/.../pet/state/` (new package: `PetState`, `PetSnapshot`, `PetStateProvider`, `PetStateResolver`) | #37 |
| `core/domain/.../overlay/OverlayPosition.kt`, `OverlayPositionRepository.kt` | #16 — persisted shape changes; `restingCorner` semantics need revisiting |
| `core/data/.../overlay/OverlayPositionRepositoryImpl.kt` | #16 — storage keys |
| `PetOverlayService.kt`, `OverlayWindowParams.kt` | #15 (touch listener, `updateViewLayout` throttling), #16 (write-at-rest wiring, read-before-`addView` timing) |
| `ComposeOverlayHost.kt` | #15 — natural home for the touch listener and `onTap` callback |
| `PetOverlay.kt`, `PetOverlayStateHolder.kt` | #37 (consume resolved state), #39 (per-character asset root) |
| New import pipeline + character storage | #39 |
| New onboarding screen composable | #12 |
| `openspec/specs/pet-sprite-sheet`, `pet-overlay-rendering` | all five — MODIFIED deltas against the slice-1 contract |

## #37 — Priority-ordered state resolution: which inputs actually exist

| Snapshot field | Real source in slice 2? | Consequence |
|---|---|---|
| `isDragging` | Yes — #15 lands in this slice and feeds it | — |
| `isTyping` | No — #18 (IME on overlay) is slice 3 | Field exists in the type, no real provider; defaults to `false` |
| `isCelebrating` | No — `TaskCompletedEvent` is slice 4 | `CelebrationTracker` is buildable and testable, but has no emitter until slice 4 |
| `energyPercent` | No — Energy ledger is slice 5 | The issue pre-filters `Unavailable` one layer below the snapshot; in slice 2 there is no metrics repository at all |
| `tasksCreatedToday`, `dailyTaskGoal` | No — task schema is slice 3 | Same gap |

This is the sharpest tension in the slice. #37 is ordered first because DRAGGING makes a second
state exist, but four of its six providers have no data source until slices 3–5.

The rule that **absence never renders as zero** is preserved structurally: providers return
`PetState?` from `evaluate()`, so a provider with no data falls through to lower priority rather
than fabricating a condition. The danger is not the resolver's shape, it is adding a HUNGRY or
SLEEPING provider that reads a repository which does not exist and quietly resolves to a value.

**Recommendation for the proposal:** slice 2 ships the resolver, the priority machinery, the
dwell-time `Flow<PetState>`, and only the providers with real inputs — DRAGGING and IDLE. The
remaining providers arrive with the slices that produce their inputs, which is exactly the
extension model the issue designs for ("a new file plus a DI binding, no edit to existing code").

## #15 / #16 — Drag, snap, and what a position is

- **Touch ownership.** `ComposeOverlayHost` is a plain `View`, so `setOnTouchListener` attaches
  cleanly. The window is already `FLAG_NOT_FOCUSABLE`/`FLAG_NOT_TOUCH_MODAL`, matching the issue's
  assumption. No flag change is needed for drag itself.
- **`DRAGGING` storage.** The issue mandates an in-memory `@Singleton StateFlow`, not persisted and
  not a service field. That mirrors `OverlayPositionRepositoryImpl`'s existing shape minus
  DataStore, so the pattern is already established.
- **Position representation — a real contradiction.** #16 states three times that fractions of
  screen size are persisted and that no absolute pixel coordinate is stored anywhere. The
  already-merged `OverlayPositionRepositoryImpl` persists `intPreferencesKey("overlay_position_x")`
  and `_y` — raw absolute pixels — and `OverlayPosition` is `data class OverlayPosition(val x: Int,
  val y: Int)` with no fraction concept. Recorded here rather than reconciled, per the build-order
  checklist rule and the #9/#17 precedent. Two options for the proposal to decide:
  1. Keep `OverlayPosition` in pixels for runtime (window params need pixels) and add a separate
     fraction type that the repository stores and emits, converting at the service boundary. Matches
     #16's letter but changes an interface merged in slice 1.
  2. Treat the shipped pixel repository as the placeholder it structurally is — nothing writes to it
     yet, because no drag exists — and replace its keys with fraction-backed ones outright.
  Option 2 is the lower-cost path and completes an interface that was scaffolded ahead of schedule
  rather than fixing a shipped defect. It is a proposal decision, not an assumption to carry.
- **Write timing.** #16 demands write-once-at-rest after the snap animation completes, cancelling an
  in-flight write when a new drag starts. New logic; no hook exists. The read-before-`addView`
  ordering is already satisfied structurally by `onStartCommand`.

## #39 — User character import

- **The folder layout fits.** `pet/<character>/idle.png` is exactly what #39 assumes. #39 is
  additive on a contract slice 1 already established, not a redesign.
- **Where imported assets live.** The only asset source today is `context.assets` (bundled,
  read-only). #39 requires `filesDir/characters/<uuid>.png` — app-private filesystem. The decoder
  needs a second read path, and `PetOverlayStateHolder`'s decode-once-at-construction shape cannot
  survive character switching without becoming reactive to an active-character `Flow`. This is a
  real architectural change to that class, not a small addition.
- **Non-focusable assumption.** #39 asks for the overlay's actual flags to be verified before its
  criteria are written into code. Verified: `FLAG_NOT_FOCUSABLE` is set. The assumption holds.
- **Render size cap.** `SpriteGrid.of(widthPx, heightPx, maxDimensionPx)` already enforces a 2048
  cap on the *source* image. The *rendered* size derives from the window's
  `OverlayWindowParams.PLACEHOLDER_SIZE_PX = 220`. Whether that fixed window size already satisfies
  #39's "maximum render size enforced in code, independent of what the sheet claims" needs
  confirming, or the cap becomes an explicitly named shared constant.
- **Missing-animation fallback.** #39's "missing states fall back to IDLE" reads, under the
  one-file-per-animation model, as a missing animation *file* — which `pet-sprite-sheet` already
  declares an ordinary valid state rather than an error. Consistent, not contradictory. The fallback
  itself is shared work: #37 decides IDLE, #39 tolerates the missing file, and they meet at the
  renderer.

## #12 — Onboarding

Every dependency is already shipped by #11, and #12's own "out of scope" list (permission detection,
DataStore fields) matches that boundary exactly. This is a Compose-UI-and-copy change over a
complete domain layer — the lowest-risk item in the slice, and coupled to nothing else in it.

The HyperOS finding recorded in #9 and #11 applies: an `adb`-granted `SYSTEM_ALERT_WINDOW` reverts to
`ignore` within seconds on that device, and only a grant made through the system settings UI
persists. The mechanics layer already satisfies the constraint by querying live and never caching.
The screen must re-query `OverlayPermissionChecker` on return from Settings rather than trust a
locally held boolean — which the interface's plain synchronous shape already supports.

## Ordering and coupling

- #15 depends on the Compose host (done) and ships the `onTap` contract that slice 3's quick menu
  consumes.
- #16 depends on #15 and is deliberately a separate PR — unrelated failure modes.
- #37 depends on #15 for a real second state. Its honest slice-2 deliverable is narrower than its
  full acceptance criteria.
- #39 depends only on the renderer contract, already merged. Product-wise it reads better after
  drag and persistence, so an imported character is immediately draggable.
- #12 depends only on #11. Fully independent; can land first or last with no rework.

Proposed boundaries under an 800-line budget with `auto-chain`, respecting the sub-PR counts the
issues themselves suggest (#37 proposes two, #39 proposes three):

1. **#37a** — state types, resolver, priority machinery, dwell time, DRAGGING and IDLE providers.
2. **#15** — drag and edge snap, feeding `DRAGGING` into the resolver.
3. **#16** — position persistence; where the pixel-versus-fraction decision gets executed.
4. **#37b** — `CelebrationTracker`, only if a stub emitter is acceptable this slice; otherwise it
   legitimately slips to slice 4 with the checklist.
5. **#39** — three sub-PRs per the issue's own breakdown: import and validation pipeline, library,
   switching surface.
6. **#12** — smallest and independent; last, to spread review load.

## Open questions for `sdd-propose`

1. **Pixel versus fraction position storage** — which of the two options above, and whether it needs
   a MODIFIED delta against slice 1's merged position work.
2. **Snap axis** — horizontal only or two-axis. #15 defers this to implementation time; it changes
   both the animation shape and the acceptance criteria.
3. **`FLAG_LAYOUT_NO_LIMITS`** — not set today; may be needed during an active drag near screen
   edges. #15's coordinate table does not address it.
4. **#37's real slice-2 scope** — which providers ship now. Must be stated in the proposal rather
   than discovered during apply.
5. **`Double` versus `Float` for fractions** — DataStore Preferences has no `Double` key. #16's
   `0.0..1.0` reads as `Double`; `Float` precision is more than sufficient for a screen fraction.
6. **Render-size cap semantics for #39** — is the fixed window size sufficient, or does the cap need
   to be an explicit named constant.

## Resolved during exploration

- The five slice-2 issues have **no comments** on GitHub (verified: 0 for each). The HyperOS
  permission-persistence finding lives in #9 and #11, both of which were read directly. It is a
  recorded fact, not an unverified claim, and #12 may cite it.
