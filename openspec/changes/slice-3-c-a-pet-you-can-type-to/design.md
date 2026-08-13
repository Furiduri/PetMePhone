# Design: Slice 3 part C — A pet you can type to (#18)

## Technical approach

Three seams, in dependency order.

1. **The window becomes focusable and sets `SOFT_INPUT_ADJUST_RESIZE`.** One indivisible change in
   `QuickMenuWindowParams`, justified entirely by measurement (facts 1–3 below).
2. **The card becomes a container hosting one content at a time.** Which content is showing is
   chosen by user action only — never by observing the keyboard, because no dependable keyboard
   signal exists on this window class.
3. **Back unwinds one step per press.** The keyboard level is the platform's, not ours; the two
   levels we own are decided by one pure function in `:core:domain`.

Satisfies `quick-menu-text-input` (new) and the `overlay-quick-menu` delta.

### Measured facts this design is built on

Xiaomi Redmi Note 14 Pro, HyperOS 3, API 36, sixteen runs across three rounds.

| # | Fact | Consequence here |
|---|---|---|
| 1 | `softInputMode` is a property of the IME target window only (`imeLayeringTarget`, `imeInputTarget`, `imeControlTarget` all one window) | Focus and `ADJUST_RESIZE` ship together or not at all |
| 2 | `ADJUST_PAN` measured no better than the no-strategy control; `ADJUST_RESIZE` measured fully visible and was approved on device | `ADJUST_RESIZE`, chosen on evidence |
| 3 | `WindowInsets.ime` is never delivered to this window class | No inset-driven layout, no `imePadding()` |
| 4 | `getWindowVisibleDisplayFrame` reported the resize on some runs and not others, same device, minutes apart | Not a signal; recorded as CONTROL only |
| 5 | Content top oscillates between resized and rest values ~8ms apart while the keyboard sits still | **No logic anywhere may key off keyboard state** |
| 6 | Taking window focus did not pause a playing video in any round | Focus cost is acceptable; measured, not assumed |
| 7 | The production card sets no `softInputMode` today; the `adjust=pan` in `dumpsys` is the system default | Nothing is being "changed from pan" — a value is being set for the first time |

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | Focus + IME strategy | Drop `FLAG_NOT_FOCUSABLE`; add `softInputMode = SOFT_INPUT_ADJUST_RESIZE` in `QuickMenuWindowParams.create`. `FLAG_NOT_TOUCH_MODAL` and `FLAG_WATCH_OUTSIDE_TOUCH` stay | `ADJUST_PAN`; anchor-to-top on focus; a manual keyboard-height reposition | Fact 1 makes the pair indivisible; fact 2 rejects pan on device evidence. Anchor-to-top also measured acceptable but repositions the window on focus, and the maintainer approved the resize run. A manual reposition needs a keyboard height, which facts 3–5 say cannot be obtained honestly |
| 2 | Focusability is set once | Focusability and `softInputMode` are properties of the params passed to `addView`. No `updateViewLayout` ever mutates flags at runtime | Toggle `FLAG_NOT_FOCUSABLE` on and off around field focus | A flag toggle has two states and therefore a wrong one, and the wrong one survives a crash between the toggles — that is exactly #18's "left focusable after dismissal" hazard. One value, set at attach, has no such interval |
| 3 | Never-focusable-after-dismissal guarantee | `closeWindow()` keeps removing the view and nulling the field; `destroy()` closes an open card; `PetOverlayService.onDestroy` calls `destroy()`; process death drops the window with the process | Hide the view and keep it attached | Slice 3-B's decision 8 already forbade hiding for exactly this reason, before the card was focusable. That decision now pays: there is no attached-but-invisible focusable window to leak, on any path |
| 4 | Where content selection lives | New pure `QuickMenuContent` (`Dashboard` \| `TaskInput`) in `:core:domain/overlay/`, held as **one field on `QuickMenuWindowController`**, passed into the container with an `onContentChange` callback | Widen `QuickMenuState` to `Open(anchor, content)`; keep it in a `remember`/`rememberSaveable` inside the container; put it on `PetOverlayService` | Widening `QuickMenuState` turns its load-bearing property — *every* event from `Open` yields `Closed`, so no undismissable state is reachable — into a claim about a product state, weakening the one argument that guarantees the user can always get out. Compose is structurally disqualified by decision 5. The service is disqualified by decision 5a. The controller is the one object whose lifetime is exactly the required one, and it already owns the window lifecycle the content is scoped to |
| 5 | Content **survives dismissal**; typed text does not | The controller's content field is **not** reset in `closeWindow()`. `openWindow` reads it and renders whatever content was active at dismissal. The typed text lives only in the composition and dies with it | Reset to `Dashboard` on every open; hold the content in the Compose tree | **Maintainer requirement:** left on the input, the card reopens on the input. This forces the state out of Compose entirely — `closeWindow()` removes the view and nulls the field (slice 3-B decision 8, kept deliberately so no focusable window survives dismissal), so the whole composition, and any `remember` in it, is destroyed on every close. `rememberSaveable` does not help either: there is no `Activity`, no saved-instance-state bundle, and no `SavedStateRegistry` on this window. The split is what makes the scope exact: **which content** is a controller field and survives; **the typed text** is composition-local and therefore discarded by the same removal that already happens, with no clearing call anyone can forget. That keeps drafts out of this change so #100's resumable step-by-step form remains the single draft mechanic |
| 5a | The state does **not** go on the service | `PetOverlayService` stays free of application state; it constructs the controller and holds it, exactly as today | A `lastContent` field on the service | The service's stated property is that it holds no state and rebuilds its whole window graph from zero on restart. The requirement does not need that property broken: the controller already outlives the card window (it is constructed once per service, and only its *view* is created and destroyed per open), so it provides the required lifetime without moving anything up a layer. If the state were on the service it would still die at exactly the same moments — so the move would buy nothing and cost a documented invariant |
| 5b | Lifetime boundary, stated exactly | **Survives:** window removal by outside touch, pet tap, pet drag, back, `AppLaunched`, `ScreenOff` — every dismissal path, because none of them touches the controller. **Destroyed:** `destroy()` on service teardown, and process death, both of which take the controller with them; the next open is `Dashboard`. **Never written to disk** | Persist it in `DataStore`/`SharedPreferences` so it survives process death | Explicitly out of scope per the requirement, and this change introduces no persisted storage. A killed process reopening on the dashboard is correct, not a bug. Persisting a UI position would also outlive its own meaning — a card reopened days later on an input the user has forgotten leaving is worse than a dashboard |
| 6 | Back ordering, level 1 (keyboard) | **Not implemented, and deliberately so.** While the IME is up it consumes the back press; our window never receives it | A `BackHandler` that hides the keyboard first | Implementing it would require knowing the keyboard is up, which fact 5 forbids. The platform already owns this level; adding our own would double-handle the press and skip level 2 |
| 7 | Back ordering, levels 2–3 | One pure `resolveBack(content): BackOutcome` in `:core:domain/overlay/`: `TaskInput → ShowDashboard`, `Dashboard → CloseCard`. The controller applies it; only `CloseCard` forwards `QuickMenuEvent.BackPressed` into `reduce` | Branch on content inside the composable; two separate `BackHandler`s | A press we receive is by definition a press the IME did not take, so the ordering reduces to a two-case total function over a two-case type — exhaustively unit-testable with no device. Two handlers reintroduce ordering between themselves, which is the bug being avoided |
| 8 | Back mechanism | `ComposeOverlayHost` gets an `OnBackPressedDispatcherOwner` via `setViewTreeOnBackPressedDispatcherOwner`; the container hosts exactly **one** `BackHandler` calling `onBack` | Override `dispatchKeyEvent` for `KEYCODE_BACK` | #17's criterion names explicit dispatcher wiring, and the dispatcher is the path predictive back on API 36 actually drives. Raw key interception misses gesture back on some configurations and would silently be the "works in a test, not in a hand" failure slice 3-B refused |
| 9 | `QuickMenuEvent.BackPressed` | Added as an additive case; `reduce(Open, BackPressed) = Closed`, `reduce(Closed, BackPressed) = Closed` | Reuse `OutsideTouch` for back | The reducer stays total and its "every event from `Open` closes" property is preserved and re-asserted. Reusing `OutsideTouch` would also trip the controller's `SAME_GESTURE_WINDOW_MS` suppression, which exists for a touch coincidence back has nothing to do with |
| 10 | The field is focusable on tap only | No `FocusRequester`, no auto-focus, no `showSoftInput` call on open — including when decision 5 reopens the card *directly onto* the input content | Focus the field when the input content appears | Auto-focus would raise a keyboard the user did not ask for over an app that just lost focus. Decision 5 makes this sharper rather than softer: reopening onto the input content is now a path where the field appears without the user having tapped it in this window's lifetime, so auto-focus there would raise a keyboard from a *pet tap*. Tap-to-focus keeps the keyboard a second, explicit decision on every path |
| 11 | Configuration values | `QuickMenuConfig` gains `taskTitleMaxLength: Int` and `inputContentMinHeightDp: Int`, `@Provides` in `OverlayModule` | Inline literals in the input composable | The standing injected-config rule. The 48dp touch target and the existing corner/padding constants stay where they are: those are platform and styling constants, not values anyone rebalances |
| 12 | No task-domain import | The input content's submit callback is `(String) -> Unit`, wired to a no-op logging lambda in the controller | Call `CreateOneOffTask` now | #100 owns submission. A `:core:domain/task` import in this change is the leak the proposal names as a risk; keeping the callback shape means #100 replaces one lambda |

