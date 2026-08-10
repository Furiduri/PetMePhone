# Exploration — Slice 1 blocking questions

Scope: the two questions `docs/build-order.md` marks as gating Slice 1 — the foreground
service type for `PetOverlayService` (issue #9) and the `CommonExtension` generic arity
that the Compose convention plugin is written against (issue #2).

Phase: `sdd-explore`. No decision is made here. Question B is closed by direct evidence;
question A is narrowed but still open.

## Repository state at exploration time

- Pre-`build-logic`: a single `:app` module; issues #1/#2/#3 are not implemented.
- `gradle/libs.versions.toml`: `agp = "9.3.1"`, `kotlin = "2.2.10"`, `composeBom = "2026.02.01"`.
- **No KSP entry exists in the catalog yet** — it arrives with #2/#3, so nothing about KSP
  can be confirmed from the current repository.
- `app/build.gradle.kts`: `compileSdk` / `targetSdk` 37, `minSdk` 26 — consistent with the PRD
  and with issue #9's stated target.
- No `PetOverlayService` and no service declaration in the manifest; issue #9 blocks writing them.

---

## Question B — `CommonExtension` generic arity (issue #2) — RESOLVED

### Finding

AGP 9.0 removed the generic parameterization of `com.android.build.api.dsl.CommonExtension`
entirely. On AGP 9.3.1 it is a plain, non-generic interface.

Issue #2 anticipated a spike to discover *the new arity*. The real answer is stronger:
**there is no arity to discover.** Code written against the AGP 8.x shape does not compile.

| AGP | Shape |
|---|---|
| 8.x | `CommonExtension<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidResourcesT>` |
| 9.3.1 | `CommonExtension` — zero type parameters |

### Evidence

Verified against the bytecode of the resolved artifact, not against release notes:

```
javap -cp ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle-api/9.3.1/<hash>/gradle-api-9.3.1.jar \
  com.android.build.api.dsl.CommonExtension

public interface com.android.build.api.dsl.CommonExtension extends org.gradle.api.plugins.ExtensionAware
```

No type parameters on the declaration. Corroborated by the AGP 9.0.0 release notes, which state
the parameterization was removed as a source-level breaking change and that block methods moved
to `ApplicationExtension`, `LibraryExtension`, `DynamicFeatureExtension` and `TestExtension`.

Confirmed on the same jar:

- `CommonExtension.getBuildFeatures(): BuildFeatures` exists, and `BuildFeatures.setCompose(Boolean)`
  exists — so `buildFeatures.compose = true` remains reachable from `CommonExtension`.
- `CommonExtension.getDefaultConfig(): DefaultConfig` exists as a **property**; there is no
  `defaultConfig { }` block method on it, matching the release-notes migration.
- `ApplicationExtension` and `LibraryExtension` both extend `CommonExtension`, so one plugin
  configuring `CommonExtension` still covers the application and library modules — the reason
  issue #2 chose `CommonExtension` in the first place remains valid.

### Signature the `.compose` convention plugin should use

```kotlin
import com.android.build.api.dsl.CommonExtension

val commonExtension: CommonExtension = project.extensions.getByType(CommonExtension::class.java)

commonExtension.buildFeatures.compose = true
```

No star projection and no `androidComponents` fallback is needed. Configure through property
access rather than nested DSL blocks, since the block-style methods no longer live on
`CommonExtension`.

### Consequence for issue #2

Issue #2's body should be corrected: the spike is discharged, and the "assume nothing carried
over from AGP 8.x" note should become the recorded finding — `CommonExtension` is non-generic —
so the implementer does not re-run it.

---

## Question A — foreground service type for `PetOverlayService` (issue #9) — STILL OPEN

### Every foreground service type valid on API 34+, checked individually

| Type | Google's stated use case | Fits? |
|---|---|---|
| `camera` | Camera access from the background | No — no camera use |
| `connectedDevice` | Bluetooth / NFC / IR / USB / network device interaction | No |
| `dataSync` | Upload, download, backup, restore, import, export | No |
| `health` | Long-running fitness and health sensor tracking | No |
| `location` | Navigation, location sharing | No |
| `mediaPlayback` | Audio and video playback, DVR | No |
| `mediaProcessing` | Media format conversion, capped at 6h/24h | No — the cap is the wrong shape for a persistent overlay |
| `mediaProjection` | Screen capture and projection | No — the overlay draws its own window, it does not capture the screen |
| `microphone` | Voice recording and communication | No |
| `phoneCall` | Ongoing calls through `ConnectionService` | No |
| `remoteMessaging` | Cross-device message transfer | No |
| `shortService` | Quick critical work, ~3 min timeout, non-sticky | No — the overlay persists indefinitely while shown |
| `systemExempted` | System apps, Device Owner, Profile Owner, `ROLE_EMERGENCY`, Device Admin, exact-alarm holders, configured VPN | No — PetMePhone meets none of the eligibility criteria |
| `specialUse` | Valid use cases no other type covers; requires `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; reviewed manually | The only type whose stated use case plausibly fits |

**This eliminates option 2 of issue #9** ("a different foreground service type that fits").
Every concrete alternative was checked and rejected. The live decision is between:

1. `specialUse` with a written justification, for the service's whole lifetime.
3. No foreground service at all — `SYSTEM_ALERT_WINDOW` alone.
4. `specialUse`, but only while the overlay is visible, user-dismissible.

### Policy sources consulted

- developer.android.com — Foreground service types; Declare foreground services and request permissions;
  Foreground service types are required (Android 14); Changes to foreground service types (Android 15).
- support.google.com/googleplay/android-developer — Device and Network Abuse policy (answer 9888379);
  Understanding foreground service and full-screen intent requirements (answer 13392821).

Answer 13392821 states that `specialUse` may be declared in limited scenarios when the use case
meets the other characteristics required for foreground service usage, and that all foreground
service types are subject to review.

**Caveat, recorded honestly:** the exact sentence asserting that `specialUse` is *rejected when
another type applies* — which issue #9's body relies on — could not be quoted verbatim from the
fetched policy pages. The fetched text is consistent with that reading but does not state it
literally. Re-verify the live policy wording before drafting the final Play Console declaration.

### What is documented about surviving without a foreground service

- `SYSTEM_ALERT_WINDOW` plus `TYPE_APPLICATION_OVERLAY` renders a window with no foreground
  service involved. The window permission is independent of foreground service status.
- The foreground service's real job here is keeping the **hosting process** alive. A window lives
  exactly as long as its owning process; it does not survive process death.
- A process with no foreground service and no visible activity is eligible for the low-memory
  killer, and its non-exempt work is deferred under Doze.

### What cannot be settled without a device

- Whether an overlay-owning process with no foreground service is killed promptly after screen-off
  on stock AOSP.
- Doze behaviour for a process whose only asset is an overlay window — the documentation covers
  network, alarm and job deferral, not window-owning-process survival.
- OEM battery managers (MIUI, Huawei, OnePlus, Samsung "Sleeping apps") are undocumented by design
  and knowable only empirically, per device and per OEM version.

This is exactly the empirical work issue #9's own checklist calls for, and it is the deciding
factor between the three remaining options. It is not dischargeable by reading.

### Draft justification text — only if `specialUse` is chosen

> PetMePhone renders a persistent, user-visible companion overlay — a virtual pet — drawn through
> `TYPE_APPLICATION_OVERLAY` above other apps, which the user explicitly enables and can dismiss
> at any time. This foreground service exists solely to keep the overlay's hosting process alive
> and to own the window's lifecycle while the feature is active; it holds no state of its own.
> No other foreground service type describes this behaviour: the service performs no camera,
> media, data synchronisation, communication or location work. It exists to sustain a continuously
> visible UI surface the user opted into.

A draft for review, not a submission. Revisit it against the live policy wording and against
whatever the device test concludes.

### API 34 → 37 delta

Nothing found in the pages consulted indicates the `specialUse` mechanics changed between API 34
and 37. Treat this as "nothing found" rather than "confirmed unchanged" — no dedicated API 36/37
behaviour-changes page was consulted.

---

## Recorded defects

| Defect | Where | Correction |
|---|---|---|
| The `CommonExtension` arity question is attributed to issue #14 | `docs/build-order.md` ~line 35 | It belongs to issue #2; #14 is `ComposeOverlayHost` |
| The spike is described as pending | issue #2 body | Replace with the recorded finding: `CommonExtension` is non-generic on AGP 9.3.1 |

## State

| Question | Status |
|---|---|
| B — `CommonExtension` arity | Resolved, evidence-backed. Issue #2 is unblocked. |
| A — foreground service type | Narrowed from four options to three. Blocked on a device test. |

Slice 1 issues #1, #2, #3 and #6 do not depend on question A and can proceed.
Issues #11, #13, #14 and #36 sit behind it, since #13 needs the manifest declaration.
