# Exploration — slice-3-c-a-pet-you-can-type-to

Issue #18 — `feat(overlay): focusable text input and IME handling in the quick menu`.

Phase: `sdd-explore`. Artifact store: `openspec`. Evidence revision: `dbfb13e` on `master`.

Scope note: issue #27 (wire submit to task creation) is out of scope here. The revised `docs/build-order.md` records that #100 supersedes #27; the seam is analysed in Q7 below.

## Current state

The quick-menu card (#17, archived in slice 3-B) already lives in its own `WindowManager` window:

- `feature/overlay/src/main/kotlin/com/gcatcode/petmephone/feature/overlay/service/QuickMenuWindowParams.kt` — `create()` builds `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH`, `TYPE_APPLICATION_OVERLAY`, `WRAP_CONTENT` height, no `FLAG_ALT_FOCUSABLE_IM`.
- `feature/overlay/src/main/kotlin/.../service/PetOverlayService.kt` — constructs the controller once in `onCreate()`, calls `quickMenuController?.destroy()` in `onDestroy()`. Its kdoc states it holds no application state; the window graph is rebuilt from zero on process restart.
- `feature/overlay/src/main/kotlin/.../quickmenu/ui/QuickMenuCard.kt` — renders three metric rows and a launch button. The "add task" control is a **disabled** `OutlinedButton`, commented: creating a task needs the text field from #18.
- `core/domain/src/main/kotlin/.../overlay/QuickMenuState.kt` — `Closed | Open(anchor)` over a total `reduce`. No `BackPressed` event.
- `NoBackGestureCodeTest.kt` — structurally forbids `OnBackPressedDispatcher`, `BackHandler`, `KEYCODE_BACK` and `setViewTreeOnBackPressedDispatcherOwner` anywhere under the `quickmenu` package.

`docs/build-order.md` (commit `2e72072`) now orders slice 3 as: #82 → #87 → #18 → #98 → #100 → #91 → #92, and states plainly that #82 is unresolved because the maintainer has no Samsung-class device.

## Q1 — What survives from the #82 spike

`spike/ime-viability/` is a separate throwaway `com.android.application` module (design decision 13 of slice 3-B: deliberately outside `:app`'s graph and outside every release variant). It contains `SpikeActivity.kt`, `SpikeOverlayService.kt`, `SpikeWindowParams.kt`, `SpikeMode.kt`, `FindingsEntry.kt`, `FindingsRepository.kt`, `HumanAnswer.kt`, `DeviceInfo.kt`.

`SpikeWindowParams.create()` builds a **focusable** window (no `FLAG_NOT_FOCUSABLE`) with `FLAG_WATCH_OUTSIDE_TOUCH or FLAG_NOT_TOUCH_MODAL`, `MATCH_PARENT` width, gravity `TOP or CENTER_HORIZONTAL`. `SpikeOverlayService.addOverlayWindow()` builds a plain `LinearLayout` with a `TextView` banner and an `EditText`, attaches `ViewCompat.setOnApplyWindowInsetsListener` reading `WindowInsetsCompat.Type.ime()`, and calls `imm.showSoftInput(..., SHOW_FORCED)` after `container.post {}`. It sets no `softInputMode`.

**Verdict: throwaway instrument, not reusable production-shaped code.** It shares no class with `QuickMenuWindowParams`/`QuickMenuWindowController`, deliberately. Worse, it measures the wrong signal: `keyboardAppeared` is derived from an `ime()` inset this window class never reports, so it recorded `false` on every run while the keyboard was demonstrably usable; `keyboardCoversField` is only computed inside `if (keyboardAppeared)`, so its recorded value is an untouched default rather than a measurement.

The trustworthy artifacts to build on are `openspec/changes/archive/2026-08-13-slice-3-b-a-pet-you-can-talk-to/spike-findings/redmi-note-14-pro-hyperos3-api36.md` and `.../spike-findings/conclusions.md` — not the spike's Kotlin.

## Q2 — Candidate strategies for keeping the field visible

Given that IME insets are never delivered on this window class:

| Approach | How | Pros | Cons / still unverified |
| --- | --- | --- | --- |
| `softInputMode = adjust=pan` | Set `LayoutParams.softInputMode = SOFT_INPUT_ADJUST_PAN` in `QuickMenuWindowParams` | No new listener code; `dumpsys` showed `adjust=pan` present during the spike with the field usable; #18 confirms this `LayoutParams` field is real, unlike the manifest attribute | The spike never isolated it as causal. #18 itself flags `softInputMode` on overlay windows as OEM-unreliable. Pan shifts window content, and its interaction with the existing gravity/anchor math (`QuickMenuPlacementResult.verticalAnchor`) is unverified |
| Manual reposition from a measured keyboard height | Derive the height from a non-inset signal, shift `LayoutParams.y`, `updateViewLayout` | Independent of OEM `softInputMode` behaviour; full control | Needs a verified height source, and Q3 shows none exists. Adds a second geometry system beside `QuickMenuPlacement.place()`, risking a repeat of the three prior placement bugs that each came from careful reasoning instead of measurement |
| Anchor-to-top while focused | On field focus, pin the card to the top of the usable bounds | Needs no keyboard height at all; composes with the existing `VerticalAnchor.TOP` case | Assumes the keyboard never covers the screen top — true on typical portrait phones, not a proven guarantee. A card that jumps on focus is a visible relocation needing its own acceptance criterion |

None is verified end to end on real hardware. Both the issue text and the spike conclusions agree that the choice must be measured, not reasoned.

## Q3 — How keyboard height is observable at all

Candidates investigated, given `ime()` insets are proven undelivered:

- **`ViewTreeObserver` global-layout heuristic** (root height before/after) — plausible fallback, unverified on this window type, and deprecated by Android in favour of insets precisely because it is OEM-unreliable, which is the same risk class the issue already warns about.
- **`navigation_bar_height` resource + display metrics diffing** — not a keyboard-height source. Irrelevant.
- **`dumpsys window windows`** — how `adjust=pan` was confirmed present during the spike. A manual verification tool, not runtime-queryable by the app.
- **Hard-coded or configured estimate** — a literal violates the injected-config rule, and a fixed guess misestimates across devices, orientations and keyboard apps: exactly the bug class the measure-never-reason constraint exists to prevent.
- **A second probe window listening to `onWindowFocusChanged`/layout changes** — closest to the spike's *focus* signal, which was trustworthy (`everReceivedWindowFocus` worked). No equivalent trustworthy height signal exists yet.

**Honest conclusion: nothing in the codebase or the spike proves a verifiable keyboard-height signal on this window class.** This is a real open technical question and should be the first thing any #18 prototype measures. Anchor-to-top is attractive partly because it is the one candidate needing no such signal.

## Q4 — The back gesture

Slice 3-B recorded #17's back-gesture criterion as a tracked deviation: a non-focusable window receives no key events, and `NoBackGestureCodeTest` enforces that at source-text level. Design decisions 6, 7 and 14a of that change establish that back-gesture eligibility is gated on window focus alone, not on the keyboard.

The HyperOS spike found **zero video-pause cost** for taking window focus, in both focus-only and full-IME modes, across seven runs, with clean focus return every time. That is the evidence that technically unblocks the back gesture on one OEM.

Newly possible: attaching an `OnBackPressedDispatcher` to the now-focusable card window, satisfying #17's original criterion and #18's own "dismiss keyboard first, card on second press".

Newly costly:

1. `NoBackGestureCodeTest` must be deliberately removed or rewritten — it fails the build the moment any forbidden reference appears. Shipping back-gesture code is a visible decision, not an accidental unlock.
2. The focus-loss risk to the app underneath, previously deferred entirely by non-focusability, becomes real and must be measured per device. The Redmi result does not generalise.
3. `QuickMenuWindowParams`' kdoc already anticipated this: the card's flags are the ones expected to change first if a spike reopens focus.
4. `QuickMenuState` has no `Focused`/`Submitted` state and no `BackPressed` event. Design decision 9 built it as an honest subset meant to be extended, so the additions are additive but need new reducer cases and new tests.

## Q5 — Focus lifecycle and the never-left-focusable guarantee

Not implemented yet; squarely inside #18's scope. From what exists:

- **Created focusable**: focusability should be a property of the params at `addView` time, produced by `QuickMenuWindowParams.create()` (or a variant), not a runtime flag toggle. `updateViewLayout` is used in this codebase only for position (`PetOverlayService.applyPosition()`), never for flag mutation.
- **Stops being focusable**: `QuickMenuWindowController.closeWindow()` already does `host.destroy()` then `windowManager.removeView(host)` and nulls the field. This is removal, not hide-and-keep-attached — design decision 8 rejected the latter precisely because a hidden-but-attached window would be the one that remains focusable after close. The existing removal discipline already satisfies most of the criterion, provided the focusable variant keeps it.
- **Process death / service restart**: `PetOverlayService` holds no application state and rebuilds from zero; `onDestroy()` calls `quickMenuController?.destroy()`, which closes an open window. A hard kill without `onDestroy()` is torn down by the OS with the process, since the window belongs to the process's `WindowManager` connection. Worth stating explicitly as a criterion note rather than leaving implicit.

## Q6 — Automatable versus manual-only

**Automatable:**

- The focus/dismissal state machine as pure logic in `:core:domain` — `Closed → Open → Focused → Submitted → Closed` or the superset #18 needs, unit tested with no device, following decision 9's precedent.
- Compose semantics on the field — content description, 48dp minimum target, IME action semantics — iterating `onAllNodes(hasClickAction())` per node, never named test tags.
- An instrumented test (extending or replacing `QuickMenuBackGestureDoesNotDismissTest`) asserting the window is created with the right focus flags and that `addView`/`removeView` happen at the right lifecycle points, against a real `WindowManager`, as `QuickMenuWindowLifecycleTest.kt` already does.
- A revised structural test replacing `NoBackGestureCodeTest`.

**Manual-only, per device:**

- Whether a playing video pauses when the card takes focus.
- Whether the keyboard appears, and whether it covers the field.
- Whether the chosen visibility strategy keeps the field visible.
- Back-gesture ordering: keyboard first, card second.
- Behaviour at every screen edge.
- Cross-OEM verification on at least two skins.

## Q7 — The seam between #18 and #100

The revised `docs/build-order.md` records that **#100 supersedes #27**: #27 specified a single field wired to task creation, but #98 (habit core) makes the minimum version a required field, so a one-field submit can no longer produce a valid task. #100, not #27, is the real next consumer of #18's field.

The seam: #18 delivers a focusable, IME-capable text field with correct focus lifecycle and submit/dismiss affordances, but its `onSubmit` terminates in locally-held state, **not** a real `CreateOneOffTask` invocation. #18 owns the `QuickMenuState` extension and the window mechanics; #100 owns what `Submitted` does downstream. Any call into the task domain from #18 is scope creep past a boundary the build-order document already drew.

## Q8 — The #82 second-OEM gap

`docs/build-order.md` lists #82 as blocking #18 and all of #100, explicitly before finishing slice 3. Issue #82 records the gap as standing rather than dropped: acceptable while overlay text entry is unreleased, unacceptable before a public release that assumes it works everywhere.

So #18's criterion "verified manually on at least two different OEM skins" **cannot be honestly checked off** without new hardware. The honest path, matching how slice 3-B handled #17's back-gesture criterion:

- Implement and ship #18 verified on the one available skin, with every other criterion satisfied and tested per Q6.
- Record the two-OEM criterion as an explicit, named tracked deviation in the design and verify artifacts, pointing at #82 — not closed, not silently dropped.
- Keep the deviation visible until Samsung-class hardware exists or a public release forces it.

## Affected areas

- `feature/overlay/.../service/QuickMenuWindowParams.kt` — focus flags, possibly `softInputMode`
- `feature/overlay/.../quickmenu/QuickMenuWindowController.kt` — open/close lifecycle, any focus-driven repositioning
- `core/domain/.../overlay/QuickMenuState.kt` — reducer extension for focused/submitted, local only
- `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` — replace the disabled `OutlinedButton` with a real, locally-scoped text field
- `feature/overlay/src/test/kotlin/.../quickmenu/NoBackGestureCodeTest.kt` — deliberately revised or removed if the back gesture ships
- `feature/overlay/src/androidTest/kotlin/.../quickmenu/QuickMenuBackGestureDoesNotDismissTest.kt` — same
- `spike/ime-viability/` — its `keyboardAppeared`/`keyboardCoversField` signal is broken and must be fixed before any second-device run under #82
- `openspec/changes/archive/2026-08-13-slice-3-b-a-pet-you-can-talk-to/spike-findings/conclusions.md` — authoritative record of what is and is not measured

## Approaches

1. **`softInputMode = adjust=pan`** — low implementation effort, medium verification effort. Not isolated as causal; OEM reliability unverified; unverified interaction with the anchor math.
2. **Manual reposition from a measured height** — high effort. No verified height signal exists; adds a second geometry system in the exact area that has already produced three reasoned-not-measured bugs.
3. **Anchor-to-top while focused** — medium effort. Needs no height signal; requires its own UX criterion for the visible relocation.

## Recommendation

Do not pick a visibility strategy from this exploration alone. The issue mandates a prototype before design, and no candidate is verified end to end.

1. Fix the known-bad `keyboardAppeared`/`keyboardCoversField` signal first, per #82's own instruction.
2. Run a small spike extension testing approach 1 and approach 3 side by side on the one available device. Approach 2 is blocked on an unsolved measurement problem.
3. Treat approach 3 as the fallback default if `adjust=pan` proves unreliable, since it needs no new measurement infrastructure.
4. Proceed to `sdd-propose` scoped to stop at the #18/#100 seam, carrying the #82 two-OEM gap as a tracked deviation rather than a blocking precondition for #18's closure.

## Risks

- No verified keyboard-height signal exists on this window class; approach 2 may be infeasible entirely.
- `softInputMode` reliability across OEMs is unverified, and the second skin needed to know whether HyperOS generalises is unavailable.
- Enabling window focus reopens the app-underneath risk, measured as zero-cost on one device only.
- `NoBackGestureCodeTest` is a hard structural gate that must be changed deliberately and visibly.
- The #82 gap has no resolution path today; #18's two-OEM criterion cannot be honestly checked off without new hardware.
- Scope creep at the #18/#100 seam: wiring submit to real task creation belongs to #100.

## Ready for proposal

Partially. Two maintainer decisions are outstanding:

1. Whether a small measurement spike precedes the proposal, per the issue's own prototype-before-design instruction.
2. Whether the #82 two-OEM gap is accepted as a tracked deviation in #18's proposal rather than a blocking precondition.
