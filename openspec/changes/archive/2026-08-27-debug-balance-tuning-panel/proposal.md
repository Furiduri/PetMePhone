# Proposal: A balance tuning panel compiled only into the debug variant

Issue #92 — `feat(debug): balance tuning panel compiled only into the debug variant`.

## Intent

Balance numbers cannot be judged from a desk. Whether `dailyTaskGoal = 10` is right, whether
`hungryThresholdRatio = 0.6` makes hunger claim the screen too eagerly, whether a 150 ms idle frame
interval reads as alive or as sluggish — each is decided by living with the app on real hardware for
a few days.

Today changing any of those means editing a constant, rebuilding, reinstalling, and losing the
accumulated state that made the observation meaningful. Losing the state is the expensive part: the
question "does hunger arrive too early?" only has an answer after several days of real task history,
and the rebuild loop destroys exactly that. The loop is slow enough that rebalancing does not happen,
so the shipped defaults stay whatever they were first guessed to be.

#91 built the seam. `ConfigOverrideStore` now persists validated per-field overrides, and
`BalanceConfig` and `PetAnimationConfig` resolve through it observably. Nothing writes to it. This
change builds the writer.

**This is a maintainer's instrument, not a feature.** It exists so the shipped defaults get chosen
from evidence. No user ever sees it, and #44's exclusion of debug toggles from the settings screen is
honoured by keeping the entry point out of shared code entirely rather than by hiding it.

## Scope

### In scope

- A launcher activity and a Compose panel living **entirely** under `app/src/debug/`, joined to the
  debug Hilt graph as a normal `@AndroidEntryPoint` member with **zero shared-code changes**.
- A second `MAIN`/`LAUNCHER` activity declared in the already-existing `app/src/debug/AndroidManifest.xml`,
  producing a second, plainly-labelled icon on debug installs and nothing at all on release.
- Rows for the eight fields reachable today: `BalanceConfig.ALL` (5) and `PetAnimationConfig.ALL` (3).
- Per row: shipped default, current value, overridden-or-not, a staleness state, and a live/deferred
  label.
- Writes exclusively through `ConfigOverrideStore.set`, surfacing the typed `ConfigWriteResult.OutOfRange`
  rejection as the panel's own wording.
- Per-field reset and reset-all, both through `ConfigOverrideStore.reset` — deletion, never a rewrite.
- An overlay service restart control.
- Pure JVM tests for row-state computation across absent, present, stale, not-versioned and rejected-write
  cases.
- `.github/workflows/ci.yml`: add `assembleRelease` and an artifact-inspection step that hard-fails the
  job on any match.

### Out of scope (non-goals)

Each of these is a way this change could quietly become a different, worse change.

- **No `BuildConfig.DEBUG` runtime gate** — not as the mechanism, and not as additional safety. See the
  rule below.
- **No product flavor and no custom build type.** None exist today; adding an environment dimension
  multiplies every variant in the project to solve what a source set already solves.
- **No feature flags.** This panel tunes numbers; it does not switch behaviour on and off.
- **No data seeding or fake task generation.** Useful, and a different issue with different risks.
- **No metric override.** Setting Hunger directly rather than the values that produce it teaches
  nothing about the balance — it produces a number the balance never generated.
- **No entry point, string, or resource in shared or release code**, including a "convenience" reuse of
  a panel string from `main`.
- **No extension of the store to the four snapshot configs.** `PetStateConfig`,
  `CharacterLibraryConfig`, `OverlayPositionConfig` and `QuickMenuConfig` have no registry and are
  unreachable from any panel; #91's proposal already owes them a follow-up slice.
- **No changes to `app/src/main`, `:core:domain`, `:core:data`, or `:feature:overlay` main sources.**
  The panel is a pure consumer of already-public API.

## The rules this change is built on

### Absent from release beats disabled in release

**Rule: the gate is the source set, and there is no runtime check standing in for it or reinforcing it.**

A `if (BuildConfig.DEBUG)` check leaves every class, every string and every write path *inside* the
release APK, reachable by reflection and readable by anyone who unzips it. The panel's whole purpose is
to expose the numbers that define the game, so shipping it dormant is shipping a cheat engine with the
door merely closed. Adding the check "for extra safety" on top of the source set is worse than useless:
it reads as a second lock while the code ships anyway, which is precisely the false confidence #92
names.

