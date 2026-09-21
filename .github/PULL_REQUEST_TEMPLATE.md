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

- [ ] Linked an issue above
- [ ] Exactly one `type:*` label
- [ ] Conventional commit messages
- [ ] No `Co-Authored-By` trailers
- [ ] Any decision that contradicts the linked issue is recorded as an ADR under `docs/adr/` ([ADR-0000](../docs/adr/0000-adr-process.md))
- [ ] One objective per branch, and the branch name says which ([ADR-0001](../docs/adr/0001-development-workflow.md))
