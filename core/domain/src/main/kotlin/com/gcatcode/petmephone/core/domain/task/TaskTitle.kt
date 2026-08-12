package com.gcatcode.petmephone.core.domain.task

/**
 * A task's title. This slice (#23) ships the model only — trim/blank/length validation and the
 * `of()` factory land with `task-creation` (#26), which replaces this file's shape per design
 * decision 4/`TaskTitle` in `design.md`'s interfaces block.
 */
@JvmInline
value class TaskTitle(val value: String)
