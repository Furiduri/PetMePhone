package com.gcatcode.petmephone.core.data.time

import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Real [AppClock] implementation wrapping an injected [Clock], per design decision 2: ports live
 * in `:core:domain`, implementations in `:core:data`, following the existing `PetProfileRepository`
 * precedent. The real platform [Clock] is `@Provides`d in `DataModule`; this type is `@Binds` in
 * `BindingsModule`.
 */
class SystemAppClock @Inject constructor(
    private val clock: Clock,
) : AppClock {
    override fun now(): Instant = clock.instant()

    override fun zone(): ZoneId = clock.zone
}
