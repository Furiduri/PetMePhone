# ADR-0003: Development workflow

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** [ADR-0001](0001-development-workflow.md)
- **Issue:** [#160](https://github.com/Furiduri/PetMePhone/issues/160)
- **Related:** [ADR-0002](0002-adr-process.md)

## Context

[ADR-0001](0001-development-workflow.md) wrote down the working method this
project already followed. Everything it decided still holds except one rule,
and the reasoning is restated in full below rather than left as a delta,
because a superseding ADR that carries only the difference forces every future
reader to assemble the decision from two documents.

ADR-0001 stated, without exception:

> **Every activity, present or future, exists as an issue in
> `Furiduri/PetMePhone` before it is worked on.**

That rule is stricter than this repository's own enforced policy.
`.github/workflows/pr-checks.yml` has exempted `type:chore` from the
linked-issue requirement since
[#53](https://github.com/Furiduri/PetMePhone/issues/53), and its reasoning is
written into the workflow:

> Some changes have no issue to close and never will: archiving a completed
> change, a formatting pass, a dependency bump. Demanding a link there produces
> issues written only to satisfy a check, which is worse than no link — it
> fills the tracker with entries nobody planned.

So the document describing the workflow contradicted the tooling enforcing it.
That is the worst of both: the first time someone follows the tool instead of
the document, the document stops being believed, and nothing records which one
was meant to win.

The friction is concrete rather than theoretical. Adding a one-line HTML
comment to `.github/PULL_REQUEST_TEMPLATE.md`, so the author checklist is
deleted instead of published, is maintenance with no effect on the application.
Under ADR-0001 it required an issue first — an issue that would exist only to
satisfy the rule, which is exactly what #53 argued against.

It is also the second time ADR-0001 has been superseded in two days. The first
was [ADR-0002](0002-adr-process.md), for a stale item it could not correct.
Both have the same root cause: ADR-0001 was written in absolutes, without
checking the tooling for exceptions that had already been reasoned and already
shipped.

### What the external evidence does and does not say

Recorded here so it is not rediscovered later.

**No external authority establishes a "chore needs no issue" practice.**
[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)
does not mention issue tracking at all, and `chore` is not part of the
specification — it comes from the Angular convention by way of commitlint, and
the specification states that such additional types are not mandated and carry
no implicit effect. **The exemption below is a local convention of this
repository, not an industry standard.**

**GitHub's own guidance is conditional.**
[GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)
says to link the issue *if your pull request addresses an issue*, and the
[pull request best practices](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/best-practices-for-pull-requests)
present linking as beneficial for traceability where a relevant issue exists,
not as a universal requirement.

**The opposite practice exists and is legitimate.** GitHub's material on
[end-to-end traceability](https://github.blog/enterprise-software/governance-and-compliance/demonstrating-end-to-end-traceability-with-pull-requests/)
exists because regulated and compliance-driven contexts do tie every change to
a tracked item. This project is not one of those. That is a choice with a cost,
not a free win, and the cost is stated under Consequences.

## Decision

Every change to code follows the stages below.

### Traceability, with one exemption

**Every change is traceable to an issue, except maintenance.**

A change labelled `type:chore` may proceed without one. A chore is maintenance
that does not change what the application does for a user: dependency bumps,
build and tooling configuration, CI plumbing, formatting passes, template and
scaffolding fixes. **If it changes product behaviour it is not a chore**,
whatever it is labelled — the definition is the effect on the user, and the
list is illustrative, not exhaustive.

**A chore may still have an issue, and when it does the pull request links it.**
The rule is that an issue is not *required*, not that one is forbidden and not
that it may exist only for particular reasons. A dependency bump driven by a
security advisory, a planned tooling change, a maintenance task deferred from
an earlier change — all legitimately carry issues, and enumerating the
permitted reasons would make this rule wrong the first time an unlisted one
appeared.

For every other type, the issue comes first and work that surfaces mid-change
gets its own issue rather than being absorbed into the current branch. **The
issue is the contract**: it stipulates the scope and the closing criteria, and
departing from it is what triggers an ADR under
[ADR-0002](0002-adr-process.md).

The exemption is auditable rather than silent. `type:chore` is a visible label,
so mislabelling a behaviour change to skip the requirement is visible in
review. That is the same trade `pr-checks.yml` already documents, and the same
hole; it is accepted here rather than closed.

### Preflight

No change starts while a blocking pull request is still open. The branch is
created before the first file is touched.

### One branch, one objective

The branch is cut from `master` and named for its objective in kebab-case,
prefixed by type: `feat/habit-anchor-and-frequency`, `fix/quick-menu-anchor`,
`docs/adr-process-and-workflow`. Commit messages follow Conventional Commits.

**The branch name is the objective.** If the objective cannot be stated in one
phrase, it is more than one branch.

### The four working stages

1. **Exploration.** Read the code actually involved. Nothing is assumed from
   memory, and no task is treated as done without reading the artifact.
2. **Technical proposal.** How the change will be built: which files, which
   contracts, what is touched and what is not. This is an implementation plan,
   and it lives in the issue and the pull request — not in a separate document.
3. **Task breakdown.** Ordered, each one verifiable on its own.
4. **Implementation.** Tests first, then the code.

### Tests are written first, at the level of a use case

The failing test comes before the code, and **it is run and its failure message
read**. A test that fails because of a `NullPointerException` in its own
scaffolding has not demonstrated anything about the behaviour it claims to
cover.

**A test describes a business situation, not an operation.** "The user imports
a file that is not a valid sprite sheet", "the overlay permission is revoked
while the pet is on screen", "the pet is hungry but not hungry enough to claim
the screen". Not "`parseFrameCount` returns 4".

One test per scenario. If two tests always fail together, they were one test.
The goal is a suite small enough that a failure is read rather than skimmed.

### Size budget: 800 lines

The budget is **800 lines of code written plus deleted per branch, excluding
every `.md` file**. Documentation never competes with implementation, so ADRs
are outside it by construction.

```bash
git diff --numstat master..HEAD -- . ':(exclude)*.md' | awk '{a+=$1; d+=$2} END {print a+d}'
```

**The budget is a scope signal before it is a limit.** Crossing 800 means the
branch is pursuing more than one objective. The response is to split the
branch, never to raise the number.

### Validation: the CI flow, locally

Before opening the pull request, the full CI flow runs locally — the same tasks
CI runs, not a subset:

```bash
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --rerun-tasks
./gradlew assembleRelease
```

`:core:domain:test` is listed separately because that module is plain JVM and
has no `testDebugUnitTest` task; any future plain-JVM module needs adding here.
`assembleDebugAndroidTest` compiles the instrumented sources, which
`testDebugUnitTest` never does. `assembleRelease` is its own invocation so a
dexing failure names which variant produced it.

`--rerun-tasks` is not optional. A cached `UP-TO-DATE` result has already been
mistaken for a passing run.

If validation fails and the correction fits inside the remaining budget, it is
corrected. If it does not, the failure is reported with a proposed fix and
approval is awaited before continuing.

### Pull request

Opened against `master` using `.github/PULL_REQUEST_TEMPLATE.md`: the linked
issue where one is required, exactly one `type:*` label, the table of changed
files, and a verification section stating what was actually run — never what is
intended to be run.

**The template's `Checklist` section is an author pre-flight and is deleted
before submitting.** Its items are already evidenced elsewhere — by the
`Closes #N` line, by the labels on the pull request, by the commit log and by
the verification section — and a self-certified tick is not evidence. The
verification section stays, because that one records what was run and what it
returned.

## Alternatives considered

**Leaving ADR-0001 as written and tightening `pr-checks.yml` to match it**,
removing the chore exemption from CI. Internally consistent, and it is the
stricter reading. Rejected because #53's argument still holds and was made with
more context than ADR-0001 had: issues created only to satisfy a check degrade
the tracker, and in a solo repository the tracker is the planning tool.

**Encoding a narrower exemption** — that a chore carries an issue only when it
is maintenance deferred from earlier work. Rejected because that is one reason
among several, and a rule that enumerates permitted reasons is wrong the first
time an unlisted one appears. This repository has already demonstrated what
correcting an accepted ADR costs.

**Exempting `type:docs` as well**, since a typo fix is no more issue-worthy
than a formatting pass. Rejected for now: `type:docs` covers the ADRs
themselves, which are decision records and benefit from a linked issue stating
what prompted them. If trivial documentation fixes become frequent friction,
that is its own decision.

**Treating this as a task-list mark rather than a supersession.** Not
available: the issue rule is in the Decision section, and ADR-0002's task-list
exception is explicitly confined to Pending verification items.

**Leaving the workflow undocumented**, as it was before ADR-0001. Rejected for
the same reason as then: it was followed only because one person remembered it,
and several of its rules had already been broken in exactly the way an
unwritten rule gets broken.

**Enforcing the rules with Git hooks** — branch name, commit format and budget
checked before each commit. Rejected for now: it adds dependencies and friction
to every commit, and CI can check the same things without slowing daily work.
To be reconsidered if the conventions are broken in practice.

**Enforcing the budget in CI as a failing check.** Rejected because the budget
is a signal, not a hard gate: a legitimate 850-line change exists, and a check
that must be overridden routinely teaches people to override checks.

## Consequences

**In favour:**

- The document and the tooling agree. A reader who follows either arrives at
  the same rule.
- Maintenance is no longer blocked behind an issue written to satisfy a check,
  so the tracker holds planned work rather than bookkeeping.
- The rules are findable by someone who is not the author.
- Naming the branch after a single objective makes the budget diagnostic:
  crossing it points at a specific second objective to extract.
- Use-case-level tests keep the suite small enough to read, and their names
  document the product behaviour.
- Running the real CI flow locally removes the class of failure where CI runs a
  task that was never run on the developer's machine.

**Against:**

- **Traceability is now incomplete by design.** A chore leaves no tracked item
  explaining why it happened, only a commit and a pull request. In a context
  that needed an audit trail over every change, this would be the wrong trade;
  it is made knowingly, and it is the first thing to revisit if this project's
  context changes.
- **The exemption can be abused by mislabelling.** Nothing prevents labelling a
  behaviour change `type:chore` to skip the requirement. The label is visible
  in review, which makes the abuse auditable rather than impossible.
- **"Does not change what the application does for a user" needs judgement.** A
  dependency bump that alters runtime behaviour is not obviously a chore, and
  the rule gives a criterion rather than an answer.
- **Local validation is slow.** `--rerun-tasks` across debug and release
  discards the Gradle cache deliberately, and this runs before every pull
  request.
- **Use-case-level tests are harder to write and slower to run** than unit
  tests on pure functions, and they localise a defect less precisely.
- **Nothing enforces the budget, the branch naming or the test level.** All
  three depend on discipline. This ADR makes them findable, not automatic — the
  blind spot is declared, not closed.
- Superseding a whole document to change one rule is expensive, and this is the
  second time in two days. The lesson is recorded under Pending verification
  rather than left implicit.

## Pending verification

Items are marked under the task-list rule in [ADR-0002](0002-adr-process.md).
The first is carried over from ADR-0001 unchanged.

- **[Pending]** Extract the release-APK token verification from
  `.github/workflows/ci.yml` into a script that runs identically locally and in
  CI. Today that check exists only inside the workflow file, so the local
  validation command above is not yet the complete gate.
- **[Pending]** Review this ADR's rules for remaining absolutes. Two
  supersessions of ADR-0001 in two days were both caused by a rule written
  without exception where the repository already had one. The candidates are
  the branch-naming rule, the one-objective rule and the test-level rule —
  none of which has been checked against existing tooling the way the issue
  rule was.
