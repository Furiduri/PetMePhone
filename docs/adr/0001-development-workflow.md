# ADR-0001: Development workflow

- **Status:** Accepted
- **Date:** 2026-09-20
- **Issue:** [#154](https://github.com/Furiduri/PetMePhone/issues/154)
- **Related:** [ADR-0000](0000-adr-process.md)

## Context

The project already follows a working method: branch from `master`, one issue
per pull request, Conventional Commits, a size budget per branch. CI enforces
the issue link, and `.github/PULL_REQUEST_TEMPLATE.md` enforces the labels and
the commit convention.

None of it is written down in this repository. The rules live in one person's
memory and in an agent configuration outside the project. A contributor cannot
find them, and neither can the author after a long enough gap.

The cost is not hypothetical. Two of the rules have already been broken in
practice:

- The validation command was approximated rather than run as CI runs it, and a
  pull request went red on its first run for a task that was never executed
  locally.
- A Gradle run reported `UP-TO-DATE` from cache and was read as a passing
  result, producing a green report for work that had not been re-executed.

A third recurring failure has no rule at all: branches that carry more than one
objective. They are harder to review, harder to revert, and the size budget was
the only thing signalling it — without anyone having written down that this is
what the budget is for.

## Decision

Every change to code — issue, feature, bug or chore — follows the stages below.

### Issue first

**Every activity, present or future, exists as an issue in
`Furiduri/PetMePhone` before it is worked on.** Work that surfaces mid-change
gets its own issue rather than being absorbed into the current branch.

The issue is the contract. It stipulates the scope and the closing criteria,
and departing from it is what triggers an ADR under [ADR-0000](0000-adr-process.md).

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
branch is pursuing more than one objective. The response is to split the branch,
never to raise the number.

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
issue, exactly one `type:*` label, the table of changed files, and a
verification section stating what was actually run — never what is intended to
be run.

## Alternatives considered

**Leaving the workflow undocumented.** It is followed today without a document,
and writing it down changes nothing about how the work happens. Rejected
because it is followed only because one person remembers it, and two of its
rules have already been broken in exactly the way an unwritten rule gets broken.

**Keeping the full spec-driven cycle from `openspec/`** — exploration,
proposal, spec, design, tasks, apply, verify, archive. It is what the six
archived changes actually ran, and it produces a durable record per change.
Rejected because the four middle artifacts were written once and never
re-consulted, while the decisions worth keeping are now captured by ADRs
instead. The cost is stated below.

**Enforcing the rules with Git hooks** — branch name, commit format and budget
checked before each commit. Rejected for now: it adds dependencies and friction
to every commit, and CI can check the same things without slowing daily work.
To be reconsidered if the conventions are broken in practice.

**Enforcing the budget in CI as a failing check.** Rejected because the budget
is a signal, not a hard gate: a legitimate 850-line change exists, and a check
that must be overridden routinely teaches people to override checks.

**Keeping the test level free.** Rejected because an unconstrained suite grows
toward whatever is easiest to write, which is the arithmetic helper, not the
use case. The repository already carries a large test count relative to its
production code, and no rule distinguishing a valuable test from a redundant
one.

## Consequences

**In favour:**

- The rules are findable by someone who is not the author.
- Naming the branch after a single objective makes the budget diagnostic:
  crossing it points at a specific second objective to extract.
- Use-case-level tests keep the suite small enough to read, and their names
  document the product behaviour.
- Running the real CI flow locally removes the class of failure where CI runs a
  task that was never run on the developer's machine.

**Against:**

- **Local validation is slow.** `--rerun-tasks` across debug and release
  discards the Gradle cache deliberately, and this runs before every pull
  request. That cost is paid on every change, including trivial ones.
- **Use-case-level tests are harder to write and slower to run** than unit
  tests on pure functions, and they localise a defect less precisely: a failing
  scenario says the behaviour is wrong, not which line is wrong.
- **Nothing enforces the budget, the branch naming or the test level.** All
  three depend on discipline, exactly like the rules that were already broken.
  This ADR makes them findable, not automatic — the blind spot is declared, not
  closed.
- **Dropping the per-change spec and design artifacts loses a record.** A change
  that produces no ADR now leaves only its issue and its pull request. If that
  proves too thin, it is a new decision and a new ADR, not an edit to this one.
- Requiring an issue for every activity adds a step before small chores.

## Pending verification

Items are marked under the task-list rule in [ADR-0002](0002-adr-process.md).

- **[Solved]** Add a CI check that fails when a pull request has no linked issue
  label or more than one `type:*` label, so the template checklist stops
  depending on the author remembering it. — It already existed when this was
  written: `.github/workflows/pr-checks.yml`, added in
  [#53](https://github.com/Furiduri/PetMePhone/issues/53). This ADR was written
  without reading that workflow; see
  [#157](https://github.com/Furiduri/PetMePhone/issues/157).
- **[Pending]** Extract the release-APK token verification from `.github/workflows/ci.yml`
  into a script that runs identically locally and in CI. Today that check
  exists only inside the workflow file, so the local validation command above
  is not yet the complete gate.