The panel lives in `app/src/debug/`. The release variant does not compile it. There is nothing to
disable because there is nothing there — and the CI artifact check turns that from an intention into a
verified property of the built APK.

### Every row shows three things, not one

| Column | Why it must be visible |
| --- | --- |
| **Shipped default** | The number in code today. Without it there is no baseline, and a delta cannot be judged. |
| **Current value** | What the app is actually using right now. |
| **Overridden?** | Whether a stored entry exists. This is what separates "I set it to 10" from "it happens to be 10". |

A panel showing only the current value is close to useless for its stated job: a maintainer three days
into a session cannot tell what they changed, or what they changed it from, and therefore cannot report
a decision back into code. The three columns exist so a tuning session ends with a defensible diff.

### Staleness renders as three states, not two

A row is **stale**, **fresh**, or **not versioned**.

`ConfigResolution.staleFromOf` returns `null` immediately when `group.currentVersion == null`, before
any comparison runs. `PetAnimationConfig.GROUP` has `currentVersion = null`, so every animation row
falls in that branch — by construction, not by omission. `BalanceConfig.GROUP` has `currentVersion = 1`
and its rows can genuinely go stale.

Rendering the animation rows as "not stale" would report the result of a check that structurally never
ran. An instrument that reports a passed check it did not perform is worse than one that reports
nothing, so the third state is fixed and distinct.

### Live and deferred are labelled per row, and the label must be true

`BalanceConfig` and `PetAnimationConfig` are both **observable** as of #91, so all eight fields visible
in this panel are live today. That does not make the label decorative: the label is the panel's claim
about what just happened, and the follow-up slice that routes the snapshot configs will add deferred
rows to this same surface.

Implying a live change that did not happen is the worst outcome this panel can produce — a maintainer
concludes a value "feels wrong" when the app was never running it, and a wrong number then gets shipped
on the strength of a real-looking observation. That is why the restart control exists rather than
leaving the maintainer to kill the service by hand: they would eventually forget, and forgetting
produces exactly that false conclusion.

### Reset deletes; it never rewrites the default as a value

Both per-field reset and reset-all delete entries, returning the app to the shipped defaults exactly as
a fresh install would resolve them. #91 established why: a rewritten default looks identical in a UI and
silently pins that field forever, so a later rebalance never reaches that install. A panel that rewrites
would defeat #91 from the outside, which is a particularly bad failure because the store's own tests
would all still pass.

## Approach

**The panel enumerates the two existing registries directly** — `BalanceConfig.ALL` and
`PetAnimationConfig.ALL` — with no new abstraction between them and the UI. Both registries already
exist and their KDoc already names this panel as their consumer. Building an intermediate registry
layer would add code whose only job is to restate what `ConfigField` already carries: key, group,
shipped default, and range.

**Row state is a pure function outside Compose**, over `(field, storedOverride, group)`, mirroring the
discipline `resolve()` already follows. Every interesting case — absent, present, stale, not versioned,
out-of-range rejection — is then a JVM test with no Android and no UI on the path.

**The service restart control is `stopService` then `startService`** with the same `Intent` shape
`MainActivity.startOverlayService()` already uses. `PetOverlayService.onCreate`/`onStartCommand` are
idempotent and `onDestroy` cleans up fully, so no new lifecycle machinery is added to the service — and
adding a restart method to shared code would be exactly the leak this issue forbids.

**The release check is a shell step, not a Gradle task.** An APK is a zip, `build-tools;36.0.0` is
already installed, and an `unzip`/`strings`/`grep` over `classes*.dex` expecting zero matches is both
simpler and harder to accidentally weaken than a custom task inside the build it is auditing.

## CI gains `assembleRelease` — a real pipeline-shape change

`.github/workflows/ci.yml` today runs `assembleDebug testDebugUnitTest :core:domain:test
assembleDebugAndroidTest lintDebug`, and it **deliberately never builds a release variant** — the file
says so, because there is no signing configuration.

This change adds `assembleRelease` for the first time. AGP produces an unsigned release APK without a
signing config, so it is buildable today, but this is a decision about what the pipeline does and is
recorded here as one rather than slipped in as a detail. It lengthens every CI run and introduces the
first place a release-only build failure can surface.

