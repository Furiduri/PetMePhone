# Design: Slice 3 part B — A pet you can talk to, the overlay half (#17, #18 spike)

## Technical approach

The same split part A used, applied to a window instead of a database. Everything decidable —
where the card goes, what a metric reading *is*, and what a dismissal event does to the card's
state — is a pure function or a pure type in `:core:domain`, unit-tested with no device. The
Android half is one new class, `QuickMenuWindowController`, which owns the second window's whole
lifecycle and nothing else. `PetOverlayService` gains one nullable field and four delegating
lines; `PetTouchController`, `OverlayWindowParams` and the pet window are untouched.

`minSdk` rises 26 → 30, so the `WindowMetrics` inset path becomes the only path and every compat
branch in the service is deleted rather than left unreachable.

Satisfies `overlay-quick-menu`, `quick-menu-positioning`, `overlay-metric-display`,
`ime-viability-spike`, and the `hunger-metric` / `build-foundation` deltas.

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | `minSdk` | `ProjectConfig.minSdk = 26 → 30`, sole owner unchanged, no build literal added anywhere | add `androidx.window` for a 26–29 inset path | The compat path could never be exercised: neither the emulator (34) nor the maintainer's device is in 26–29, so it would ship as untested code guarded by a version check no test can enter. A dependency whose only justification is correctness on hardware nobody can run is a liability, not coverage. The bump also removes four existing unreachable branches (below) |
| 2 | Metric display type | `MetricReading` sealed interface in `:core:domain/metric/`: `Loading`, `Available(percent: Int)`, `Unavailable` | put it in `feature/overlay/ui/`; reuse `CharacterSheets`' shape verbatim | The full-screen app (#28, slice 7) renders the same three metrics; in the feature module that becomes a feature→feature dependency or a copy. It is pure Kotlin with no Android type, so `:core:domain` costs nothing. `percent` exists **only** on `Available` — a zero is unconstructible without someone writing `Available(0)`, which is a real reading |
| 3 | Happiness and Energy are not flows | `PetOverlayStateHolder` exposes `val happiness: MetricReading = MetricReading.Unavailable` and the same for `energy` — plain vals, not `StateFlow` | expose all three as `StateFlow<MetricReading>` seeded `Unavailable` | "Happiness never enters `Loading`" becomes a compile-time fact instead of a test asserting a runtime sequence. When slice 4 gives Happiness a producer, the val becomes a `StateFlow` and every call site that assumed otherwise fails to compile — which is the correct blast radius |
| 4 | Hunger is reactive off Room, keyed on a day flow | `ObserveHunger(clock, tasks, config): Flow<Int>` in `:core:domain/balance/`. A `todayFlow` emits `clock.today()`, then suspends until the next local midnight and re-emits; `flatMapLatest` into two **new** `TaskRepository` Flow counts, `combine`d through the existing `calculateHunger` | poll on a fixed interval; recompute once when the card opens | Poll: staleness bounded only by the interval, and a timer running while nothing is on screen. Card-open-only: correct today, but #27's card stays open across several submits and would show a frozen number, and the fix would then have to be retrofitted under the pressure of a working keyboard. Room's invalidation already emits on write, so reactive costs one method pair. **Failure mode named**: if the process is dozing, the midnight re-emission fires late — bounded because the flow is only collected while the card window is attached, and the card is opened by a live tap, which re-reads `clock.today()` on subscription |
| 5 | No count is cached in the overlay | `hunger: StateFlow<MetricReading>` via `stateIn(scope, WhileSubscribed(0), initialValue = Loading)` | `SharingStarted.Eagerly`, or a `var lastKnown` | `WhileSubscribed(0)` drops the collection the moment the card window is removed, so nothing survives a dismiss to be re-shown stale. `Loading` as the seed makes the transient loading state structural rather than hand-managed, and it never reverts because `stateIn` only moves forward |
| 6 | Card window focus | **The card window is non-focusable.** `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH`, exactly as the pet window is focusable-free, and **no** `FLAG_ALT_FOCUSABLE_IM` | a focusable card; focusable-for-keys via `FLAG_ALT_FOCUSABLE_IM` without `FLAG_NOT_FOCUSABLE` | Maintainer's ruling. A focusable card takes window focus, and the app underneath receives `onWindowFocusChanged(false)` — videos pause, cursors drop, games pause. That is **precisely the cost issue #18 mandates a spike to measure**. Paying it in the shell, before the spike has reported, inverts the gate the build order put there on purpose: the shell would have already committed to the outcome the spike exists to decide. The card renders no text field, so it needs no focus for its own function; focus would be taken *solely* to enable back, buying one gesture at the price of pre-empting the decision. Non-focusable also means the card can never be the window that "remains focusable after close" — decision 8's hazard stops being reachable at all |
| 7 | Back gesture | **Deliberately not implemented in this change.** No `OnBackPressedDispatcher` is created, no `setViewTreeOnBackPressedDispatcherOwner` call, no `KEYCODE_BACK` interception, no `BackHandler` | wire a dispatcher now so the mechanism is "ready" | Key events are delivered only to a focusable window. Under decision 6 there is no focusable window, so **both** candidate mechanisms have nothing to receive: a wired dispatcher would be dead code dressed as a feature, passing a unit test that calls `onBackPressed()` directly while never once firing in the user's hand. That is worse than an honest gap, because it reads as satisfied. **Tracked deviation: #17's back-gesture acceptance criterion is not met by this change**, by decision, with the reason recorded here and in `overlay-quick-menu`. It becomes deliverable only if the spike (decision 14a) shows taking window focus is acceptable |
| 8 | Dismissal removes the window | Every dismissal path calls `windowManager.removeView` and nulls the controller's view field; the card is never merely hidden | keep the view attached and toggle visibility | A hidden-but-attached window is a live `View` nobody is looking at, and under a later focusable card it would be the window that "remains focusable after close", silently stealing input from every app afterwards — #18's own worst case. Removal retires that whole class of bug now, before the card ever becomes focusable, and leaves no `View` to leak |
| 9 | Dismissal state machine | Pure `QuickMenuState` (`Closed` \| `Open(anchor)`) plus a total `reduce(state, event)` over `PetTapped`, `PetDragged`, `OutsideTouch`, `AppLaunched`, `ScreenOff`, in `:core:domain/overlay/`. No `BackPressed` event exists, per decision 7 | model #18's full opened/focused/submitted/dismissed set now | The shell's honest subset is opened/dismissed. `PetTapped` while `Open` returns `Closed` — the toggle *is* the required `ACTION_OUTSIDE` fallback, not a separate mechanism, and under decision 7 it carries materially more weight than it did when back was still on the table (see the reachability argument below). "Focused" is a property of `Open`, not a sibling state, and `Submitted` is an additive case on a total reducer, so the spike's outcome **extends** this rather than replacing it |
| 10 | Positioning signature | `QuickMenuPlacement.place(anchor, screenWidthPx, screenHeightPx, cardWidthPx, cardHeightPx, insets: ScreenInsets, gapPx): OverlayPosition` in `:core:domain/overlay/` | take `OverlayAnchor` directly; return a new offset type | `OverlayAnchor` lives in `:feature:overlay` and cannot cross into domain, so the controller maps it into the domain-side `QuickMenuAnchor`. Returning the existing `OverlayPosition` reuses the pixel type the window layer already speaks, matching `OverlayPositionFraction.toPixels` |
| 11 | Card size is configured, not measured | Fixed width/height in an injected `QuickMenuConfig` (dp), converted to px by the controller before calling `place` | measure the composition, then position | Measuring requires the view to be attached, so the card would be added at a wrong position and moved — a visible jump, and the same bug `[POS-5]` already forbids for the pet. A configured size keeps `place` pure and keeps the number out of a literal, per the standing injected-config rule |
| 12 | Controller construction | `QuickMenuWindowController` is constructed by `PetOverlayService`, like `PetTouchController`; its collaborators (`WindowManager`, state holder, config, placement inputs) are injected into the service | `@Inject` the controller | It needs the live window and a per-open anchor, and its lifetime is the service's window, not the process. Following the existing extraction precedent keeps one construction idiom in the service |
| 13 | Spike delivery | A new `:spike:ime-viability` `com.android.application` module with its own `applicationId`, not in `:app`'s dependency graph and not in any release variant | a `debug` variant of `:app`; an `androidTest` artifact | A debug variant puts IME experiments into the production module's manifest and source sets, and deleting it later edits `:app`. An `androidTest` artifact cannot be driven interactively over a playing video — instrumentation runs under the test runner, not as an app the maintainer opens. A separate module is installable, deletable in one line of `settings.gradle.kts`, and structurally incapable of reaching the release APK |
| 14 | How findings leave the device | The spike writes a Markdown run record to its app-specific external files dir and offers a share/copy action; the maintainer `adb pull`s or shares it and commits it verbatim under `spike-findings/` | a template the maintainer fills in by hand | Transcription is where measurements become recollections. Device identity (`Build.MANUFACTURER`/`MODEL`/`SDK_INT`) and most questions are captured programmatically (below); only the cross-app ones need a human, and the app asks for them as explicit answers rather than assuming one |
| 14a | The spike measures **focus cost separately from IME cost** | The spike runs in two modes against the same window: **focus-only** (focusable, no text field, no keyboard raised) and **full IME** (focusable with a text field). Each mode records whether a playing video pauses, and whether focus returns correctly to the app underneath after dismissal | measure only the IME case, as #18 originally framed it | Under decisions 6 and 7 the back gesture is now blocked on *window focus alone*, not on the keyboard. If the spike only ever measured a focused window **with** a text field, a "video paused" result could not distinguish the keyboard's cost from focus's cost — and back would stay blocked on evidence that never actually tested it. Splitting the modes is what makes the spike able to return back to the table: focus-only clean means back becomes deliverable even if full IME is unviable. Focus return after dismissal is measured because a card that takes focus and does not give it back is worse than one that never took it |
| 15 | The #70 resolver widening stays out | Metrics flow from `PetOverlayStateHolder` straight to the card; `PetStateResolver` is not touched | absorb part A's recorded tier-2 constraint here | The card reads numbers, the resolver picks an animation — already separate paths. Widening the resolver here would be done again by #38/#70 for tap-to-browse, exactly the duplication part A's design warned about |