### One proposal success criterion is superseded (decision 5)

The proposal's first success criterion reads *"The card opens on the dashboard content with no
keyboard."* The maintainer's reopen-restoration requirement, decided after the proposal was
written, changes its first half. It is recorded here rather than silently satisfied by a weaker
reading, and the criterion should be restated in the spec as:

- The card opens on the content it was last dismissed on, and on the dashboard content on its
  first open and after process death.
- **No open raises a keyboard**, on any path — unchanged, and now load-bearing on the restoration
  path too (decision 10).

The second half is strengthened, not weakened: reopening onto the input content is precisely the
path where a careless auto-focus would raise a keyboard from a pet tap.

## Data flow

```
[open]     PetTapped(anchor) ──► reduce(Closed, e) = Open(anchor)        [:core:domain, pure]
                              ──► content = controller.content           decisions 5, 5b
                                     (whatever was active at the last dismissal;
                                      Dashboard on the first open and after process death)
                              ──► addView(host, QuickMenuWindowParams.create(...))
                                      focusable + SOFT_INPUT_ADJUST_RESIZE   decisions 1, 2
                                      + setViewTreeOnBackPressedDispatcherOwner  decision 8

[swap]     add-task tapped ──► onContentChange(TaskInput) ──► controller.content = TaskInput
           leave input     ──► onContentChange(Dashboard)
                               (no keyboard signal is read on either path — fact 5)

[type]     field tapped ──► window is IME target ──► ADJUST_RESIZE ──► field fully visible
                            no ime() inset, no visible-frame read, no measurement   facts 3, 4

[back]     IME up  ──► consumed by the IME, never reaches this window      decision 6
           reaches us ──► resolveBack(content)                             [:core:domain, pure]
                              TaskInput  → ShowDashboard → content = Dashboard   (window stays)
                              Dashboard  → CloseCard     → onEvent(BackPressed)
                                                         → reduce(Open, e) = Closed → removeView

[close]    every path ──► host.destroy(); removeView; view = null         decision 3
```