The artifact-inspection step **hard-fails the job** on any match. #92 calls this "the criterion that
actually protects the release", and a check that reports without blocking is ignored within weeks. It is
the only mechanical guard against a future refactor moving one panel file into shared code — a leak that
would otherwise be invisible in review, because the diff would look like an ordinary file move.

## Maintainer decision — the panel does not mention what it cannot show

Only the eight fields of `BalanceConfig` and `PetAnimationConfig` appear. The four snapshot configs are
absent with no empty state, no note, and no placeholder explaining the absence.

**Recorded as a deliberate choice made against the recommendation at the time.** The argument for naming
them was that a maintainer instrument should not mislead about its own coverage, and that someone
looking for `minimumDwellMillis` will not find it and will not learn why. The maintainer weighed that
and chose the quieter panel.

If the absence later causes confusion, this is the decision to revisit. The follow-up slice that routes
the remaining four configs through the store removes the question entirely.

## Open design question — carried to `sdd-design`

1. **The exact class and string names the release-artifact grep targets.** The check is only as strong
   as the token it searches for: too specific and a rename silently disarms it, too generic and it
   matches unrelated code. This depends on the package naming chosen for the debug source set, so it is
   decided once in design rather than guessed here.

## Capabilities

### New capabilities

- `debug-tuning-panel`: a debug-variant-only instrument that enumerates the registered config fields,
  shows default/current/overridden/staleness/liveness per row, writes and resets exclusively through
  `ConfigOverrideStore`, restarts the overlay service, and is verifiably absent from the release
  artifact.

### Modified capabilities

- None. No shared-source capability changes behaviour; the panel is a consumer of `config-override-store`
  as already specified.

## Affected areas

| Area | Impact | Change |
| --- | --- | --- |
| `app/src/debug/kotlin/...` (new) | Added | `@AndroidEntryPoint` launcher activity, Compose panel, resets, restart control |
| `app/src/debug/kotlin/...` row state (new) | Added | Pure `(field, stored, group)` → row state function |
| `app/src/debug/AndroidManifest.xml` | Modified | Second `MAIN`/`LAUNCHER` activity beside the existing `exported` override |
| `app/src/test/...` or debug unit tests (new) | Added | JVM coverage of row state and rejection surfacing |
| `.github/workflows/ci.yml` | Modified | `assembleRelease` plus a hard-failing artifact-inspection step |
| `app/src/main`, `:core:*`, `:feature:overlay` main sources | Untouched | Deliberately — any change here is a leak |

## Risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| `BuildConfig.DEBUG` creeping in as belt-and-suspenders | Med | Named as forbidden here; the source set is the only gate |
| A convenience import from shared code dragging the panel into the release graph | Med | CI artifact check hard-fails; it is the only reliable guard |
| Scope creep into a general debug menu (seeding, flags, metric overrides) | Med | Each named as an explicit non-goal above |
| A false "not stale" on animation rows | Med | Three fixed row states; the not-versioned state is distinct |
| The new `assembleRelease` step failing for reasons unrelated to this change | Low | It builds unsigned today; failure is real signal about the release variant |
| A reader expecting every tunable value not finding the snapshot four, with no explanation | Med | Accepted deliberately; recorded above as the decision to revisit |
| The grep token chosen too narrowly and silently disarmed by a rename | Low | Flagged as the open design question for `sdd-design` |

## Review-workload forecast (code only, docs excluded)

| Area | Estimate |
| --- | --- |
| Debug-source Kotlin: activity, Compose panel, resets, restart control | 200–320 |
| Pure row-state computation and its JVM tests | 80–130 |
| Debug manifest addition | 10–20 |
| CI: `assembleRelease` plus artifact inspection | 30–60 |
| **Total** | **~320–530** |

Comfortably inside the 800-line budget as a single slice. No split is planned.

## Rollback

Delete the debug source directory and revert the two CI steps. Nothing in shared or release code was
touched, so there is nothing to unwind there. Any override entries a tuning session persisted stay in
`petmephone_prefs` and keep resolving through #91's store exactly as before — the panel added no
persistence of its own, so removing it removes a writer, not a schema.

## Dependencies

- #91's `ConfigOverrideStore`, `ConfigField`, `ConfigResolution` and the two `ALL` registries — merged.
- Honours #44 by keeping the entry point out of shared code entirely.
- Feeds the balance export issue, which turns a tuning session into shipped defaults.

