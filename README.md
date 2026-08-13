# PetMePhone

[![CI](https://github.com/Furiduri/PetMePhone/actions/workflows/ci.yml/badge.svg)](https://github.com/Furiduri/PetMePhone/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-30-3DDC84?logo=android&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.3.1-02303A?logo=gradle&logoColor=white)

**A habit tracker with a pet that lives on top of your phone — not inside another app you have to remember to open.**

The pet is drawn in a system overlay window, so it floats over whatever you are doing. It gets hungry, it reacts, and it stays well because you keep your habits. And it can be **your** character: import your own sprite sheet and the thing on your screen is yours, not a mascot someone else designed.

<!-- TODO: a short screen recording of the pet floating over another app belongs here.
     It is the single most useful thing this README is missing — the entire product is
     something you understand in two seconds of video and not at all in a paragraph. -->

## The idea

Most habit apps are a list you have to remember to open, and a notification that interrupts you to say so. This one is built on the mechanics from *Atomic Habits*, and the overlay is what makes them possible rather than a gimmick.

> This section describes what the app is being built to be. [Status](#status) is the honest account of what actually runs today.

**The cue is always visible, so nothing has to interrupt you.** The first law of behaviour change is to make the cue obvious. A notification is obvious for two seconds and then it is dismissed unread; a pet sitting on your screen is obvious continuously, at zero interruption cost. No notification is ever designed to pull you back in.

**You commit to the smallest version, not the whole thing.** Every habit carries a *minimum* — the two-minute version of itself. The list shows you "put three dishes in the sink", not "clean the kitchen", because nobody postpones three dishes. Doing the minimum keeps the chain alive on a day when nothing else will.

**Missing once costs nothing.** A streak that resets to zero punishes something the research says is nearly harmless: one missed day barely dents automaticity, and the damage comes from what you do *next*. So the rule is never miss twice, there is no counter anywhere to protect, and rest days accrue like vacation for the days you genuinely could not be there.

**Looking at the app is not doing the thing.** Opening the menu, reading the list and browsing all leave every metric untouched. Only a real completion moves anything — enforced by a test, not by good intentions, because "open the app so you don't lose your streak" is how a habit tracker quietly becomes the habit.

**The target adapts to you.** The daily goal starts from what you actually did rather than a number you guessed, and it moves in both directions as your capacity changes — slowly, and never without telling you why.

## Status

Early development, built as thin vertical slices: every slice ends with something you can install and look at. See [docs/build-order.md](docs/build-order.md) for the slice-by-slice plan, the reordering decisions, and the open questions that block specific issues.

**Shipped:** the floating pet with its idle animation, dragging with edge-snap and persisted position, custom character import, the quick-menu card with live metrics, and the Hunger metric with injected balance configuration.

**In progress:** text entry inside the overlay window — the hardest unsolved problem in the project, currently under exploration in `openspec/changes/slice-3-c-a-pet-you-can-type-to`. See [Spikes](#spikes) for what has actually been measured and what has not.

## Requirements

| | |
|---|---|
| minSdk | 30 |
| compileSdk / targetSdk | 37 |
| JVM toolchain | 17 |
| Kotlin | 2.2.10 |
| AGP | 9.3.1 |

The app needs the **Display over other apps** permission (`SYSTEM_ALERT_WINDOW`) — the overlay is the product, so nothing is visible without it.

> CI runs Gradle on JDK 25 while the project's toolchain targets 17. Both are deliberate: the toolchain is what your code compiles against, and the newer JDK is only what runs the build.

## Build and run

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

If more than one device is connected, pin the target first — Gradle device tasks reach every attached device:

```bash
ANDROID_SERIAL=<serial> ./gradlew installDebug
```

While developing, granting the permission from the shell saves a trip through Settings:

```bash
adb shell appops set com.gcatcode.petmephone SYSTEM_ALERT_WINDOW allow
```

## Tests

```bash
./gradlew test
```

```bash
./gradlew connectedAndroidTest
```

Lint is part of the CI gate, so run it before pushing — skipping it is what turns a first CI run red:

```bash
./gradlew lintDebug
```

## Module layout

```
app/                    Application, MainActivity, navigation host
core/domain             Entities and use cases — pure Kotlin, no Android
core/data               Room, DataStore, repository implementations
core/designsystem       Theme, tokens, shared Compose components
feature/overlay         The floating pet window, drag, quick menu
feature/tasks           Habit creation and the task list
build-logic/            Convention plugins — one place for SDK levels and shared config
spike/ime-viability     Standalone measuring instrument, never a dependency of :app
```

Dependencies point inward: `feature` and `app` depend on `core`, never the reverse, and `core:domain` knows nothing about Android.

## Conventions

Rules that shape a lot of the code and are easy to break by accident inside a change that looks unrelated:

- **Absence never renders as zero.** A loading or empty state is distinct from a real zero. Showing `0` is a claim the app has to have earned, and it is modelled in the type rather than remembered.
- **Balance values are injected configuration, not literals.** Rebalancing the pet should be a value change, never a hunt through the source.
- **Viewing moves nothing.** Only completion changes state — guarded by a test asserting that opening and browsing leave every metric identical.
- **A closed day is frozen.** Its figures are never recomputed, and rebalancing is not a bug fix.
- **No notification is designed to pull you back in.**

Specifications live in [`openspec/`](openspec) — `specs/` holds the current behaviour, `changes/` holds work in flight.

## Spikes

`spike/ime-viability` is a standalone module that measures whether text input works inside an overlay window on a real device. Raw runs are in [ime-viability-findings.md](ime-viability-findings.md), and the separation of measured signal from interpretation lives with the change that produced each pass.

Keeping those apart is the point of the module, so the same discipline applies here:

**Measured.** A keyboard appears and text can be typed in an overlay window. Taking window focus does not pause a playing video, and focus returns cleanly to the app underneath. And the finding that shapes the most: the `ime()` inset **never arrives**. The listener attaches and fires but never reports a non-zero inset, so there is no verified way to learn the keyboard's height on this window class.

**Not settled.** How the field stays visible without a height signal. Three candidates are on the table — `softInputMode = adjust=pan`, anchoring the card to the top while focused, and manual repositioning from a measured height — and the third is blocked precisely because no such measurement exists. None has been isolated as causal, which is why a second spike pass is running rather than a strategy being picked from reasoning.

**A design response, not a finding.** The step-by-step form uses one input per card. That follows *from* the missing inset rather than being measured by the spike: a single field can be pinned where a keyboard cannot cover it, and a stacked one cannot.

All of it on one OEM skin. Until a second is covered, overlay text entry is unproven rather than solved — tracked openly rather than quietly dropped.
