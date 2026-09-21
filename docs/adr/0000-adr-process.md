# ADR-0000: Recording architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-20
- **Issue:** [#154](https://github.com/Furiduri/PetMePhone/issues/154)

## Context

This project makes architecture decisions whose reasoning is not recorded
anywhere a reader will find it. The code shows what was done; it never shows
what was discarded, or why.

The reasoning has drifted into two places that do not work as documentation.

The first is `openspec/`: six archived changes, three open ones and twenty spec
directories. Its per-change artifacts — `proposal.md`, `design.md`,
`verify-report.md` — were written once and never re-read. They are shaped
around a single change, so a decision recorded in one of them is invisible to
anyone who did not already know that change existed.

The second is code comments. `.github/workflows/ci.yml` carries multi-paragraph
explanations of why each Gradle task is on the line and why `assembleRelease`
is a separate step. `gradle/libs.versions.toml` explains why WorkManager is
pinned. The overlay kdocs explain why `ComposeOverlayHost` owns its own
lifecycle. Every one of those is load-bearing documentation living in a file
nobody opens to read documentation.

The concrete risk is that a decision gets re-litigated with less context than
was available when it was made. The first time the Compose overlay causes pain,
"we should have used Views" comes back — and without a written rationale, a
reader cannot tell whether that alternative was considered and rejected or
simply never occurred to anyone.

A second risk applies to the document itself. If a decision document is edited
when the decision changes, the edit destroys the only thing the code cannot
tell you: that there was a change, when it happened, and what caused it.

## Decision

We record architecture decisions as ADRs under `docs/adr/`, named
`NNNN-title-in-kebab-case.md`, numbered consecutively, and never reusing a
number.

### When an ADR is written

**An ADR is written when a decision contradicts or changes something already
stipulated in the issue being worked on.** It is written at whatever stage the
contradiction surfaces — exploration, implementation or review — not on a fixed
schedule and not once per change.

Most changes produce no ADR. An issue that is implemented as written is not a
decision record; it is the issue being executed. The document exists for the
moment the work departs from the plan, because that departure is exactly what
no future reader can reconstruct.

This is narrower than the criterion proposed in
[issue #8](https://github.com/Furiduri/PetMePhone/issues/8), which would record
any decision whose motive is not evident in the code and whose reversal is
costly. That criterion is defensible and produces a fuller archive. We reject it
because it has no natural stopping point in a solo repository: almost every
decision qualifies, the index fills with entries nobody consults, and the signal
that an ADR carries — *something changed here* — is lost. The cost of the
narrower rule is stated under Consequences.

### Immutability

**An ADR in state `Accepted` is not edited.** When the decision changes, a new
ADR is written that supersedes it.

The only modifications allowed on an accepted ADR are:

- Changing its **Status** line to record that it was superseded or became
  obsolete.
- Adding the link to the ADR that replaces it.
- Fixing typographical errors or broken links, without altering the content.

Any change of substance — the decision, its alternatives or its consequences —
requires a new ADR.

### States

| State | Meaning |
| --- | --- |
| `Proposed` | Drafted, not yet agreed. May be edited freely. |
| `Accepted` | In force. From here the content is immutable. |
| `Superseded by ADR-NNNN` | Replaced by another decision. Kept. |
| `Obsolete` | No longer applies and nothing replaces it: the problem is gone. |
| `Rejected` | Evaluated and deliberately not adopted. Kept. |

An ADR is never deleted. A rejected ADR has value: it stops someone proposing
in six months exactly what was already discarded.

### Supersession

The new ADR declares which one it replaces, and the old one links to the new
one. The link is bidirectional so the history can be walked from either end.

In the new ADR:

```markdown
- **Status:** Accepted
- **Supersedes:** ADR-0004 (link to its file)
```

In the superseded ADR, changing only that line:

```markdown
- **Status:** Superseded by ADR-0009 (link to its file)
```

### Structure

Every ADR contains, at minimum: **Context**, **Decision**, **Alternatives
considered** and **Consequences**. The sections **Verification**, **Pending
verification**, **Scope** and **Deferred decision** are optional and included
when they apply.

Writing rules:

- **One decision per ADR.** If a document decides several things, none of them
  can be superseded without dragging the others along.
- **The context contains facts, not opinions.**
- **The decision is written in the present tense and in the affirmative:**
  "we use X", not "X could be used".
- **The consequences include the negative ones.** An ADR with no declared costs
  is incomplete.
- **What was verified is distinguished from what was assumed.**

ADRs are written in English, like every other artifact in this repository.

### Index

`docs/adr/README.md` holds the table of every ADR with its state, and a section
listing decisions identified but not yet recorded. It is updated when an ADR is
added or a state changes. It is the only file in the directory edited routinely.

### Relationship to `openspec/`

`openspec/` is frozen. It stays on disk as the record of how the first slices
were built, and nothing under it is updated again. New decisions come here.

## Alternatives considered

**Editing the documents when the decision changes.** Keeps one file per topic
and avoids accumulating superseded documents. Rejected because it destroys the
history of the reasoning, which is the only reason an ADR exists. The Git
history does not substitute for it: nobody audits old commits to understand why
the project is the way it is.

**Nygard's four-header format — Status, Context, Decision, Consequences —
as stipulated in issue #8.** Fewer headers and lower activation energy, which
was that issue's explicit argument, and a real one: a format nobody wants to
open is a format that stops being used. Rejected because it makes the
alternatives section optional, and the alternatives are the part that makes an
ADR worth reading in a year. An ADR that lists only the chosen option is a
justification written afterwards, not a decision record. Issue #8 anticipated
this and patched it with an acceptance criterion requiring a rejected-alternatives
section in each of its five ADRs; making it part of the format instead means
the requirement survives past those five.

**Continuing with `openspec/` per-change artifacts.** Already in place, already
familiar, and it produces a proposal and a design document for every change.
Rejected because the artifacts are organised by change rather than by decision,
so they answer "what did that slice do" and never "why is the project like
this". The evidence is local: six archived changes, and the decisions that
actually govern the code still ended up in code comments.

**Documenting the architecture in one living document.** Comfortable to read
end to end. Rejected because a single document cannot carry per-decision states,
leaves no trace of what was discarded, and tends to describe the system instead
of explaining its decisions.

**Not documenting decisions.** Rejected: the overlay, the module boundaries and
the balance-configuration rules all rest on criteria that are not evident in the
code, and two architecture styles were already rejected during planning with
nothing recording why.

## Consequences

**In favour:**

- The history of the decisions is complete and auditable.
- A reverted decision leaves evidence that it was reverted, instead of
  disappearing.
- A rejected ADR prevents re-discussing what was already discarded.
- The states make it possible to see at a glance what is currently in force.
- Because an ADR is only written on a contradiction, the presence of one is
  itself a signal: something departed from the plan here.

**Against:**

- The directory accumulates superseded documents that no longer describe the
  current system. Without reading the state line, a reader can apply a dead
  decision.
- Writing a new ADR to change a decision is more work than editing the existing
  one, and that friction can discourage recording smaller changes.
- The index is a manual maintenance point and can fall out of date.
- **The narrow trigger will lose decisions.** A costly-to-reverse decision made
  entirely within what the issue already stipulated produces no ADR under this
  rule, and its reasoning stays in a code comment or nowhere. This is the
  deliberate cost of keeping the index short, and it is the first thing to
  revisit if a decision turns out to be missing when it is needed.

## Scope

This ADR defines how decisions are recorded and when. It does not define the
development workflow those decisions are made inside; that is
[ADR-0001](0001-development-workflow.md).