## Interfaces

```kotlin
// :core:domain/overlay/QuickMenuContent.kt        pure; no android.* import
sealed interface QuickMenuContent {
    data object Dashboard : QuickMenuContent
    data object TaskInput : QuickMenuContent
}

sealed interface BackOutcome {
    data object ShowDashboard : BackOutcome   // level 2: unwind the container
    data object CloseCard : BackOutcome       // level 3: dismiss the window
}

/** Total over [QuickMenuContent]. Level 1 (the keyboard) is absent by design: a back press that
 *  reaches this window is one the IME did not consume, so no keyboard state is read. */
fun resolveBack(content: QuickMenuContent): BackOutcome

// :core:domain/overlay/QuickMenuState.kt — additive
sealed interface QuickMenuEvent { /* ... */ data object BackPressed : QuickMenuEvent }
```

## File changes

| Path | Action | Purpose |
|---|---|---|
| `feature/overlay/.../service/QuickMenuWindowParams.kt` | Modify | Drop `FLAG_NOT_FOCUSABLE`; set `softInputMode`; kdoc records facts 1–2 (decision 1) |
| `feature/overlay/.../service/OverlayWindowParams.kt` | Unchanged | The pet window's params are never mutated — an #18 criterion |
| `core/domain/.../overlay/QuickMenuContent.kt` | Create | Decisions 4, 7 |
| `core/domain/.../overlay/QuickMenuState.kt` | Modify | `BackPressed` case, decision 9 |
| `feature/overlay/.../quickmenu/QuickMenuWindowController.kt` | Modify | Hoisted content, back dispatcher owner, `resolveBack` application (decisions 4, 7, 8) |
| `feature/overlay/.../ui/ComposeOverlayHost.kt` | Modify | `OnBackPressedDispatcherOwner` (decision 8) |
| `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` | Modify | Becomes the container: hosts one content, one `BackHandler`; today's rows move into `QuickMenuDashboardContent` |
| `feature/overlay/.../quickmenu/ui/QuickMenuDashboardContent.kt` | Create | Today's three rows + launch button, appearance unchanged; add-task control enabled as the swap trigger |
| `feature/overlay/.../quickmenu/ui/QuickMenuTaskInputContent.kt` | Create | The field, submit and back-to-dashboard actions |
| `feature/overlay/.../quickmenu/QuickMenuConfig.kt` | Modify | Decision 11 |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | New config values |
| `.../test/.../NoBackGestureCodeTest.kt` | Replace | → `QuickMenuBackWiringCodeTest`, below |
| `.../androidTest/.../QuickMenuBackGestureDoesNotDismissTest.kt` | Delete | Replaced, below |

