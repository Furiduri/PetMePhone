# Build order

**Milestones group issues by concern. They are not a build order.** This document is the order to actually build in.

Working milestone by milestone would mean nothing runs on a real phone until M2 is finished — and the pet, which is the whole identity of the product, would not be animated until M4. This orders all 50 issues as thin vertical slices instead, so something works on a device early and keeps working after every slice.

## Why this exists

The milestones were planned as horizontal layers: build, then overlay, then task domain, then metrics. That is a good way to *think* about the system and a poor way to *construct* it. With horizontal layers you have nothing demonstrable until the last layer closes.

For a solo project that cost is not just technical. Seeing your own pet moving on your own screen in week one is what sustains the work long enough to reach the clean architecture. A project you cannot show anyone — including yourself — is easy to abandon.

Each slice below ends with something you can install and look at.

## Quick path

| Slice | What you get on the phone |
|---|---|
| 1 | A pet floating over your apps, breathing |
| 2 | You can drag it, it stays where you put it, and it can be *your* drawing |
| 3 | You can create a task from the overlay and watch Hunger move |
| 4 | Recurring tasks, a checklist, and Happiness |
| 5 | Screen-time tracking and all three metrics live |
| 6 | The pet reacts — celebrations, squash, particles |
| 7 | The full-screen app, statistics and the journal |
| 8 | It survives reboots, and your data can leave the device |

## Answer these before you need them

Six questions need empirical work, not discussion. Two of them change whole issues, so resolve those before writing the code that depends on them.

