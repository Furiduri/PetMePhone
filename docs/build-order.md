# Build order

**Milestones group issues by concern. They are not a build order.** This document is the order to actually build in.

Working milestone by milestone would mean nothing runs on a real phone until M2 is finished — and the pet, which is the whole identity of the product, would not be animated until M4. This orders every open issue as thin vertical slices instead, so something works on a device early and keeps working after every slice.

## Why this exists

The milestones were planned as horizontal layers: build, then overlay, then task domain, then metrics. That is a good way to *think* about the system and a poor way to *construct* it. With horizontal layers you have nothing demonstrable until the last layer closes.

For a solo project that cost is not just technical. Seeing your own pet moving on your own screen in week one is what sustains the work long enough to reach the clean architecture. A project you cannot show anyone — including yourself — is easy to abandon.

Each slice below ends with something you can install and look at.

## Quick path

| Slice | What you get on the phone |
|---|---|
| 1 | A pet floating over your apps, breathing |
| 2 | You can drag it, it stays where you put it, and it can be *your* drawing |
| 3 | You can create a habit from the overlay and watch Hunger move — and tune the balance without rebuilding |
| 4 | Recurring tasks, a checklist that reads as doable, and Happiness |
| 5 | Screen-time tracking and all three metrics live |
| 6 | The pet reacts, and completing a habit unlocks its animation |
| 7 | The full-screen app, statistics and the journal |
| 8 | Habits that stick — the guided day walk, an adaptive goal, and never miss twice |
| 9 | It survives reboots, and your data can leave the device |

## Answer these before you need them

Empirical work, not discussion. Two of them change whole issues, so resolve those before writing the code that depends on them.

