# Agents and project skills

Guidance for AI agents working on this repository.

## Project skills

| Skill | Path | Triggers when |
| --- | --- | --- |
| `petmephone-workflow` | `.claude/skills/petmephone-workflow/SKILL.md` | Work on PetMePhone starts or resumes, an issue is picked up, a branch is opened, a change is validated, or a pull request is prepared |

## Rules no agent may skip

- **Never assume the state of the project from memory.** Read the artifact.
  No task is done until the file has been opened.
- **Capture the exit code from the command, not from a pipe.** `./gradlew … | tail`
  returns `tail`'s status. A `BUILD FAILED` has already been reported as a pass
  this way, in this repository.
- **A cached `UP-TO-DATE` is not evidence.** Validation runs with
  `--rerun-tasks`.
- **An accepted ADR is immutable.** It is superseded by a new one, never
  edited. The single exception is marking a task-list item.
- **One measurable objective per branch**, and the branch name says which.
- **Do not start a change while a blocking pull request is open**, and create
  the branch before touching the first file.
- **No AI attribution anywhere** — not in commit trailers, not in pull request
  bodies, not in issues or comments.
- **When the user decides against a recommendation**, record the risk once and
  build the thing completely. Do not relitigate and do not half-deliver.

## Sources of truth

- `docs/adr/README.md` — architecture decisions, their states, and the ones
  identified but not yet recorded.
- `./tools/adr-pending.sh` — follow-ups that accepted ADRs recorded and nobody
  has closed.
- [ADR-0003](docs/adr/0003-development-workflow.md) — the development workflow.
- [ADR-0002](docs/adr/0002-adr-process.md) — how decisions are recorded, and
  when one is required.
- GitHub issues in `Furiduri/PetMePhone` — scope and closing criteria.
- `openspec/` — **frozen.** Historical record of the first slices. Where a
  decision has no ADR, its reasoning may be in there; where it disagrees with
  the code, the code is right.

## Artifact language

Everything committed to this repository is written in English — code,
comments, documentation, issues, commit messages and pull request bodies —
regardless of the language of the conversation that produced it.
