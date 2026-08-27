# Exploration: debug-balance-tuning-panel

**Issue**: #92 — feat(debug): balance tuning panel compiled only into the debug variant
**Phase**: sdd-explore
**Status**: done — ready for proposal

## Current State

### Build setup

`AndroidApplicationConventionPlugin.kt` declares no product flavors and no custom build types. AGP's
implicit `debug`/`release` split is the whole variant surface, which is exactly the mechanism this
issue wants and forbids extending.

`app/src/debug/` **already exists**. `app/src/debug/AndroidManifest.xml` overrides `PetOverlayService`'s
`exported` flag for adb-driven manual testing, using `tools:node="merge"` / `tools:replace`. That is
direct, proven prior art for the manifest-merge mechanics the launcher activity needs.

No module has debug-only Kotlin source yet. The only other debug-scoped artifact is
`debugImplementation(libs.androidx.compose.ui.test.manifest)` in `:core:designsystem` — a dependency
precedent, not a source-set-code one. A debug Compose panel would be the first debug source code here.

A `debug` source set inherits everything `main` gets from the module's own dependencies, plus anything
`debugImplementation`. Nothing anywhere is scoped `releaseImplementation`, so there is no asymmetry.

### Hilt wiring — the crux, and it resolves cleanly

`PetMePhoneApplication` is the single `@HiltAndroidApp` root at `:app`. Hilt's Gradle plugin runs KSP
per variant, and AGP merges `main` + `debug` sources before compiling the debug variant.

Consequence: an `@AndroidEntryPoint` activity in `app/src/debug/kotlin/...` is a normal member of the
debug variant's Hilt graph with **zero shared-code changes**. `ConfigOverrideStore` and the config
sources are already bound in `:core:data`'s `BindingsModule` and on `:app`'s classpath.

This was the fact most likely to invalidate the whole design. It does not.

### Debug manifest route

The main manifest declares `MainActivity` with `MAIN`/`LAUNCHER`. Manifest merging is additive for new
elements, so a second `<activity>` with its own `MAIN`/`LAUNCHER` filter in the debug manifest produces
a second launcher icon on debug installs and is absent from release, which never reads that file.

### Compose and design system

`:app` already depends on `:core:designsystem` and applies the compose convention plugin. All of it is
visible from `app/src/debug` for free.

### CI — a real pipeline change

`.github/workflows/ci.yml` runs `assembleDebug testDebugUnitTest :core:domain:test
assembleDebugAndroidTest lintDebug`. It **deliberately never runs `assembleRelease`**, and the file
says so: there is no signing configuration.

This issue's build-verification criterion requires adding `assembleRelease` **for the first time**.
AGP will produce an unsigned release APK fine, so this works without a signing config, but it changes
what CI builds and should be an explicit decision rather than an assumption.

Tooling is already sufficient: `build-tools;36.0.0` is installed, and the APK is a zip, so
`unzip -p app-release.apk 'classes*.dex' | strings | grep -c "<PanelFqcn>"` expecting `0` is a plain
shell step. Simpler and more robust than a disassembler.

### Overlay service lifecycle

`PetOverlayService` has no restart method, but `onCreate`/`onStartCommand` are idempotent and
`onDestroy` cleans up fully. A restart control is `stopService` then `startService` with the same
`Intent` shape `MainActivity.startOverlayService()` already uses. No new machinery.

## The two facts confirmed in code

### 1. Only two configs are reachable

`BalanceConfig.ALL` has 5 entries; `version` is deliberately not overridable and absent from it.
`PetAnimationConfig.ALL` has 3. `PetStateConfig` and `CharacterLibraryConfig` have no companion, no
descriptors, no registry — confirmed by grep, not assumed.

**The panel can enumerate 8 fields today.** This issue's criterion "every field of `BalanceConfig` is
present" is fully satisfiable. But the four snapshot configs cannot be shown at all without a
follow-up slice extending the store, and the issue's broader framing ("balance numbers", "every
editable field") reads wider than what is actually buildable.

### 2. Animation rows can never be stale, structurally

`ConfigResolution.staleFromOf` returns `null` immediately when `group.currentVersion == null`, before
any comparison. `PetAnimationConfig.GROUP` has `currentVersion = null`.

So animation overrides are never stale — by construction, not by omission. `BalanceConfig.GROUP` has
`currentVersion = 1` and its rows can genuinely go stale.

For the panel this means **three row states, not two**: stale, fresh, and *staleness does not apply to
this group*. Rendering the third as "not stale" would report a check that never ran.

## Affected areas

