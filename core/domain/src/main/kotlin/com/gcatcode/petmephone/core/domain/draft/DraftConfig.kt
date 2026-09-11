package com.gcatcode.petmephone.core.domain.draft

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigGroup
import java.time.Duration

/**
 * How long a draft keeps being offered back (#100: "a draft older than an **injected** age stops
 * surfacing at all").
 *
 * Injected rather than a literal, like every other tuned number in this app — a value the maintainer
 * will want to feel out on a real phone must be changeable without a code hunt.
 */
data class DraftConfig(
    /**
     * Age past which a draft stops being offered. It is never deleted at this point; it simply
     * stops interrupting.
     */
    val offerMaxAge: Duration = Duration.ofHours(DEFAULT_OFFER_MAX_AGE_HOURS.toLong()),
) {
    companion object {
        /**
         * A day and a half. Long enough that a draft started last night is still there after work,
         * short enough that yesterday's abandoned thought stops asking to be finished.
         */
        const val DEFAULT_OFFER_MAX_AGE_HOURS = 36

        /** No staleness notion of its own — this is a preference, not a balance revision. */
        val GROUP = ConfigGroup(id = "draft", currentVersion = null)

        /** One hour to two weeks. Below an hour a draft would vanish mid-session. */
        val OFFER_MAX_AGE_HOURS = ConfigField.IntField(
            "config_override.draft.offer_max_age_hours",
            GROUP,
            DEFAULT_OFFER_MAX_AGE_HOURS,
            1,
            24 * 14,
        )

        /** Registry consumed by the tuning panel and the registry-invariant tests. */
        val ALL: List<ConfigField<*>> = listOf(OFFER_MAX_AGE_HOURS)
    }
}
