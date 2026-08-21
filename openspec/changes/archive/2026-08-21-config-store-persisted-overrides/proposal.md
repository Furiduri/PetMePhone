# Proposal: A persisted, validated store behind the injected configuration

Issue #91 — `feat(config): a persisted, validated store behind the injected configuration`.

## Intent

Every tuned number in the app is already injected rather than read from a global. What is missing is a
**source**. Today `BalanceConfig` carries its numbers as Kotlin default parameter values and
`OverlayModule` hands fourteen `private const val` declarations to the other config classes. So the
injection seam exists and nothing can move through it: neither a user setting nor the tuning panel of
#92 has anywhere to write.

This change builds that store and nothing else. No UI ships here.

## Scope

### In scope

- A config-override store: interface in `:core:domain`, Preferences-DataStore implementation in
  `:core:data`, bound by `@Binds` in `BindingsModule.kt`, reusing the already-provided
  `petmephone_prefs` instance with a sparse key per overridable field.
- `BalanceConfig` resolved from the store and exposed **observable** — `dailyTaskGoal` feeds Hunger,
  which is on screen in the quick menu, so a change must be visible without a restart.
- `PetAnimationConfig` resolved from the store and exposed **observable** — animation pace is judged by
  eye; tuning it behind a restart means never trusting the result.
- Validation ranges declared once, in the domain, beside the field they bound.
- Version staleness: each override records the `BalanceConfig.version` it was written under.
- Pure JVM tests for the resolution matrix and instrumented tests for the persisted round trip.

### Out of scope (non-goals)

- **No UI of any kind.** No settings row, no tuning panel, no debug entry point. #92 owns that.
- **No new tunable fields.** Energy and Happiness stay deferred to the slices that give them a
  function and a test.
- **No migration of the `const val` declarations into a new constants object.** They stay where they
  are, as the shipped defaults.
- **No remote or file-based config.** Local persistence only.
- **The four snapshot configs** — `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`,
  `QuickMenuConfig` — keep their current bare providers. See the tracked deviation below.

## The rules this change is built on

Each is stated with the reason it exists, because each is easy to satisfy in appearance and violate in
fact.

| Rule | Why |
| --- | --- |
| A stored value is a **nullable override**; `null` means "use the shipped default, read fresh from code every launch" | If every field is persisted eagerly, a later change to a shipped default never reaches anyone who already installed the app. Rebalancing becomes a migration problem instead of a value change — the exact failure the injected-config rule exists to prevent. |
| **Reset deletes the entry**, never rewrites the current default as an explicit value | A rewritten default looks identical in a UI and silently pins that field forever. |
| **Absence resolves to the shipped default, never to zero** and never to a partially-zeroed config | A `dailyTaskGoal` of `0` makes Hunger divide by nothing. This is the standing project rule applied to configuration: absence is absence. |
| **Validation rejects, never clamps silently**; ranges declared once, in the domain, beside the field | A clamped write shows the user a number they did not choose. One declaration means the user setting and #92's panel enforce the same bounds through the same code path. |
| A stale override is **kept and flagged**, never discarded and never silently trusted | Discarding throws away a deliberate tuning session; trusting with no marker makes a stale number indistinguishable from a fresh one. |
| Observable vs snapshot is decided **per config**, not globally | Making everything reactive rewrites pure domain call sites for fields with no live consumer. |

**Why eager persistence is the dominant risk, and what structurally prevents it here.** It passes every
obvious test — the value round-trips, the UI shows it, reset works — and fails invisibly only when a
rebalance ships to nobody. Two structural defences, not vigilance: the store's write API takes a single
field, so there is no "save the whole config" entry point to misuse; and the repo's existing
source-walking code tests (`QuickMenuNoPersistenceCodeTest` and siblings) already encode architectural
negatives as running tests, so the absence of a whole-object write and of any zero substitution becomes
an assertion rather than a convention.

## Tracked deviation — reduced scope

Issue #91's criterion "Each config class is provided from the store, and no provider returns a bare
compile-time constant any more" is **not satisfied by this change**, deliberately.