### Dismissability is total (decision 9)

With back gone, dismissal rests entirely on `ACTION_OUTSIDE` plus the pet-tap/drag fallback, so
"can the user always close this card?" stops being a nice property and becomes the load-bearing
one. The reducer answers it structurally: `QuickMenuState` has exactly two cases, and **four of the
five events map `Open` to `Closed`** — `PetTapped`, `PetDragged`, `OutsideTouch` and `ScreenOff`
(`AppLaunched` also closes, but the user reaches it only through the card itself). No event maps
`Open` to `Open`, and no event carries a guard or a condition. There is therefore **no reachable
state in which the card cannot be dismissed**, and no input path that leads to one.

That is asserted, not assumed: the reducer's exhaustive state × event test includes a named case
proving every event from `Open` yields `Closed`, and the pet-tap and pet-drag paths are the ones
that survive even when `ACTION_OUTSIDE` never fires — which is the failure mode the fallback exists
for, and the only remaining line of defence now that back is gone.

### What the spike measures, and who answers (decisions 14, 14a)

Run in both modes — **focus-only** (no text field) and **full IME** — so the cost of focus is
separable from the cost of the keyboard.

| Question | Mode | How it is answered |
|---|---|---|
| Does the keyboard appear on tap? | full IME | `ViewCompat.setOnApplyWindowInsetsListener` on the spike window; `ime()` visibility |
| Does it cover the field? | full IME | `ime()` inset top vs the field's bottom in screen coordinates |
| Do IME insets arrive without `imePadding()`? | full IME | Whether any `ime()` callback fired at all, recorded with its values |
| Is the window still focusable after dismiss? | both | The spike re-reads its own `LayoutParams.flags` and `onWindowFocusChanged` after removal |
| **Does taking focus pause a playing video?** | **both, recorded separately** | **Human answer.** Cross-app state the spike cannot observe; prompted per mode as Yes/No/Not-tested. The focus-only result is what decides whether the back gesture can ever be delivered |
| **Does focus return to the app underneath after dismissal?** | both | Partly automatic (`onWindowFocusChanged` on the spike's own window), plus a human confirmation that the app below regained its cursor/playback |

## Data flow

```
[open]   PetTouchController ──onTap(OverlayAnchor)──► PetOverlayService.onPetTapped
             └─► QuickMenuWindowController.onEvent(PetTapped(anchor))
                     reduce(Closed, PetTapped) = Open(anchor)          [:core:domain, pure]
                     QuickMenuPlacement.place(anchor, bounds, cardSize, insets, gap)
                             insets ← WindowMetrics.getInsetsIgnoringVisibility(
                                          systemBars() or displayCutout())     API 30+, only path
                     ──► OverlayPosition ──► QuickMenuWindowParams.create(position)
                     ──► windowManager.addView(ComposeOverlayHost{ QuickMenuCard(...) })
                             + host.setOnTouchListener { ACTION_OUTSIDE → onEvent(OutsideTouch) }
                             (no back dispatcher: the window is non-focusable — decisions 6, 7)

[metrics] TaskRepository.observeManuallyCreatedOn(date): Flow<Int>     Room invalidation
          TaskRepository.observeRecurringScheduledOn(date): Flow<Int>  (0 until slice 4)
              todayFlow(clock) ──flatMapLatest──► combine ──► calculateHunger(...)  [pure]
              ──► map(MetricReading::Available)
              ──► stateIn(scope, WhileSubscribed(0), Loading)   PetOverlayStateHolder.hunger
          happiness / energy : MetricReading = Unavailable       plain vals, decision 3

[close]  PetTapped | PetDragged | OutsideTouch | AppLaunched | ScreenOff      (no BackPressed)
              reduce(Open, e) = Closed  ──► host.destroy(); windowManager.removeView(host)
```

## Interfaces

```kotlin
// :core:domain/metric/MetricReading.kt
sealed interface MetricReading {
    data object Loading : MetricReading                      // transient, pre-first-emission only
    data class Available(val percent: Int) : MetricReading    // the only case carrying a number
    data object Unavailable : MetricReading                   // no producer exists yet
}

// :core:domain/overlay/QuickMenuPlacement.kt      pure; no android.* / androidx.* import
data class QuickMenuAnchor(val xPx: Int, val yPx: Int, val sizePx: Int)
data class ScreenInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)
object QuickMenuPlacement {
    fun place(
        anchor: QuickMenuAnchor,
        screenWidthPx: Int, screenHeightPx: Int,
        cardWidthPx: Int, cardHeightPx: Int,
        insets: ScreenInsets,
        gapPx: Int,
    ): OverlayPosition        // side with the most space, insets subtracted, then clamped
}

// :core:domain/overlay/QuickMenuState.kt          pure state machine, decision 9
sealed interface QuickMenuState {
    data object Closed : QuickMenuState
    data class Open(val anchor: QuickMenuAnchor) : QuickMenuState
}
sealed interface QuickMenuEvent {              // no BackPressed — decision 7
    data class PetTapped(val anchor: QuickMenuAnchor) : QuickMenuEvent
    data object PetDragged : QuickMenuEvent
    data object OutsideTouch : QuickMenuEvent
    data object AppLaunched : QuickMenuEvent
    data object ScreenOff : QuickMenuEvent
}
/** Total. Every event applied to [QuickMenuState.Open] yields [QuickMenuState.Closed]; there is no
 *  reachable state in which the card cannot be dismissed. */
fun reduce(state: QuickMenuState, event: QuickMenuEvent): QuickMenuState

// :core:domain/balance/ObserveHunger.kt   @Provides in DataModule, as CreateOneOffTask is
class ObserveHunger(private val clock: AppClock, private val tasks: TaskRepository,
                    private val config: BalanceConfig) {
    operator fun invoke(): Flow<Int>      // percentage; day-boundary aware, decision 4
}

// :core:domain/task/TaskRepository.kt — two additions, Flow for reads (existing convention)
fun observeManuallyCreatedOn(date: LocalDate): Flow<Int>
fun observeRecurringScheduledOn(date: LocalDate): Flow<Int>

// :feature:overlay/quickmenu/QuickMenuWindowController.kt
internal class QuickMenuWindowController(/* context, windowManager, stateHolder, config,
    bounds/inset suppliers, appLauncher */) {
    fun onEvent(event: QuickMenuEvent)    // the only entry point; owns add/remove
    fun destroy()                         // service teardown
}
```

`QuickMenuWindowParams` sits beside `OverlayWindowParams` in `service/`, mirroring its shape:
`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or
FLAG_WATCH_OUTSIDE_TOUCH`, `PixelFormat.TRANSLUCENT`, `Gravity.TOP or START`. The two params
objects differ in exactly one flag — `FLAG_WATCH_OUTSIDE_TOUCH`, which belongs to the card, as
`OverlayWindowParams`' own kdoc already says. Its kdoc records why they are still separate objects
(independent lifecycles, and the card's flags are expected to change if the spike reopens focus)
and why `FLAG_ALT_FOCUSABLE_IM` is deliberately absent: that flag is only meaningful for a window
that is not `FLAG_NOT_FOCUSABLE`, so pairing the two would be noise suggesting a focus story that
does not exist here.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `build-logic/.../ProjectConfig.kt` | Modify | `minSdk` 26 → 30 (decision 1) |
| `openspec/config.yaml`, `openspec/specs/build-foundation/spec.md` | Modify | The stated `minSdk` follows the owner |
| `core/domain/.../metric/MetricReading.kt` | Create | Decision 2 |
| `core/domain/.../overlay/{QuickMenuPlacement,QuickMenuState}.kt` | Create | Decisions 9, 10 |
| `core/domain/.../balance/ObserveHunger.kt` | Create | Decision 4 |
| `core/domain/.../task/TaskRepository.kt` | Modify | Two `Flow<Int>` count reads |
| `core/data/.../local/task/TaskDao.kt` | Modify | Two `Flow<Int>` `@Query` counts, no balance literal |
| `core/data/.../repository/TaskRepositoryImpl.kt` | Modify | Implements both |
| `core/data/.../di/DataModule.kt` | Modify | `@Provides ObserveHunger` |
| `feature/overlay/.../quickmenu/QuickMenuWindowController.kt` | Create | Decisions 6, 8, 12 |
| `feature/overlay/.../service/QuickMenuWindowParams.kt` | Create | Card-window flags, non-focusable |
| `feature/overlay/.../quickmenu/ui/{QuickMenuCard,MetricRow}.kt` | Create | Semantics, 48dp targets, `@Preview` data only |
| `feature/overlay/.../quickmenu/QuickMenuConfig.kt` | Create | Card size, gap; `@Provides` in `OverlayModule` |
| `feature/overlay/.../ui/PetOverlayStateHolder.kt` | Modify | `hunger` StateFlow; `happiness`/`energy` vals |
| `feature/overlay/.../service/PetOverlayService.kt` | Modify | `onPetTapped` delegates; drag → `PetDragged`; screen-off → `ScreenOff`; compat branches deleted |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | `QuickMenuConfig` |
| `spike/ime-viability/**` | Create | Decision 13; `settings.gradle.kts` include |
| `openspec/changes/.../spike-findings/README.md` | Create | Findings record, one file per device, focus-only and full-IME results kept apart |
| `openspec/changes/.../specs/overlay-quick-menu/spec.md` | Modify | Record the back-gesture deviation (below) |

### What the `minSdk` bump makes dead (decision 1)

Removed, not left behind: `usableBoundsPx()`'s `SDK_INT < R` early return; `navigationBarInsetBottomPx()`'s
`SDK_INT < R` early return; `screenBoundsPx()`'s `defaultDisplay.getRealMetrics` branch together
with its `@Suppress("DEPRECATION")`; and `onCreate`'s `SDK_INT >= Q` `startForeground` fork, since
Q is 29. An unreachable branch that no test can enter is not a safety net — it is code a reader
must still reason about.

### Tracked deviation: #17's back-gesture criterion is not met (decision 7)

#17 lists "the back gesture dismisses the card, via explicit dispatcher wiring" as an acceptance
criterion, and `overlay-quick-menu` carries it as a requirement. **This change does not satisfy
it, by decision.**

The reason is a dependency the issue did not have when it was written: back requires key events,
key events require a focusable window, and a focusable window costs the app underneath its focus —
which is the exact measurement #18's spike was created to gate. Satisfying #17's back criterion
here would mean pre-empting #18's spike with the shell, deciding by default the question the build
order deliberately deferred.

The honest options were (a) ship focus and pre-empt the spike, (b) ship a dispatcher that nothing
can deliver to, or (c) ship no back and record why. (b) is the worst of the three: it turns a
missing feature into a passing test. This design takes (c).

What replaces it: dismissal is total without back (see decision 9's reachability argument), so the
gap costs the user a gesture, not a way out of the card. What unblocks it: the spike's focus-only
result (decision 14a). `overlay-quick-menu`'s back requirement is rewritten in this change to state
the deviation and its condition, rather than left standing and quietly unmet.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit `:core:domain` | `place` at all four corner anchors and both mid-edges; clamping when the card exceeds available space on each axis; top-inset and cutout subtraction; determinism on repeated calls; `reduce` total over every state × event pair, with a **named test asserting every event from `Open` yields `Closed`** — no undismissable state exists — and `PetTapped` while `Open` closing (the fallback) | JUnit4, pure Kotlin, parameterised — no device |
| Unit `:core:domain` | `ObserveHunger` emits on count change and re-emits across a day boundary; `MetricReading` has no numeric default outside `Available` | `runTest` + `Turbine`-style collection, fake `AppClock`/`TaskRepository`, virtual time |
| Unit `:core:data` (Robolectric, in-memory Room) | Both `Flow<Int>` counts emit on insert and ignore generated occurrences | `runTest` + `Room.inMemoryDatabaseBuilder` |
| Unit `:feature:overlay` (Robolectric) | `QuickMenuWindowParams` sets `FLAG_WATCH_OUTSIDE_TOUCH` **and** `FLAG_NOT_FOCUSABLE`, and does **not** set `FLAG_ALT_FOCUSABLE_IM`; `OverlayWindowParams` still omits `FLAG_WATCH_OUTSIDE_TOUCH`; every dismissal path calls `removeView` and leaves no view field set | Robolectric + a shadow `WindowManager` |
| Unit `:feature:overlay` (source scan) | No `OnBackPressedDispatcher`, `setViewTreeOnBackPressedDispatcherOwner`, `BackHandler`, or `KEYCODE_BACK` reference exists in the quick-menu package — decision 7 held structurally, so it cannot creep back in as "harmless wiring" | Source-text scan over `feature/overlay/src/main` |
| Instrumented (emulator-5554, API 34) | Tap opens a second window with the pet's params byte-identical before and after; `ACTION_OUTSIDE` dismisses; pet re-tap and pet drag dismiss; the launch button starts the launcher `Activity`; Compose semantics — every interactive node described, every target ≥48dp, no undescribed scrim, **no editable text node anywhere in the card** | `androidTest`, overlay permission granted via `adb appops` |
| **Maintainer device only — not closable here** | OEM-skin variance; real IME behaviour on `TYPE_APPLICATION_OVERLAY`; the video-pause measurement **in both focus-only and full-IME modes**; whether focus returns after dismissal; the manual TalkBack pass | The `:spike:ime-viability` app plus a TalkBack pass, findings committed under `spike-findings/` |

The last row must not be marked satisfied by this pipeline. adb-injected input does not reach the
overlay on the maintainer's HyperOS device, so those criteria have no automated route by
construction, not by omission.

## Threat matrix

Mostly N/A — no shell, subprocess, VCS/PR automation, or executable-file classification. One
applicable row:

| Boundary | Applicable | Expected behavior | RED test |
|---|---|---|---|
| Process integration — the launch button starts an `Activity` from a `Service` | **Applicable** | An **explicit** `Intent` naming this app's own launcher component, with `FLAG_ACTIVITY_NEW_TASK`; never an implicit intent, never a component name derived from external or stored data; failure is caught and logged, never crashes the service | Instrumented: the launch button starts this app's launcher `Activity` and nothing else; unit: the constructed `Intent` carries an explicit component and `NEW_TASK` |
| Untrusted input crossing the window boundary | N/A | The card renders no user-supplied text in this change (no text field exists), and `ACTION_OUTSIDE` carries no usable coordinates to trust | — |

## Migration / rollout

No data migration. The `minSdk` bump drops installability on API 26–29 devices; the app is
pre-release with no such install base, and the decision is recorded in `build-foundation`. Revert
order is newest-first: card UI, controller + window params, Hunger flow, positioning and state
machine, spike module, `minSdk`. The spike sits low in that order despite landing early, because
nothing depends on it — it can also be reverted alone, at any point, without touching the card.
A full revert restores `onPetTapped` to its `Log.d` stub, and since
the touch layer and the pet window are never modified, today's behaviour returns exactly.

## PR boundaries

**The spike goes second, not last.** It now gates the back gesture as well as the IME work, so
every day it is not installed is a day the deviation above cannot be retired. Its real
dependencies, stated honestly, are: the `minSdk` bump (PR 1 — the spike targets the same
`ProjectConfig`, and building against 26 would make it measure a configuration that no longer
ships) and **nothing else**. It shares no code with the card: not the controller, not the
placement math, not `MetricReading`. That is a property of decision 13's separate module, and it
is what lets it jump the queue.

| PR | Content | Depends on | Est. changed lines |
|---|---|---|---|
| 1 | `minSdk` 30, dead compat branches removed, config/spec text | tracker branch | ~90 |
| 2 | **`:spike:ime-viability` module (both modes) and the findings record** | 1 | ~300 |
| 3 | `MetricReading`, `QuickMenuPlacement`, `QuickMenuState` + full pure test suite | 1 | ~330 |
| 4 | `ObserveHunger`, the two `TaskRepository`/DAO Flow counts, `DataModule` provider | 3 | ~230 |
| 5 | `QuickMenuWindowParams`, `QuickMenuWindowController`, service delegation | 4 | ~310 |
| 6 | `QuickMenuCard` UI, `MetricRow`, `QuickMenuConfig`, state-holder metrics, semantics tests | 5 | ~300 |

Each slice is under the 800-line budget alone; the chain is not. PR 1 targets the tracker branch;
PRs 2 and 3 both target PR 1 and are independent of each other, so the maintainer can install the
spike and start measuring on real hardware while the card is still being built. Every later PR
targets its predecessor (`stacked-to-main`).

If the spike reports early and cleanly on focus-only, the back gesture can be added as a PR 7
against the same chain, restoring #17's criterion inside this change rather than deferring it —
which is the outcome the reordering is for. If it reports late or negatively, nothing is blocked:
PRs 3–6 never referenced it.

## Open questions

- [x] **Resolved by the maintainer.** The first draft made the card focusable so the back gesture
      could work, and flagged that this pays #18's window-focus cost in the shell, before the spike
      reports. The ruling went against it: the card ships **non-focusable** (decision 6), the back
      gesture leaves this change entirely (decision 7), and the spike moves early and gains a
      focus-only measurement (decision 14a) so it can decide whether back is ever deliverable. The
      deviation from #17's back criterion is recorded above and written into
      `overlay-quick-menu` rather than left silently unmet.
- [ ] The card is dismissable only by touch now (outside tap, pet re-tap, pet drag) plus screen-off.
      That is total by construction (decision 9), but it is worth a maintainer's eye on real
      hardware: a card that can only be closed by touching something is fine until a skin swallows
      `ACTION_OUTSIDE` *and* the pet is under a system UI element. The pet-drag path is the last
      one standing in that scenario.
- [ ] The `AppLaunched` event closes the card. Launching the full app over a card that stays open
      would leave a floating card above a full-screen Activity the user just asked for; closing is
      the assumption, and it is cheap to reverse.
- [ ] `QuickMenuConfig`'s card width/height defaults have no product reference yet. They are
      injected config, so rebalancing stays a value change — but the first values are chosen
      visually against `@Preview`, not derived, and should be reviewed on hardware.