### The two contradicted tests (proposal's "deliberate revision/removal")

Neither is dropped silently.

**`NoBackGestureCodeTest`** was a structural gate asserting four back references are *absent*. It is
**inverted, not deleted**, into `QuickMenuBackWiringCodeTest`: exactly one
`setViewTreeOnBackPressedDispatcherOwner` and exactly one `BackHandler` exist in the quick-menu
package, and `KEYCODE_BACK` appears nowhere (decision 8's single mechanism). The gate's purpose —
back handling cannot drift in unnoticed — is preserved and pointed the other way: it now catches a
*second* handler, which is the failure mode that would skip a level.

**`QuickMenuBackGestureDoesNotDismissTest`** is deleted with no instrumented successor. It asserted
that a `sendKeyDownUpSync(KEYCODE_BACK)` did not remove the card — an assertion that also passes if
the key never arrived at all, and on this device adb-injected input does not reach the overlay. An
inverted version ("back *does* dismiss") would therefore fail for the honest reason and be
un-diagnosable. Its coverage is redistributed: the ordering to a pure unit test, the wiring to the
structural gate, the observable three-level behaviour to the manual row.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit `:core:domain` | `resolveBack` total over both contents; `reduce` re-asserted total including `BackPressed`, with the named "every event from `Open` yields `Closed`" case extended | JUnit4, pure Kotlin, no device |
| Unit `:feature:overlay` (Robolectric) | `QuickMenuWindowParams` does **not** set `FLAG_NOT_FOCUSABLE`, **does** set `softInputMode == SOFT_INPUT_ADJUST_RESIZE`, and still sets `FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH`; `OverlayWindowParams` is byte-identical to today | Robolectric |
| Unit `:feature:overlay` (source scan) | `QuickMenuBackWiringCodeTest` (above) | Source-text scan over `feature/overlay/src/main` |
| Compose semantics | Container shows exactly one content; add-task swaps to input; leaving restores the dashboard; dispatching back once from the input yields the dashboard and does **not** dismiss; dispatching back from the dashboard invokes dismissal exactly once. Accessibility asserted by iterating `onAllNodes(hasClickAction())` — every node described, every target ≥48dp — never by naming test tags | `createComposeRule` with a real `OnBackPressedDispatcher` |
| Unit `:feature:overlay` (Robolectric, decision 5) | **Restoration is pure controller logic and fully unit-testable.** Open → swap to `TaskInput` → dismiss via each of `OutsideTouch`, `PetTapped`, `PetDragged`, `BackPressed`, `AppLaunched`, `ScreenOff` → reopen renders `TaskInput`, once per dismissal path so none is special-cased; left on `Dashboard`, reopens on `Dashboard`; a fresh controller opens on `Dashboard`; **`destroy()` then reopen yields `Dashboard`** (decision 5b's teardown boundary) | Robolectric + shadow `WindowManager`, asserting the content passed to the container — no device |
| Unit `:feature:overlay` (source scan, decision 5b) | No `DataStore`, `SharedPreferences`, `Room`, or file write appears anywhere in the quick-menu package — "introduces no persisted storage" held structurally, so a restoration that quietly grows a disk write cannot land unnoticed | Source-text scan over `feature/overlay/src/main` |
| Compose semantics (decision 5) | Typed text is **not** restored: entering text, disposing the composition, and recomposing on `TaskInput` yields an empty field. This is the half of the split that Compose owns, and it is asserted separately from the content half so a regression cannot be read as the other passing | `createComposeRule` |
| Instrumented (`WindowManager`) | The card window is added with the expected focus flags and `softInputMode`; `addView`/`removeView` land at the right lifecycle points; `destroy()` leaves no view attached; the pet window's params are unchanged before and after | `androidTest`, overlay permission via `adb appops` |
| **Manual, per device — not closable here** | Keyboard appears on field tap; field fully visible while typing; back unwinds exactly one step per press across all three levels; behaviour at every screen edge; no video pause. **Decision 5's manual half:** that reopening onto the input content raises no keyboard by itself (decision 10), and that a real process kill — not `destroy()` — reopens on the dashboard | Device pass, recorded like the spike findings |

The manual row must not be marked satisfied by the pipeline: adb-injected input does not reach the
overlay on this device, so those criteria have no automated route by construction.

## Threat matrix

| Boundary | Applicable | Expected behavior | RED test |
|---|---|---|---|
| Untrusted input crossing the window boundary | **Applicable** — the card now renders user-supplied text for the first time | The typed string is length-bounded by `taskTitleMaxLength`, rendered as text only, never persisted, never used to build an `Intent`, a component name, a file path, or a log key. Submit passes it to a no-op callback (decision 12) | Unit: a max-length-exceeding string is rejected at the field, not truncated silently downstream. Source scan: no task-domain import in the quick-menu package |
| Process integration — launch button starts an `Activity` from a `Service` | Applicable, **unchanged** | As slice 3-B: explicit intent from `getLaunchIntentForPackage`, `FLAG_ACTIVITY_NEW_TASK`, failure logged not crashed | Existing tests retained |
| Routing, shell, subprocess, VCS/PR automation, executable-file classification | N/A | No such boundary exists in this change | — |

## Migration / rollout

No data migration, no schema, no persisted state. Rollback is a pure code revert of the params flag,
the container split, and the back wiring; the window returns to non-focusable and the two retired
tests are restorable from history.

## Tracked deviation: #18's two-OEM-skin criterion is not met

#18 requires manual verification on at least two different OEM skins. **This change does not satisfy
it.** No Samsung-class device is available; **#82** is open recording exactly this gap. It is
carried as a named tracked deviation pointing at #82 — not satisfied, not dropped, and not a blocker
on this change, mirroring how slice 3-B carried #17's back criterion. Every measured fact above is
from one device and one skin, and the `ADJUST_RESIZE` result is the one most likely to vary.

## Open questions

- [ ] Whether the IME consumes the first back press (decision 6) is a platform behaviour asserted
      from the API contract, not from a spike run. It is a manual-row item; if a skin delivers back
      to the window with the keyboard up, level 1 is skipped and the card unwinds a step early.
- [ ] `taskTitleMaxLength` has no product reference yet. It is injected config, so a change is a
      value change — but the first value is chosen, not derived.
- [ ] The input content's height under `ADJUST_RESIZE` on a small screen or large font scale was not
      measured; the existing `verticalScroll` ceiling is the mitigation, unverified for this content.
