# Architecture decisions (ADR)

A record of PetMePhone's architecture decisions and the reasoning behind each
one.

The process is defined in [ADR-0002](0002-adr-process.md). The main rule:
**an accepted ADR is not edited, it is superseded by a new one.**

An ADR is written when a decision contradicts or changes something already
stipulated in the issue being worked on — not once per change.

## Index

| # | Decision | State | Date |
| --- | --- | --- | --- |
| [0000](0000-adr-process.md) | Recording architecture decisions | Superseded by [0002](0002-adr-process.md) | 2026-09-20 |
| [0001](0001-development-workflow.md) | Development workflow | Superseded by [0003](0003-development-workflow.md) | 2026-09-20 |
| [0002](0002-adr-process.md) | Recording architecture decisions | Accepted | 2026-09-21 |
| [0003](0003-development-workflow.md) | Development workflow | Accepted | 2026-09-21 |

## Pending

Decisions that have been identified but do not yet have an ADR. The five marked
with [#8] are scoped by that issue and will be numbered from 0003 onward.

- Compose as the overlay surface, with Views/XML and a hybrid rejected. ([#8](https://github.com/Furiduri/PetMePhone/issues/8))
- The stateless foreground service. ([#8](https://github.com/Furiduri/PetMePhone/issues/8))
- Priority-ordered pet state providers, against a hardcoded `when` block. ([#8](https://github.com/Furiduri/PetMePhone/issues/8))
- Two-layer animation: Lottie markers plus Compose. ([#8](https://github.com/Furiduri/PetMePhone/issues/8))
- Multi-module Gradle with a pure-JVM domain. ([#8](https://github.com/Furiduri/PetMePhone/issues/8))
- **Absence never renders as zero.** Loading and empty states are distinct from
  a value of zero. Decided independently three times and recorded only in
  `openspec/config.yaml`, which is now frozen.
- **Balance and tuning values are injected configuration, never literals.**
  Same origin, same problem.
- **No static analysis.** The project runs no detekt, ktlint or Spotless, and
  Android Lint warnings do not fail the build. Whether that stays true is an
  open decision, not an omission.
- **Instrumented tests are compiled in CI but never executed.** There is no
  device or emulator in the pipeline. The choice of Gradle Managed Devices, or
  of leaving them unexecuted, needs recording.
- Android 17 behaviour for the overlay: window sizes above 600dp, revocation of
  `SYSTEM_ALERT_WINDOW` mid-session, process death and memory limits.

## How to read an ADR

1. **Check the state before anything else.** A superseded ADR is kept, but it
   does not describe the current system.
2. **Look for the alternatives section.** If it is missing, it is not a
   decision record: it is a justification written afterwards.
3. **Read the negative consequences.** If there are only upsides, the document
   is incomplete.
4. **Read it whole before applying it.** Contradictions are rarely in the same
   paragraph.
5. **Distinguish what was verified from what was assumed.** The ADRs here say
   which is which when it matters.

## When a decision is not explained here

**Check `openspec/`.** It holds the spec-driven artifacts used for the first
slices — six archived changes with their proposals, delta specs and design
documents — and it is where the reasoning behind the early architecture lives.

It is frozen, not deleted, for exactly this reason: see
[its note](../../openspec/README.md). It is a historical record and may
disagree with the current code. Where it does, the code is right. Where it
disagrees with an ADR, the ADR is right.

Decisions made from 2026-09-20 onward live here.
