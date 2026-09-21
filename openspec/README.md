# Frozen — historical record

**This directory is no longer updated.** As of 2026-09-20, architecture
decisions are recorded as ADRs under [`docs/adr/`](../docs/adr/README.md), and
the development workflow is defined in
[ADR-0001](../docs/adr/0001-development-workflow.md).

Nothing here has been deleted. What it contains is the record of how the first
slices of PetMePhone were built:

- `changes/archive/` — six completed changes, each with its exploration,
  proposal, delta specs, design, tasks, verification and archive report.
- `changes/` — three changes that were never archived. They are historical
  drafts, not work in progress.
- `specs/` — twenty capability specifications, accurate as of the last change
  archived on 2026-08-27 and not maintained since.
- `config.yaml` — the spec-driven tooling configuration. **Stale.** It still
  describes a single-module app with no Hilt and no version catalog, none of
  which has been true for months.

## Reading this directory

Treat everything here as a snapshot, not as a description of the current
system. Where it disagrees with the code, the code is right. Where it disagrees
with an ADR, the ADR is right.

Two rules stated in `config.yaml` are still live project conventions and are
listed as pending ADRs in the [index](../docs/adr/README.md): absence never
renders as zero, and balance values are injected configuration rather than
literals. They survive because they are still followed, not because this file
is authoritative.

## Why it was frozen

The per-change artifacts were written once and never re-read. They are
organised by change, so a decision recorded in one is invisible to anyone who
does not already know that change existed. The reasoning that actually governs
the code had drifted into code comments instead. The full argument is in
[ADR-0002](../docs/adr/0002-adr-process.md).

These files are **not** being distilled into ADRs. Freezing is the whole plan:
the reasoning behind the early architecture stays here, and
[`docs/adr/README.md`](../docs/adr/README.md) points back at this directory for
any decision the ADRs do not explain. Rewriting six archived changes would cost
real work to relocate reasoning that is already written down and still
readable.