| Question | Blocks | When |
|---|---|---|
| [#9](https://github.com/Furiduri/PetMePhone/issues/9) Foreground service type | [#13](https://github.com/Furiduri/PetMePhone/issues/13), much of [#47](https://github.com/Furiduri/PetMePhone/issues/47) | **Before slice 1** |
| [#14](https://github.com/Furiduri/PetMePhone/issues/14) `CommonExtension` arity on AGP 9.3.1 | The convention plugin signatures | Before slice 1 |
| [#36](https://github.com/Furiduri/PetMePhone/issues/36) Frame clock with the screen off | The idle-throttling design | During slice 1 |
| [#20](https://github.com/Furiduri/PetMePhone/issues/20) `lib-recur` maintenance status | Library versus hand-rolled subset | Before slice 4 |
| [#18](https://github.com/Furiduri/PetMePhone/issues/18) IME on an overlay window | Whether [#27](https://github.com/Furiduri/PetMePhone/issues/27) is viable at all | **Before slice 3** |
| [#31](https://github.com/Furiduri/PetMePhone/issues/31) Play classification for usage access | Submission, not development | Before release |

Two further question issues are **already answered** and appear in no slice, because there is nothing left to build in them — their decisions were recorded as comments and folded into the issues they blocked:

- [#10](https://github.com/Furiduri/PetMePhone/issues/10) — Energy accumulates continuously; a raw time ledger is persisted and points are a pure function over it
- [#22](https://github.com/Furiduri/PetMePhone/issues/22) — Carry-over is unbounded with deletion as the exit; the Happiness denominator has a floor equal to the daily goal; the make-up flag never compounds

[#51](https://github.com/Furiduri/PetMePhone/issues/51) is the issue for this document and likewise has no slice.

---

## Slice 1 — A pet on screen

The first thing you can show someone. Nothing persists, nothing is tracked; a pet renders and idles.

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

**Verify:** grant the permission by hand and see a pet idling over another app. `adb shell appops set com.gcatcode.petmephone SYSTEM_ALERT_WINDOW allow` saves a trip to Settings while developing.

> [#36](https://github.com/Furiduri/PetMePhone/issues/36) is a large issue. Slice 1 needs only enough of it to draw one animated row. The rest — quality tiers, failure states, the full grid contract — can follow in slice 2.

## Slice 2 — A pet you can move, and make yours

| Order | Issue |
|---|---|
| 1 | [#37](https://github.com/Furiduri/PetMePhone/issues/37) Priority-ordered state resolution — needed as soon as there is a second state |
| 2 | [#15](https://github.com/Furiduri/PetMePhone/issues/15) Drag and edge snap |
| 3 | [#16](https://github.com/Furiduri/PetMePhone/issues/16) Position persistence |
| 4 | [#39](https://github.com/Furiduri/PetMePhone/issues/39) User character import |
| 5 | [#12](https://github.com/Furiduri/PetMePhone/issues/12) Overlay onboarding screen |
| 6 | [#7](https://github.com/Furiduri/PetMePhone/issues/7) CI pipeline |
| 7 | [#53](https://github.com/Furiduri/PetMePhone/issues/53) Issue link and type label checks |

**Verify:** drag the pet, kill the service, restart — it returns where you left it. Import your own drawing and watch it become the pet.

Character import lands here rather than late on purpose. It is the single highest-motivation feature in the project and depends only on the renderer. CI arrives now because before slice 1 there was nothing meaningful for it to run.

## Slice 3 — A pet you can feed

The first real loop, and where the app becomes something you would use.

| Order | Issue |
|---|---|
| 1 | [#29](https://github.com/Furiduri/PetMePhone/issues/29) Balance configuration |
| 2 | [#23](https://github.com/Furiduri/PetMePhone/issues/23) Task and occurrence schema |
| 3 | [#26](https://github.com/Furiduri/PetMePhone/issues/26) Task creation use case |
| 4 | [#33](https://github.com/Furiduri/PetMePhone/issues/33) Hunger |
| 5 | [#17](https://github.com/Furiduri/PetMePhone/issues/17) Quick menu card shell |
| 6 | [#18](https://github.com/Furiduri/PetMePhone/issues/18) Focusable text input and IME |
| 7 | [#27](https://github.com/Furiduri/PetMePhone/issues/27) Wire submit to task creation |

**Verify:** tap the pet, type a task, submit, watch Hunger rise. Create ten and watch it cap.

⚠️ **Correction needed in [#17](https://github.com/Furiduri/PetMePhone/issues/17).** It says the card displays all three metrics "from existing domain flows". At this point only Hunger exists. Happiness and Energy render as loading states — not zero, per the rule that absence never renders as zero. Do not fabricate values to fill the card.

⚠️ **[#33](https://github.com/Furiduri/PetMePhone/issues/33) counts recurring occurrences at 3:1, capped at 4.** No recurring tasks exist until slice 4, so that term is simply zero here. Build it anyway — retrofitting it after Hunger ships means touching the metric everything else already trusts.

⚠️ **[#18](https://github.com/Furiduri/PetMePhone/issues/18) mandates a spike first.** If in-overlay text entry proves unviable, this slice ends at the card shell and task creation moves to the full-screen app in slice 7.

## Slice 4 — A pet that keeps score

| Order | Issue |
|---|---|
| 1 | [#20](https://github.com/Furiduri/PetMePhone/issues/20) RRULE engine — **starts with the library decision gate** |
| 2 | [#21](https://github.com/Furiduri/PetMePhone/issues/21) Occurrence generation |
| 3 | [#24](https://github.com/Furiduri/PetMePhone/issues/24) Carry-over and make-up flags |
| 4 | [#25](https://github.com/Furiduri/PetMePhone/issues/25) Happiness |
| 5 | [#34](https://github.com/Furiduri/PetMePhone/issues/34) Day close and frozen snapshots |
| 6 | [#28](https://github.com/Furiduri/PetMePhone/issues/28) Today's checklist in the overlay |

**Verify:** define a recurring task, move the device date forward, and confirm the occurrence appears and yesterday's score froze.

[#34](https://github.com/Furiduri/PetMePhone/issues/34) supersedes the separate Happiness snapshot table described in [#25](https://github.com/Furiduri/PetMePhone/issues/25). Build [#25](https://github.com/Furiduri/PetMePhone/issues/25) knowing that table is going away.

## Slice 5 — A pet that watches your screen time

| Order | Issue |
|---|---|
| 1 | [#4](https://github.com/Furiduri/PetMePhone/issues/4) Build hardening — **before the ledger exists** |
| 2 | [#31](https://github.com/Furiduri/PetMePhone/issues/31) Usage access permission flow |
| 3 | [#19](https://github.com/Furiduri/PetMePhone/issues/19) Energy ledger schema |
| 4 | [#30](https://github.com/Furiduri/PetMePhone/issues/30) Ledger from `UsageStatsManager` |
| 5 | [#32](https://github.com/Furiduri/PetMePhone/issues/32) Energy scoring |
| 6 | [#35](https://github.com/Furiduri/PetMePhone/issues/35) Unified metrics repository |

**Hardening comes first for one specific reason:** `android:allowBackup="false"` is what keeps the screen-time ledger off Google's backup servers. Building the most sensitive data in the app before that flag exists means shipping a window in which it leaks.

**Verify:** deny usage access and confirm Energy reads as unavailable and the pet does **not** look exhausted. Then grant it, use the phone, and watch Energy fall.

## Slice 6 — A pet that reacts

| Order | Issue |
|---|---|
| 1 | [#38](https://github.com/Furiduri/PetMePhone/issues/38) Reactive animation layer |

Squash on tap, cross-fade between states, and particles on the high tier — all inside the pet's own bounds.

**Verify:** complete a task and watch the celebration; let Energy reach zero and watch it sleep.

By this point the state machine ([#37](https://github.com/Furiduri/PetMePhone/issues/37)) has every input it needs: `DRAGGING` from slice 2, `HUNGRY` from slice 3, `HAPPY` from slice 4's checklist, and `SLEEPING` from slice 5.

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

**Verify:** create a recurring task from the full app and see its occurrence appear in the overlay's checklist. Delete a task that has been carrying for days and watch it stop. Open the journal and read a past day with the titles as they were written then, not as they are now.

Deletion goes first because it introduces the soft-delete column every other read path must respect, and because [#41](https://github.com/Furiduri/PetMePhone/issues/41) corrects the `ON DELETE CASCADE` specified in the schema issue.

⚠️ **[#42](https://github.com/Furiduri/PetMePhone/issues/42) requires `titleSnapshot`** on `TaskOccurrence`, which is a change to the schema from slice 3. It is one-way: a snapshot added later can only capture titles from that day forward. Add the column in slice 3 even though nothing reads it until here.

## Slice 8 — Resilience and data

| Order | Issue |
|---|---|
| 1 | [#47](https://github.com/Furiduri/PetMePhone/issues/47) Pet presence and recovery |
| 2 | [#48](https://github.com/Furiduri/PetMePhone/issues/48) Energy rest reminder |
| 3 | [#49](https://github.com/Furiduri/PetMePhone/issues/49) Data export |
| 4 | [#50](https://github.com/Furiduri/PetMePhone/issues/50) Purge |

**Verify:** reboot the phone and confirm the pet does **not** reappear on its own — the app offers to bring it back when you next open it. Export, then open the resulting zip on a computer and read your own history in it.

Export must precede purge. An offline app with no account has no other copy of this data.

**Any time from slice 3 onward:** [#5](https://github.com/Furiduri/PetMePhone/issues/5) architecture tests and [#8](https://github.com/Furiduri/PetMePhone/issues/8) ADRs. Neither blocks anything, and both rot if left to the end.

---

## Rules that cut across every slice

Each was decided once and applies everywhere. They are listed here because they are easy to violate locally inside a slice that looks unrelated.

| Rule | Where it came from |
|---|---|
| **Absence never renders as zero.** Model it in the type | `EnergyReading.Unavailable`, `DayCoverage.NO_DATA`, `MetricsState.Loading`, and the purge tombstone |
| **The system never blames the user for something they did not do** | The exhausted pet, skipped days as failure, holidays wrecking averages, a 3am notification |
| **A closed day's figure is frozen and never recomputed.** Rebalancing is not a bug fix | [#29](https://github.com/Furiduri/PetMePhone/issues/29), [#34](https://github.com/Furiduri/PetMePhone/issues/34) |
| **The only permitted change to a closed day is marking it `ON_VACATION`**, and it alters no number | [#34](https://github.com/Furiduri/PetMePhone/issues/34) |
| **The foreground service holds no state** | The architecture decision behind the whole overlay |
| **Balance values are injected, never literals** | [#29](https://github.com/Furiduri/PetMePhone/issues/29) |
| **Permission grants are queried live, never cached** | [#11](https://github.com/Furiduri/PetMePhone/issues/11), [#31](https://github.com/Furiduri/PetMePhone/issues/31) |
| **No notification is designed to pull someone back in** | [#45](https://github.com/Furiduri/PetMePhone/issues/45), [#47](https://github.com/Furiduri/PetMePhone/issues/47), [#48](https://github.com/Furiduri/PetMePhone/issues/48) |

## Checklist before starting any slice

- [ ] Every blocking question in the slice is answered, not assumed
- [ ] The previous slice still installs and runs
- [ ] Nothing in this slice renders an unknown value as zero
- [ ] Any decision made mid-slice that contradicts an issue is written back to that issue

## Next step

Slice 1, issue [#1](https://github.com/Furiduri/PetMePhone/issues/1) — and resolve [#9](https://github.com/Furiduri/PetMePhone/issues/9) alongside it, since the service cannot be written without it.