## Success criteria

- [ ] The panel and its entry point exist only under `app/src/debug/`; no reference to either appears in
      shared or release sources.
- [ ] A release APK contains none of the panel's classes, strings, or resources — verified by inspecting
      the built artifact in CI, not by reading Gradle files.
- [ ] The gate is the source set; no `BuildConfig.DEBUG` check exists anywhere in this change.
- [ ] No product flavor and no custom build type is added.
- [ ] All five `BalanceConfig.ALL` fields and all three `PetAnimationConfig.ALL` fields are present,
      including those the settings screen deliberately excludes.
- [ ] Every row shows shipped default, current value, and whether it is overridden.
- [ ] An override written under an earlier balance `version` is marked stale; a `PetAnimationConfig` row
      renders the distinct **not versioned** state, never "not stale".
- [ ] Each row states whether the change is live or applies on next service start.
- [ ] A live field's change is visible on the running overlay without leaving the panel.
- [ ] The panel restarts the overlay service via `stopService`/`startService`, adding no method to
      `PetOverlayService`.
- [ ] Writes go through `ConfigOverrideStore.set`; an out-of-range value is rejected with the same typed
      reason a user-facing setting would surface.
- [ ] Per-field and global reset both delete entries rather than writing defaults as values.
- [ ] CI runs `assembleRelease` and the artifact-inspection step hard-fails the job on any match.
- [ ] The absence of the four snapshot configs is recorded as a deliberate maintainer decision, not as an
      oversight.

## Proposal question round

The three questions raised in exploration are settled and recorded above. These are offered as an
optional round before `sdd-spec` and `sdd-design`; none of them blocks progress, and answers would
sharpen the spec rather than reopen a decision.

1. **Panel labelling of its own risk.** All eight fields are live today, so every row will read "live".
   Should the panel state that liveness plainly per row anyway, or only distinguish once a deferred row
   exists to contrast with? The tradeoff is a column that currently says the same thing eight times
   versus a habit of reading rows as live that breaks silently when deferred rows arrive.
2. **What a rejected write leaves on screen.** When `set` returns `OutOfRange`, should the field revert
   to the current value, or keep the typed number visible with the reason beside it? Keeping it lets a
   maintainer correct a typo; reverting guarantees the panel never displays a value the app is not using.
3. **Reset-all confirmation.** Reset-all can end a multi-day tuning session in one tap. Is a
   confirmation step wanted, or is a maintainer instrument expected to be unguarded?
4. **A stale row's affordance.** A stale row already names the older version it was written under. Should
   the panel offer anything beyond displaying it — for example a one-tap reset of only the stale rows — or
   is display alone the full scope here?

## Maintainer decisions from the proposal question round

These three are settled. The fourth question raised in the round — a stale row's affordance — is a
presentation detail left to `sdd-design`.

### The live/deferred column is shown even though every row currently reads the same

All eight registered fields are observable today, so the column distinguishes nothing right now. It is
rendered anyway, per row.

The follow-up slice that routes the four snapshot configs makes the column meaningful without a UI
rewrite, and a maintainer who learns to read the column while it is uniform will not have to relearn
the panel when it stops being uniform. Issue #92 asks for it per row; that stands.

### A rejected write leaves the typed value on screen with its reason

When `ConfigOverrideStore.set` returns `OutOfRange`, the field keeps the value the maintainer typed and
shows the expected range beside it. It does not revert to the current value.

Reverting discards the input and leaves no evidence of what was wrong with it, so the maintainer
retypes from scratch and guesses. Keeping it makes correcting an edit rather than a re-entry, and the
store already returns `key`, `min`, `max` and the offending value as typed data, so the panel has
everything it needs to say so precisely.

### Reset-all asks for confirmation

Per-field reset does not. Reset-all does.

Reset deletes entries, so there is no undo, and reset-all can end a multi-day tuning session in one
tap. The asymmetry is deliberate: one field is a small, obvious, easily-redone act, and all of them at
once is not.

Note this differs from the reasoning in #100, where a confirmation on Cancel was refused. There, Cancel
was the only clean exit from a form and guarding it added a step to the sole escape route. Here,
reset-all is not an exit and not the only way to clear anything — per-field reset already covers the
ordinary case.