Only `BalanceConfig` and `PetAnimationConfig` route through the store. The maintainer's reasoning:
these two are the configs with live consumers and the ones a tuning session actually judges. The
snapshot four have no second reader — an override there is read once at service start and proves
nothing a JVM test has not already proven — so including them adds line count without adding proof, in
a change already forecast near the review budget.

The follow-up slice owes: `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig` and
`QuickMenuConfig` resolved from the same store, their `OverlayModule` providers rewired, their
validation ranges declared, and documentation that each takes effect on the next service start.

The architectural seam is still exercised at this reduced scope: `PetAnimationConfig` lives in
`:feature:overlay`, which depends on `:core:data` only in test source. Routing it through a
`:core:domain` interface is what proves the seam works without a new main-source dependency — so the
follow-up slice inherits a proven path, not an untested one.

## Approach

Interface in `:core:domain`, implementation in `:core:data`, `@Binds` in `BindingsModule.kt` — the
repo's existing shape for every stateful capability (`OverlayPositionRepository`,
`ActiveCharacterRepository`, `TaskRepository`). Persistence is Preferences DataStore over the existing
`petmephone_prefs` instance, one sparse `Preferences.Key` per overridable field: "no key present" maps
directly onto the nullable-override semantics, with no encoding of absence to invent.

Resolution is a pure function in `:core:domain` over `(storedOverride, shippedDefault, range)`, so the
whole matrix — absent, present, out-of-range, stale — is testable without Android.

Observability changes providers, not call sites: the provider exposes a `StateFlow<BalanceConfig>` for
reactive consumers while pure domain functions keep taking a plain snapshot parameter. No pure
signature changes.

**Do not** add a main-source `:feature:overlay -> :core:data` dependency. That is a real architecture
change beyond this issue's framing.

## Open design questions — carried to `sdd-design`, not resolved here

1. **Store shape** — a single generic store keyed by field name, or one typed store per config class.
2. **Validation ranges** — how they are declared syntactically: a sibling declaration object versus
   inline metadata beside each field.
3. **Version staleness granularity** — stored once per override group or once per field. This changes
   the resolution function's signature, so it is a design decision, not an implementation detail.

## Capabilities

### New capabilities

- `config-override-store`: nullable per-field overrides, reset-as-delete, absence-to-default
  resolution, validation-rejects-never-clamps, and version staleness flagging.

### Modified capabilities

- `balance-configuration`: "BalanceConfig is Hilt-provided" narrows from a bare `@Provides` to
  resolution through the store; validation ranges are added beside each field; `version` becomes
  load-bearing rather than pure metadata.
- `dependency-injection`: the config providers gain a store-backed source.

## Affected areas

| Area | Impact | Change |
| --- | --- | --- |
| `core/domain/.../config/` (new) | Added | Store interface, pure resolution function, range declarations |
| `core/data/.../config/` (new) | Added | Preferences-DataStore implementation |
| `core/domain/.../balance/BalanceConfig.kt` | Modified | Ranges beside fields; `version` becomes load-bearing |
| `feature/overlay/.../ui/PetAnimationConfig.kt` | Modified | Ranges beside fields; becomes observable |
| `core/data/.../di/DataModule.kt` | Modified | `provideBalanceConfig()` routes through the store |
| `core/data/.../di/BindingsModule.kt` | Modified | `@Binds` for the store |
| `.../di/OverlayModule.kt` | Modified | `PetAnimationConfig` provider only; the other four untouched |
| `BalanceConfigInjectionTest.kt` | Modified | Bare defaults now hold only when no override is stored |
| JVM + instrumented tests | Added | Resolution matrix, reset, staleness, boundaries, round trip, corrupt store |

## Risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Eager persistence of all fields | Med | Per-field write API; structural code test forbidding whole-object writes |
| Absence resolving to zero in one path | Med | Explicit corrupt-read test; structural test against zero substitution |
| Reduced scope read later as the criterion being met | Med | Named as a deviation here with the follow-up debt written down |
| A `:feature:overlay -> :core:data` main-source dependency creeping in | Low | Named as forbidden in this proposal and enforceable structurally |
| Design resolves staleness granularity late, churning the resolution signature | Low | Flagged as open question 3 for `sdd-design` |