| Question | Blocks | When |
|---|---|---|
| ~~[#9](https://github.com/Furiduri/PetMePhone/issues/9) Foreground service type~~ — **closed** | [#13](https://github.com/Furiduri/PetMePhone/issues/13), much of [#47](https://github.com/Furiduri/PetMePhone/issues/47) | Resolved |
| ~~[#2](https://github.com/Furiduri/PetMePhone/issues/2) `CommonExtension` arity on AGP 9.3.1~~ — **closed** | The convention plugin signatures | Resolved |
| [#82](https://github.com/Furiduri/PetMePhone/issues/82) Overlay IME on a second OEM skin | [#18](https://github.com/Furiduri/PetMePhone/issues/18), and all of [#100](https://github.com/Furiduri/PetMePhone/issues/100) | **Before finishing slice 3** |
| [#20](https://github.com/Furiduri/PetMePhone/issues/20) `lib-recur` maintenance status | Library versus hand-rolled subset | Before slice 4 |
| [#31](https://github.com/Furiduri/PetMePhone/issues/31) Play classification for usage access | Submission, not development | Before release |

[#82](https://github.com/Furiduri/PetMePhone/issues/82) is now the highest-leverage unknown in the project, and it was promoted from a footnote for a reason. The spike mandated by [#18](https://github.com/Furiduri/PetMePhone/issues/18) ran and covered **one** OEM skin — Xiaomi HyperOS 3.0 on API 36. Samsung One UI is untested because no device is available.

Two findings from that spike already shape the design and are worth having in front of you:

- **IME insets are never delivered to an overlay window.** The listener attaches and fires but never reports a non-zero `ime()` inset, so `imePadding()` and `WindowInsets.ime` are unusable on this surface. This is why [#100](https://github.com/Furiduri/PetMePhone/issues/100) mandates one input per card rather than a stacked form — a single field can be pinned where a keyboard cannot cover it, and a stacked one cannot
- **Text entry works on the one tested skin.** If One UI turns out not to show a keyboard for a `TYPE_APPLICATION_OVERLAY` window at all, the overlay half of [#100](https://github.com/Furiduri/PetMePhone/issues/100) has no path and creation falls back to the full-screen app

Two question issues remain open **as a record** and appear in no slice, because their decisions were folded into the issues they blocked:

- [#10](https://github.com/Furiduri/PetMePhone/issues/10) — Energy accumulates continuously; a raw time ledger is persisted and points are a pure function over it
- [#22](https://github.com/Furiduri/PetMePhone/issues/22) — Carry-over is unbounded with deletion as the exit; the make-up flag never compounds. **Its third decision, the Happiness floor, is superseded by [#95](https://github.com/Furiduri/PetMePhone/issues/95)**

[#105](https://github.com/Furiduri/PetMePhone/issues/105) is the issue for this document and likewise has no slice.

> **Housekeeping, not blocking:** several issues from shipped slices are still open — [#12](https://github.com/Furiduri/PetMePhone/issues/12), [#15](https://github.com/Furiduri/PetMePhone/issues/15), [#16](https://github.com/Furiduri/PetMePhone/issues/16), [#39](https://github.com/Furiduri/PetMePhone/issues/39) from slice 2, and [#36](https://github.com/Furiduri/PetMePhone/issues/36) which was deliberately partial. Verify each against the code and close what is done, so open state means something again.

---

## Slice 1 — A pet on screen ✅

Shipped. The first thing you can show someone: a pet renders and idles, nothing persists.

| Order | Issue |
|---|---|
| 1 | [#1](https://github.com/Furiduri/PetMePhone/issues/1) Module skeleton |
| 2 | [#2](https://github.com/Furiduri/PetMePhone/issues/2) Convention plugins |
| 3 | [#3](https://github.com/Furiduri/PetMePhone/issues/3) Version catalog and test infrastructure |
| 4 | [#6](https://github.com/Furiduri/PetMePhone/issues/6) Hilt object graph |
| 5 | [#11](https://github.com/Furiduri/PetMePhone/issues/11) Overlay permission mechanics |
| 6 | [#13](https://github.com/Furiduri/PetMePhone/issues/13) `PetOverlayService` |
| 7 | [#14](https://github.com/Furiduri/PetMePhone/issues/14) `ComposeOverlayHost` |
| 8 | [#36](https://github.com/Furiduri/PetMePhone/issues/36) Sprite format and renderer — **the IDLE row only** |

## Slice 2 — A pet you can move, and make yours ✅

Shipped.

| Order | Issue |
|---|---|
| 1 | [#37](https://github.com/Furiduri/PetMePhone/issues/37) Priority-ordered state resolution |
| 2 | [#15](https://github.com/Furiduri/PetMePhone/issues/15) Drag and edge snap |
| 3 | [#16](https://github.com/Furiduri/PetMePhone/issues/16) Position persistence |
| 4 | [#39](https://github.com/Furiduri/PetMePhone/issues/39) User character import |
| 5 | [#12](https://github.com/Furiduri/PetMePhone/issues/12) Overlay onboarding screen |
| 6 | [#7](https://github.com/Furiduri/PetMePhone/issues/7) CI pipeline |
| 7 | [#53](https://github.com/Furiduri/PetMePhone/issues/53) Issue link and type label checks |

## Slice 3 — A pet you can feed 🚧

**Partially shipped.** The card shipped with its metrics; the text-entry half did not. Verified against the code: there is no `TextField` or `BasicTextField` anywhere in the overlay feature outside the character import and preview screens, and no reference to `CreateOneOffTask` from any overlay code.

| Order | Issue | State |
|---|---|---|
| 1 | [#29](https://github.com/Furiduri/PetMePhone/issues/29) Balance configuration | ✅ |
| 2 | [#23](https://github.com/Furiduri/PetMePhone/issues/23) Task and occurrence schema | ✅ |
| 3 | [#26](https://github.com/Furiduri/PetMePhone/issues/26) Task creation use case | ✅ |
| 4 | [#33](https://github.com/Furiduri/PetMePhone/issues/33) Hunger | ✅ |
| 5 | [#17](https://github.com/Furiduri/PetMePhone/issues/17) Quick menu card shell | ✅ |
| 6 | [#82](https://github.com/Furiduri/PetMePhone/issues/82) Second OEM skin for overlay IME | **next** |
| 7 | [#87](https://github.com/Furiduri/PetMePhone/issues/87) Quick menu placement and dismissal defects | |
| 8 | [#18](https://github.com/Furiduri/PetMePhone/issues/18) Focusable text input and IME | |
| 9 | [#98](https://github.com/Furiduri/PetMePhone/issues/98) The habit core | |
| 10 | [#100](https://github.com/Furiduri/PetMePhone/issues/100) Step-by-step creation form | |
| 11 | [#91](https://github.com/Furiduri/PetMePhone/issues/91) Persisted configuration store | |
| 12 | [#92](https://github.com/Furiduri/PetMePhone/issues/92) Balance tuning panel | |

**Verify:** tap the pet, walk the form, create a habit with its minimum version, watch Hunger move. Then open the debug tuning panel, change `dailyTaskGoal`, and watch the same metric respond without rebuilding.

⚠️ **[#100](https://github.com/Furiduri/PetMePhone/issues/100) supersedes [#27](https://github.com/Furiduri/PetMePhone/issues/27).** #27 specifies a single text field wired to task creation. [#98](https://github.com/Furiduri/PetMePhone/issues/98) makes the minimum version a required field, so a one-field submit can no longer produce a valid task. Close #27 against #100 rather than building it.

⚠️ **[#87](https://github.com/Furiduri/PetMePhone/issues/87) comes before [#18](https://github.com/Furiduri/PetMePhone/issues/18) and [#100](https://github.com/Furiduri/PetMePhone/issues/100)** even though its defects are cosmetic. #100 requires a card of fixed height across every step, and it suspects a single root cause in window gravity that also governs dismissal. Building a multi-step form on geometry known to be wrong means debugging both at once.

⚠️ **[#98](https://github.com/Furiduri/PetMePhone/issues/98) moved here from M8, and that is deliberate.** It changes what a task *is* — behavior, minimum, anchor, identity. Every task created before it lands is a task without a minimum, and a minimum backfilled later can only be guessed. The schema change is cheapest now, while there is almost no data.

⚠️ **[#91](https://github.com/Furiduri/PetMePhone/issues/91) and [#92](https://github.com/Furiduri/PetMePhone/issues/92) moved earlier from M7, for two reasons.** Every slice from here adds `BalanceConfig` fields — [#97](https://github.com/Furiduri/PetMePhone/issues/97) adds five, [#102](https://github.com/Furiduri/PetMePhone/issues/102) adds four, [#32](https://github.com/Furiduri/PetMePhone/issues/32) and [#101](https://github.com/Furiduri/PetMePhone/issues/101) one each. A field born before the store exists is another `@Provides` constant to migrate later. And Hunger is already live on the device with no way to tune it except editing a constant and rebuilding, which is exactly the loop the panel removes.

> [#92](https://github.com/Furiduri/PetMePhone/issues/92) ships here with its source-set gate, which is the real protection. Its **CI artifact check** needs a shrunk release build and therefore [#4](https://github.com/Furiduri/PetMePhone/issues/4), so that one criterion completes in slice 5. Track it as an open box on #92 rather than delaying the panel.

## Slice 4 — A pet that keeps score

| Order | Issue |
|---|---|
| 1 | [#20](https://github.com/Furiduri/PetMePhone/issues/20) RRULE engine — **starts with the library decision gate** |
| 2 | [#21](https://github.com/Furiduri/PetMePhone/issues/21) Occurrence generation |
| 3 | [#24](https://github.com/Furiduri/PetMePhone/issues/24) Carry-over and make-up flags |
| 4 | [#95](https://github.com/Furiduri/PetMePhone/issues/95) Happiness measures follow-through |
| 5 | [#25](https://github.com/Furiduri/PetMePhone/issues/25) Happiness |
| 6 | [#34](https://github.com/Furiduri/PetMePhone/issues/34) Day close and frozen snapshots |
| 7 | [#28](https://github.com/Furiduri/PetMePhone/issues/28) Today's checklist in the overlay |
| 8 | [#99](https://github.com/Furiduri/PetMePhone/issues/99) Rows that read as doable |

**Verify:** define a recurring task, move the device date forward, and confirm the occurrence appears and yesterday's score froze. Then complete a habit from the checklist and confirm the pet reacts in the row, while opening and browsing the menu changes nothing at all.

[#34](https://github.com/Furiduri/PetMePhone/issues/34) supersedes the separate Happiness snapshot table described in [#25](https://github.com/Furiduri/PetMePhone/issues/25). Build [#25](https://github.com/Furiduri/PetMePhone/issues/25) knowing that table is going away.

⚠️ **[#95](https://github.com/Furiduri/PetMePhone/issues/95) lands before [#25](https://github.com/Furiduri/PetMePhone/issues/25), not after.** It removes the daily goal from Happiness's denominator, which reverses two of #25's conclusions: `pointsPossible` becomes the real planned total, and the empty day needs a sealed absent case rather than the floor that made it impossible. Building #25 as written means rewriting it.

⚠️ **[#34](https://github.com/Furiduri/PetMePhone/issues/34) needs three columns nothing reads yet.** The effective goal ([#97](https://github.com/Furiduri/PetMePhone/issues/97)), the retro-logged completion proportion ([#96](https://github.com/Furiduri/PetMePhone/issues/96)), and the claimed-rest flag ([#102](https://github.com/Furiduri/PetMePhone/issues/102)) all arrive in slice 8. **Add them here anyway.** All three are facts about a closed day, and a column added later can only capture data from that day forward — the same one-way problem as `titleSnapshot`, and the same instruction.

⚠️ **[#99](https://github.com/Furiduri/PetMePhone/issues/99) lands with [#28](https://github.com/Furiduri/PetMePhone/issues/28), not later.** It changes what a row displays (the minimum, not the behavior) and which row leads. Shipping #28 first and amending it means writing the row renderer twice, and #98's minimum field is already available from slice 3.

## Slice 5 — A pet that watches your screen time

| Order | Issue |
|---|---|
| 1 | [#4](https://github.com/Furiduri/PetMePhone/issues/4) Build hardening — **before the ledger exists** |
| 2 | [#31](https://github.com/Furiduri/PetMePhone/issues/31) Usage access permission flow |
| 3 | [#19](https://github.com/Furiduri/PetMePhone/issues/19) Energy ledger schema |
| 4 | [#30](https://github.com/Furiduri/PetMePhone/issues/30) Ledger from `UsageStatsManager` |
| 5 | [#32](https://github.com/Furiduri/PetMePhone/issues/32) Energy scoring |
| 6 | [#35](https://github.com/Furiduri/PetMePhone/issues/35) Unified metrics repository |
| 7 | [#93](https://github.com/Furiduri/PetMePhone/issues/93) Balance snapshot export |

**Hardening comes first for one specific reason:** `android:allowBackup="false"` is what keeps the screen-time ledger off Google's backup servers. Building the most sensitive data in the app before that flag exists means shipping a window in which it leaks.

**Verify:** deny usage access and confirm Energy reads as unavailable and the pet does **not** look exhausted. Then grant it, use the phone, and watch Energy fall.

[#4](https://github.com/Furiduri/PetMePhone/issues/4) also completes [#92](https://github.com/Furiduri/PetMePhone/issues/92)'s outstanding CI criterion, since the artifact check needs a shrunk release build to inspect. [#93](https://github.com/Furiduri/PetMePhone/issues/93) follows the panel it exports from, and [#32](https://github.com/Furiduri/PetMePhone/issues/32)'s `maxGapCredit` should enter [#91](https://github.com/Furiduri/PetMePhone/issues/91)'s store as it is written rather than after.

## Slice 6 — A pet that reacts

| Order | Issue |
|---|---|
| 1 | [#38](https://github.com/Furiduri/PetMePhone/issues/38) Reactive animation layer |
| 2 | [#70](https://github.com/Furiduri/PetMePhone/issues/70) Sprite bindings as data |
| 3 | [#101](https://github.com/Furiduri/PetMePhone/issues/101) Completing a habit unlocks its animation |

**Verify:** complete a habit and watch the celebration, then long-press the pet and browse what today's completions unlocked. Cross midnight without completing it and confirm the animation is gone from the browse set.

By this point the state machine ([#37](https://github.com/Furiduri/PetMePhone/issues/37)) has every input it needs: `DRAGGING` from slice 2, `HUNGRY` from slice 3, `HAPPY` from slice 4's checklist, and `SLEEPING` from slice 5.

[#70](https://github.com/Furiduri/PetMePhone/issues/70) comes before [#101](https://github.com/Furiduri/PetMePhone/issues/101) because #101 extends its binding model with an activity axis. #101 also resolves a collision in #70: it specifies tap to cycle animations, but tap already opens the quick menu in shipped code, so browsing moves to a long press.

## Slice 7 — The full-screen app

| Order | Issue |
|---|---|
| 1 | [#41](https://github.com/Furiduri/PetMePhone/issues/41) Task deletion with soft delete — **first, because [#40](https://github.com/Furiduri/PetMePhone/issues/40) depends on it** |
| 2 | [#40](https://github.com/Furiduri/PetMePhone/issues/40) Shell, navigation and task list |
| 3 | [#42](https://github.com/Furiduri/PetMePhone/issues/42) Create and edit |
| 4 | [#43](https://github.com/Furiduri/PetMePhone/issues/43) RRULE configuration UI |
| 5 | [#44](https://github.com/Furiduri/PetMePhone/issues/44) Settings |
| 6 | [#45](https://github.com/Furiduri/PetMePhone/issues/45) Statistics |
| 7 | [#46](https://github.com/Furiduri/PetMePhone/issues/46) Activity journal |

**Verify:** create a recurring habit from the full app and see its occurrence appear in the overlay's checklist. Delete a task that has been carrying for days and watch it stop. Open the journal and read a past day with the titles as they were written then, not as they are now.

Deletion goes first because it introduces the soft-delete column every other read path must respect, and because [#41](https://github.com/Furiduri/PetMePhone/issues/41) corrects the `ON DELETE CASCADE` specified in the schema issue.

⚠️ **[#42](https://github.com/Furiduri/PetMePhone/issues/42) requires `titleSnapshot`** on `TaskOccurrence`, which is a change to the schema from slice 3. It is one-way: a snapshot added later can only capture titles from that day forward. Add the column in slice 3 even though nothing reads it until here.

[#42](https://github.com/Furiduri/PetMePhone/issues/42) shares [#100](https://github.com/Furiduri/PetMePhone/issues/100)'s form component and its validation, so quick entry here is two required fields rather than one. [#44](https://github.com/Furiduri/PetMePhone/issues/44) persists its quality tier and pet size through [#91](https://github.com/Furiduri/PetMePhone/issues/91)'s store rather than adding its own layer.

## Slice 8 — Habits that stick

The behavioural core, built on a full app that can host it.

| Order | Issue |
|---|---|
| 1 | [#96](https://github.com/Furiduri/PetMePhone/issues/96) The guided walk through the day |
| 2 | [#94](https://github.com/Furiduri/PetMePhone/issues/94) User-raisable daily goal |
| 3 | [#97](https://github.com/Furiduri/PetMePhone/issues/97) Goldilocks ramp, with auto as the default |
| 4 | [#102](https://github.com/Furiduri/PetMePhone/issues/102) Never miss twice, heat map, and rest days |
| 5 | [#104](https://github.com/Furiduri/PetMePhone/issues/104) Enjoyment and mastery ratings |
| 6 | [#103](https://github.com/Furiduri/PetMePhone/issues/103) The app produces a record, never an assessment |

**Verify:** run the guided walk on a blank evening and end with a planned tomorrow. Miss a habit once and confirm nothing happens anywhere. Miss it twice and watch the pet react. Then alternate miss and completion for a fortnight and confirm the pet never reacts and no number counts down.

[#94](https://github.com/Furiduri/PetMePhone/issues/94) and [#97](https://github.com/Furiduri/PetMePhone/issues/97) land together. #97 amends #94: the shipped default stops being the floor and the ramp's current value takes that role, while #94's rule that a user may never type a lower number survives intact.

[#96](https://github.com/Furiduri/PetMePhone/issues/96) comes first because [#97](https://github.com/Furiduri/PetMePhone/issues/97) takes its cold-start value from the walk's count, and because [#104](https://github.com/Furiduri/PetMePhone/issues/104) collects its ratings in the walk's evening mode rather than on a new surface.

> [#103](https://github.com/Furiduri/PetMePhone/issues/103) ships here for its **copy discipline and its automated string check**, which is the half that decays silently and should exist while this slice writes most of the app's user-facing text. Its **export criteria** complete alongside [#49](https://github.com/Furiduri/PetMePhone/issues/49) in slice 9.

## Slice 9 — Resilience and data

| Order | Issue |
|---|---|
| 1 | [#47](https://github.com/Furiduri/PetMePhone/issues/47) Pet presence and recovery |
| 2 | [#48](https://github.com/Furiduri/PetMePhone/issues/48) Energy rest reminder |
| 3 | [#49](https://github.com/Furiduri/PetMePhone/issues/49) Data export |
| 4 | [#74](https://github.com/Furiduri/PetMePhone/issues/74) Remove `fallbackToDestructiveMigration` |
| 5 | [#50](https://github.com/Furiduri/PetMePhone/issues/50) Purge |

**Verify:** reboot the phone and confirm the pet does **not** reappear on its own — the app offers to bring it back when you next open it. Export a date range, open the file, and confirm a hard day, a claimed rest day, a day you never opened the app and a day the service was down all read as four different things.

Export must precede purge. An offline app with no account has no other copy of this data.

[#74](https://github.com/Furiduri/PetMePhone/issues/74) sits before [#50](https://github.com/Furiduri/PetMePhone/issues/50) and before any public release: a destructive-migration fallback that silently drops the database is the opposite of what an app with no backup can afford.

**Any time from slice 3 onward:** [#5](https://github.com/Furiduri/PetMePhone/issues/5) architecture tests and [#8](https://github.com/Furiduri/PetMePhone/issues/8) ADRs. Neither blocks anything, and both rot if left to the end.

---

## Rules that cut across every slice

Each was decided once and applies everywhere. They are listed here because they are easy to violate locally inside a slice that looks unrelated.

| Rule | Where it came from |
|---|---|
| **Absence never renders as zero.** Model it in the type | `EnergyReading.Unavailable`, `DayCoverage.NO_DATA`, `MetricsState.Loading`, the purge tombstone, and [#95](https://github.com/Furiduri/PetMePhone/issues/95)'s empty day |
| **The system never blames the user for something they did not do** | The exhausted pet, skipped days as failure, holidays wrecking averages, a 3am notification |
| **A closed day's figure is frozen and never recomputed.** Rebalancing is not a bug fix | [#29](https://github.com/Furiduri/PetMePhone/issues/29), [#34](https://github.com/Furiduri/PetMePhone/issues/34) |
| **The foreground service holds no state** | The architecture decision behind the whole overlay |
| **Balance values are injected, never literals** | [#29](https://github.com/Furiduri/PetMePhone/issues/29) |
| **A stored configuration value is a nullable override; absence means the shipped default** | [#91](https://github.com/Furiduri/PetMePhone/issues/91), [#44](https://github.com/Furiduri/PetMePhone/issues/44)'s quality tier, [#97](https://github.com/Furiduri/PetMePhone/issues/97)'s auto |
| **Permission grants are queried live, never cached** | [#11](https://github.com/Furiduri/PetMePhone/issues/11), [#31](https://github.com/Furiduri/PetMePhone/issues/31) |
| **No notification is designed to pull someone back in** | [#45](https://github.com/Furiduri/PetMePhone/issues/45), [#47](https://github.com/Furiduri/PetMePhone/issues/47), [#48](https://github.com/Furiduri/PetMePhone/issues/48), [#101](https://github.com/Furiduri/PetMePhone/issues/101) |
| **Viewing moves nothing. Only completion moves state** | [#99](https://github.com/Furiduri/PetMePhone/issues/99), guarded by a state-invariance test rather than by convention |
| **The app produces a record, never an assessment.** No user-facing string names a disorder or applies clinical language to the app's function | [#103](https://github.com/Furiduri/PetMePhone/issues/103), guarded by an automated string check |
| **A qualification is never dropped.** Retro-logged, minimum-versus-full, unrated-versus-zero, effective goal, rest day | [#96](https://github.com/Furiduri/PetMePhone/issues/96), [#98](https://github.com/Furiduri/PetMePhone/issues/98), [#102](https://github.com/Furiduri/PetMePhone/issues/102), [#103](https://github.com/Furiduri/PetMePhone/issues/103), [#104](https://github.com/Furiduri/PetMePhone/issues/104) |

## Open design question

**Is `ON_VACATION` the same thing as a claimed rest day?**

[#34](https://github.com/Furiduri/PetMePhone/issues/34) permits marking a closed day `ON_VACATION`, altering no number. [#102](https://github.com/Furiduri/PetMePhone/issues/102) introduces claimed rest days, which may only be claimed during their own day and never on a closed one — so the two do not conflict, but they may be the same mechanism described twice.

Decide before slice 4 writes the snapshot schema, since the answer determines whether that is one column or two. Cheap now, expensive after both ship.

## Checklist before starting any slice

- [ ] Every blocking question in the slice is answered, not assumed
- [ ] The previous slice still installs and runs
- [ ] Nothing in this slice renders an unknown value as zero
- [ ] Nothing in this slice lets viewing change state
- [ ] Any decision made mid-slice that contradicts an issue is written back to that issue

## Next step

Finish slice 3. [#82](https://github.com/Furiduri/PetMePhone/issues/82) first — everything from [#18](https://github.com/Furiduri/PetMePhone/issues/18) through [#100](https://github.com/Furiduri/PetMePhone/issues/100) rests on an answer that currently covers one device.
