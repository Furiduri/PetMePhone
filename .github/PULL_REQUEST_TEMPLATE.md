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

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes locally
- [ ] Manually verified on a device or emulator, where the change is user-visible

## Checklist

- [ ] Linked an issue above
- [ ] Exactly one `type:*` label
- [ ] Conventional commit messages
- [ ] No `Co-Authored-By` trailers
- [ ] Any decision that contradicts the linked issue is written back to it