## Revised review-workload forecast (reduced scope, code only)

| Area | Estimate |
| --- | --- |
| Domain store interface + resolution/validation logic | 120–180 |
| Validation ranges beside two configs' fields | 25–40 |
| DataStore-backed implementation | 150–220 |
| Provider rewiring (`DataModule`, `BindingsModule`, one `OverlayModule` provider) | 40–60 |
| Structural code tests for the guard rails | 40–70 |
| Pure JVM tests (resolution matrix, reset, staleness, boundaries) | 140–200 |
| Instrumented tests (round trip, corrupt store, observable) | 80–120 |
| **Total** | **~595–890** |

Down from the exploration's ~750–1,140. The 800-line budget is now plausible in a single PR but not
guaranteed at the top of the range. If `sdd-tasks` forecasts high, the natural split is unchanged:
slice A — domain interface, ranges, resolution logic, pure tests; slice B — DataStore implementation,
provider rewiring, instrumented tests.

## Rollback

Revert the two provider bodies to their bare form; the store code becomes dead and is removed with the
same revert. Persisted keys left in `petmephone_prefs` are inert — nothing reads them once the
providers no longer resolve through the store, and absence semantics mean a re-apply resumes cleanly.
No schema, no migration, no user-visible surface is touched.

## Dependencies

- `androidx-datastore-preferences` v1.2.1 — already declared and already providing `petmephone_prefs`.
- Blocks #92 (debug tuning panel) and the user-raisable daily task goal issue in this milestone.
- Deliberately does not touch the settings screen reserved by #44.

## Success criteria

- [ ] `BalanceConfig` and `PetAnimationConfig` are provided from the store; neither provider returns a
      bare compile-time constant.
- [ ] An override is nullable per field; no entry means the shipped default is read fresh from code.
- [ ] Changing a shipped default in code reaches an existing install that never overrode that field —
      verified by a test, not by reasoning.
- [ ] The case that matters most passes: override field A, change the shipped default of field B in
      code, reload — A keeps its override and B picks up the new default.
- [ ] Reset **removes** the entry rather than writing the current default as a value.
- [ ] A failed, empty, or corrupt read resolves to the shipped defaults, never to zero and never to a
      partially-zeroed config.
- [ ] Both configs are observable; a change is visible without restarting the app or the service.
- [ ] Each override records the `version` it was written under; a stale override is flagged rather than
      discarded or silently trusted.
- [ ] An out-of-range write is rejected with a reason; it is never clamped silently.
- [ ] Validation ranges are declared once, in the domain, beside the field they bound.
- [ ] No whole-config write API exists, enforced by a structural test.
- [ ] No main-source `:feature:overlay -> :core:data` dependency is introduced.
- [ ] The reduced scope is recorded as a named tracked deviation against #91, with the follow-up debt
      written down.

## Maintainer decisions from the proposal question round

These three were open when the proposal was written and are now settled. They resolve the
version-staleness open design question; the other two remain for `sdd-design`.

### Staleness is per override group, not global

`BalanceConfig.version` is a balance revision. It stamps balance overrides only. A bump of that version
must not flag a `PetAnimationConfig` override as stale, because a balance revision says nothing about
whether 150ms still reads as alive. Each config group carries its own staleness notion, or none.

A single global stamp was rejected: it would mark a number nobody touched and nothing invalidated.

### The stored version is recorded, not just a boolean

An override records the exact version it was written under, not merely that it precedes the current one.
The write is happening anyway; the extra field is what lets #92 explain *why* a row is flagged rather
than only that it is.

### Rejection returns a typed domain reason

An out-of-range write returns a typed reason naming the field, the range and the offending value. It does
not return display copy. The store must not assume a UI exists; #92 decides the wording, and a typed
reason means nothing has to parse a string to recover the data.
