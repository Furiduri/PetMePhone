<!-- Every PR must link an issue. CI enforces this. -->

Closes #

## Type

<!-- Check exactly one, and add the matching type:* label. -->

- [ ] Bug fix — `type:bug`
- [ ] New feature — `type:feature`
- [ ] Documentation — `type:docs`
- [ ] Refactoring — `type:refactor`
- [ ] Maintenance or tooling — `type:chore`
- [ ] Breaking change — `type:breaking-change`

## Summary

<!-- One to three bullets. What changed and why. -->

## Changes

| File | Change |
|------|--------|
|      |        |

## Verification

<!-- What you actually ran or observed. Not what you intend to run. -->

<!-- This is the CI flow, run locally. Do not run a subset: `:core:domain:test`
     is a plain-JVM module with no `testDebugUnitTest`, `assembleDebugAndroidTest`
     is the only task that compiles the instrumented sources, and `--rerun-tasks`
     is there because a cached UP-TO-DATE has already been read as a pass. -->

- [ ] `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --rerun-tasks` passes locally
- [ ] `./gradlew assembleRelease` passes locally
- [ ] Manually verified on a device or emulator, where the change is user-visible
- [ ] Code budget under 800 lines — `git diff --numstat master..HEAD -- . ':(exclude)*.md' | awk '{a+=$1; d+=$2} END {print a+d}'`

## Checklist

<!-- DELETE THIS WHOLE SECTION BEFORE SUBMITTING.
     It is a pre-flight for you, not content for the pull request. Every item
     here is already evidenced elsewhere — by the Closes line, by the labels,
     by the commit log, by the Verification section above — and a self-ticked
     box is not evidence. Verification stays; this does not. (ADR-0003) -->

- [ ] Linked an issue above, or labelled `type:chore` — maintenance is exempt ([ADR-0003](../docs/adr/0003-development-workflow.md))
- [ ] Exactly one `type:*` label
- [ ] Conventional commit messages
- [ ] No `Co-Authored-By` trailers
- [ ] Any decision that contradicts the linked issue is recorded as an ADR under `docs/adr/` ([ADR-0002](../docs/adr/0002-adr-process.md))
- [ ] One objective per branch, and the branch name says which ([ADR-0003](../docs/adr/0003-development-workflow.md))
