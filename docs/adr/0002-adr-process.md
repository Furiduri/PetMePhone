# ADR-0002: Recording architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** [ADR-0000](0000-adr-process.md)
- **Issue:** [#157](https://github.com/Furiduri/PetMePhone/issues/157)

## Context

[ADR-0000](0000-adr-process.md) established how this project records
architecture decisions. Everything it decided still holds, and the reasoning
that produced it is restated below rather than summarised, because a superseding
ADR that only carries a delta forces every future reader to reconstruct the
decision from two documents.

One rule in it turned out to be too tight, and it took four days to find out.

ADR-0000 allows exactly three modifications to an accepted ADR: the status line,
the supersession link, and typographical or broken-link fixes "without altering
the content". Anything else requires a new ADR.

[ADR-0001](0001-development-workflow.md) shipped with a factual error in its
**Pending verification** section: it asked for a CI check that fails when a
pull request has no linked issue or more than one `type:*` label. That check
has existed since [#53](https://github.com/Furiduri/PetMePhone/issues/53), in
`.github/workflows/pr-checks.yml`, and it ran green on the very pull request
that introduced the claim.

A false statement about the repository is content, not a typo. Read literally,
ADR-0000 requires superseding an entire document to strike one stale bullet
from a to-do list. That is the wrong trade, and ADR-0000 predicted it in its own
negative consequences: the friction of writing a new ADR "can discourage
recording smaller changes".

The underlying mistake is a category error. **A Pending verification list is not
part of the decision.** It is a task list attached to the decision, recording
what the decision knew it had not finished. Task lists are supposed to change as
the tasks get done. Freezing one guarantees it goes stale, and a section that is
known to be stale is a section nobody reads — which removes the only reason to
write it.

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
- **Marking an item in a task-list section**, under the rule below.

Any change of substance — the decision, its alternatives or its consequences —
requires a new ADR.

### Task-list sections

**Pending verification**, and any future optional section of the same kind, is a
task list rather than part of the decision. Its items may be marked without a
new ADR.

Each item carries one of three states:

| State | Meaning |
| --- | --- |
| `Pending` | Not done. The default; an unmarked item is `Pending`. |
| `Solved` | Done. The mark names what solved it. |
| `Deprecated` | No longer applies. The mark names why. |

The rules that keep this from becoming a loophole:

- **Marking never deletes an item** and never rewrites its text. The original
  wording stays, and the mark is appended.
- **Marking never touches** the Context, Decision, Alternatives considered or
  Consequences sections, and never adds a new item. A new task that the
  decision did not anticipate is a new issue, not a new bullet.
- A `Solved` mark names the concrete thing that solved it — a file, a pull
  request or an issue — so a reader can check the claim.

An item is marked rather than removed for the same reason a superseded ADR is
kept: the list records what the decision knew it had not finished, and erasing a
completed item erases the evidence that it was ever open.

Marks are written inline:

```markdown
- **[Solved]** Add a CI check that fails when a pull request has no linked
  issue. — `.github/workflows/pr-checks.yml`, #53.
- **[Deprecated]** Split the sprite cache per character. — the cache was
  removed entirely in #142.
```

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
were built, and nothing under it is updated again. New decisions come here, and
`docs/adr/README.md` points back at it for any decision the ADRs do not explain.

## Alternatives considered

**Amending ADR-0000 in place**, since the change is one bullet in its list of
allowed modifications. Rejected because the immutability rule is content of
ADR-0000, and editing a document to widen the rule that forbids editing it is
circular. The rule has to permit the change before the change is made, and the
only mechanism that does that is supersession.

**Declaring that Pending verification was never covered by immutability**, and
treating the ADR-0001 fix as always having been allowed. Cheaper — no new ADR at
all. Rejected because ADR-0000 did not say that, and deciding after the fact
which sections count as "content" is exactly how an immutability rule stops
meaning anything. The distinction is worth having; it is not worth having
retroactively and undocumented.

**Allowing items to be deleted once done**, rather than marked. Keeps the list
short and readable. Rejected: the item records that the decision's author
believed something was unverified. Marking it `Solved` preserves that; deleting
it makes the ADR look like it never got anything wrong, which is the same
failure as editing a decision when it changes.

**Dropping Pending verification from the format** and tracking follow-ups only
as GitHub issues, which already have states and owners that a markdown list does
not. A genuinely good option. Rejected because the value of the section is
proximity: a reader of the decision sees what that decision knew it had not
verified without leaving the document. Items can be filed as issues as well; the
section is where they are declared, not where they are managed.

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
justification written afterwards, not a decision record.

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
- A stale follow-up can now be corrected at the cost of one line, so the
  Pending verification sections have a chance of staying true.

**Against:**

- The directory accumulates superseded documents that no longer describe the
  current system. Without reading the state line, a reader can apply a dead
  decision. This ADR adds one such document immediately.
- Writing a new ADR to change a decision is more work than editing the existing
  one, and that friction can discourage recording smaller changes. **This ADR is
  the evidence**: correcting one rule cost a full restatement of the process,
  because one-decision-per-ADR leaves no room for a partial amendment.
- The index is a manual maintenance point and can fall out of date.
- **The narrow trigger will lose decisions.** A costly-to-reverse decision made
  entirely within what the issue already stipulated produces no ADR under this
  rule, and its reasoning stays in a code comment or nowhere. This is the
  deliberate cost of keeping the index short, and it is the first thing to
  revisit if a decision turns out to be missing when it is needed.
- **The task-list exception is a hole in immutability**, however narrow. It
  depends on the boundary between "task list" and "decision" holding, and
  nothing enforces that boundary but the writer's judgement. If marks start
  appearing that change what a decision means, the exception is wrong and needs
  its own supersession.

## Scope

This ADR defines how decisions are recorded and when. It does not define the
development workflow those decisions are made inside; that is
[ADR-0001](0001-development-workflow.md).
