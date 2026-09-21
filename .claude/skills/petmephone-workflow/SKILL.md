---
name: petmephone-workflow
description: Trigger: starting or resuming work on PetMePhone, picking up an issue, opening a branch, validating a change, or preparing a pull request. Executes the workflow decided in ADR-0003.
---

# PetMePhone workflow

This skill is **how** to execute the workflow. [ADR-0003](../../../docs/adr/0003-development-workflow.md)
is **why** each rule exists, and it is the authority. Where this file and the
ADR disagree, the ADR wins and this file is wrong — say so rather than
following it.

Never assume the state of the project from memory. Read it.

## 1. Preflight

Run all of it before touching a file:

```bash
gh pr list --repo Furiduri/PetMePhone --state open
git branch --show-current
git status --porcelain
./tools/adr-pending.sh
```

- **An open pull request blocks a new change.** Do not start a second branch.
- The branch is created before the first file is touched.
- `adr-pending.sh` lists follow-ups that accepted ADRs recorded and nobody has
  closed. It is a report and always exits 0; read it, do not act on it
  automatically. If an item overlaps the work about to start, say so.

## 2. The issue

**Every change is traceable to an issue, except maintenance.** A `type:chore`
change may proceed without one — a chore is maintenance that does not change
what the application does for a user. If it changes product behaviour it is not
a chore, whatever it is labelled.

A chore may still have an issue; when it does, the pull request links it.

For every other type the issue comes first. Work that surfaces mid-change gets
its own issue rather than being absorbed into the current branch.

Issues use the YAML forms in `.github/ISSUE_TEMPLATE/`. The form declares only
the `enhancement` label, so the `type:*` and `area:*` labels that
`pr-checks.yml` and the pull request template require must be added
deliberately.

## 3. The branch

Cut from `master`, kebab-case, prefixed by type:

```bash
git checkout master && git pull
git checkout -b <type>/<objective-in-kebab-case>
```

**The branch name is the objective.** One measurable objective per branch. If
it cannot be stated in one phrase, it is more than one branch.

## 4. The four stages

1. **Exploration** — read the code actually involved. No task is done until its
   artifact has been read.
2. **Technical proposal** — which files, which contracts, what is touched and
   what is not. It lives in the issue and the pull request, not a separate doc.
3. **Task breakdown** — ordered, each verifiable alone.
4. **Implementation** — tests first, then the code.

## 5. Tests

Write the failing test first, **run it, and read the failure message.** A test
that fails inside its own scaffolding has demonstrated nothing.

**A test describes a business situation, not an operation.** "The user imports
a file that is not a valid sprite sheet." Not "`parseFrameCount` returns 4".
One test per scenario; if two always fail together, they were one test.

## 6. Budget

```bash
git diff --numstat master..HEAD -- . ':(exclude)*.md' | awk '{a+=$1; d+=$2} END {print a+d}'
```

800 lines of code written plus deleted, excluding every `.md`. **It is a scope
signal before it is a limit** — crossing it means a second objective is in the
branch. Split the branch; never raise the number.

## 7. Validation

The full CI flow, locally, before the pull request:

```bash
./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --rerun-tasks
./gradlew assembleRelease
./tools/adr-pending.test.sh
```

**Capture the exit code from Gradle, not from a pipe.** Piping into `tail` or
`head` returns that command's status, and a `BUILD FAILED` has already been
reported as a pass this way. Redirect to a file and echo `$?`.

`--rerun-tasks` is not optional: a cached `UP-TO-DATE` has already been
mistaken for a passing run.

If validation fails and the fix fits the remaining budget, fix it. If not,
report the failure with a proposed fix and wait for approval.

## 8. The pull request

Use `.github/PULL_REQUEST_TEMPLATE.md`. Keep `Type`, `Summary`, `Changes`,
`Verification`, and reviewer notes when there are any.

- **Delete the `Checklist` section.** It is an author pre-flight, not content.
  Every item is evidenced elsewhere, and a self-ticked box is not evidence.
- **Keep `Verification`**, and state what was actually run and what it
  returned — never what is intended to be run.
- Exactly one `type:*` label.
- **No AI attribution anywhere** — no `Co-Authored-By` trailer, no generated-by
  line in the body.

## When something contradicts the issue

That is the trigger for an ADR, at whatever stage it surfaces — see
[ADR-0002](../../../docs/adr/0002-adr-process.md). An accepted ADR is never
edited; it is superseded by a new one with bidirectional links. The one
exception is marking an item in a task-list section `[Pending]`, `[Solved]` or
`[Deprecated]`, which never deletes the item or touches the decision.

## Sources of truth

- `docs/adr/README.md` — accepted decisions and the ones still unrecorded.
- `./tools/adr-pending.sh` — follow-ups accepted ADRs are still carrying.
- Issues in `Furiduri/PetMePhone` — scope and closing criteria.
- `openspec/` — frozen, historical. Where a decision has no ADR, its reasoning
  may be in there. Where it disagrees with the code, the code is right.