- **New, entirely under `app/src/debug/`**: launcher activity (`@AndroidEntryPoint`), Compose panel,
  a pure row-state function, and the manifest declaration.
- **New tests**: JVM coverage for row-state computation across absent, present, stale, not-applicable
  and rejected-write cases.
- **Modified**: `.github/workflows/ci.yml` — add `assembleRelease` plus the artifact-inspection step.
- **Must stay untouched**: everything in `app/src/main`, `:core:domain`, `:core:data`,
  `:feature:overlay` main sources. The panel is a pure consumer of already-public API.

## Approaches

| # | Approach | Verdict |
| --- | --- | --- |
| A | Enumerate `BalanceConfig.ALL` + `PetAnimationConfig.ALL` directly, no new abstraction | **Recommended** — the only approach matching what shipped |
| B | Extend the store to the four snapshot configs first, then build over six registries | Rejected — real scope creep beyond both issues, belongs in its own slice |
| — | Row state as a pure `rowState(field, stored, group)` outside Compose | **Recommended** — same discipline as `resolve()` |
| — | Release check as a shell `unzip`/`strings` grep after `assembleRelease` | **Recommended** over a custom Gradle task |

## Risks

- **`BuildConfig.DEBUG` creeping in** as belt-and-suspenders. The issue forbids it as the mechanism,
  and adding it "for safety" is the false confidence it warns about.
- **A flavor or custom build type** to solve this. None exist today; introducing one multiplies every
  variant in the project to solve what a source set already solves.
- **Any entry point, string or resource leaking into shared or release code.** The CI artifact check
  is the only mechanical guard against a future refactor moving one file into `main`.
- **New CI shape.** `assembleRelease` is absent by design today. Adding it is a decision, not a detail.
- **Snapshot configs out of reach**, so a reader expecting every tunable value will not get it here.
- **A false "not stale"** on animation rows, implying a check that structurally never ran.

## Rough changed-line estimate (code only)

| Area | Estimate |
| --- | --- |
| Debug-source Kotlin: activity, Compose panel, resets, restart control | 200–320 |
| Pure row-state computation and its JVM tests | 80–130 |
| Debug manifest addition | 10–20 |
| CI: `assembleRelease` plus artifact inspection | 30–60 |
| **Total** | **~320–530** |

Comfortably inside the 800-line budget as a single slice, unlike #91.

## Ready for Proposal

Yes, with four open questions carried forward:

- Whether the panel visibly acknowledges the four out-of-scope configs, or simply omits them.
- The UI treatment for animation rows' stale column: a third fixed state, or omitting the column for
  that group.
- Whether adding `assembleRelease` to CI is acceptable, and whether the artifact check hard-fails the
  job. The issue's wording implies hard-fail.
- The exact class and string names to grep for, decided once package naming is chosen in design.

## Note on #91's archive

This exploration ran against `master` before #125 merged, so it observed
`openspec/changes/config-store-persisted-overrides/` unarchived. That archive is in flight in #125;
propose and design should cite `openspec/specs/config-override-store/spec.md` as the merged home.

## Maintainer decisions from the exploration question round

Three of the four open questions are settled. The fourth — the exact class and string names for the
release-artifact grep — stays with `sdd-design`, since it depends on package naming.

### Staleness renders as three states, not two

A row is stale, fresh, or **not versioned**. The third is a distinct fixed state for a group whose
`currentVersion` is null, which today means every `PetAnimationConfig` row.

Rendering that as "not stale" would report the result of a check that structurally never ran. The
panel is an instrument for judging numbers; an instrument that reports a passed check it did not
perform is worse than one that reports nothing.

### CI gains `assembleRelease`, and the artifact check hard-fails

The pipeline deliberately never built a release variant, because no signing configuration exists. AGP
produces an unsigned release APK without one, so this is buildable today.

The artifact-inspection step fails the job on any match. Issue #92 calls this "the criterion that
actually protects the release", and a check that reports without blocking gets ignored within weeks.
It is the only guard against a future refactor moving one panel file into shared code.

### The panel does not mention the configs it cannot show

Only the eight fields of `BalanceConfig` and `PetAnimationConfig` appear. The four snapshot configs
are absent, with no empty state, note, or placeholder explaining their absence.

**Recorded as a deliberate choice against the recommendation made at the time.** The argument for
naming them was that a maintainer instrument should not mislead about its own coverage, and that
someone looking for `minimumDwellMillis` will not find it and will not learn why. The maintainer
weighed that and chose the quieter panel. If the absence later causes confusion, this is the decision
to revisit, and the follow-up slice that routes the remaining four configs removes the question
entirely.
