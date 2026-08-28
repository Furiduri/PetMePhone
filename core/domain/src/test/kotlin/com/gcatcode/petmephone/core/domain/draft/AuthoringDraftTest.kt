package com.gcatcode.petmephone.core.domain.draft

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #100: a stale draft stops being offered, and stopping being offered is not being deleted. */
class AuthoringDraftTest {

    private val started = Instant.parse("2026-08-27T10:00:00Z")

    private fun draft(updatedAt: Instant = started, behavior: String = "Read one pa") = AuthoringDraft(
        kind = DraftKind.HABIT,
        rawBehavior = behavior,
        rawMinimum = "",
        anchor = null,
        step = 1,
        createdAt = started,
        updatedAt = updatedAt,
    )

    @Test
    fun `a fresh draft is offerable`() {
        val oneHourLater = started.plus(Duration.ofHours(1))

        assertTrue(draft().isOfferable(oneHourLater, Duration.ofHours(36)))
    }

    @Test
    fun `a draft past the age stops being offered`() {
        val twoDaysLater = started.plus(Duration.ofDays(2))

        assertFalse(
            "a two-week-old half-typed thought is not something the user is trying to finish",
            draft().isOfferable(twoDaysLater, Duration.ofHours(36)),
        )
    }

    @Test
    fun `the age is measured from the last edit, not from when it was started`() {
        // A draft begun last week but touched an hour ago is live. Measuring from createdAt would
        // retire a draft the user is actively working on.
        val startedLongAgo = draft(updatedAt = started.plus(Duration.ofDays(7)))
        val now = started.plus(Duration.ofDays(7)).plus(Duration.ofHours(1))

        assertTrue(startedLongAgo.isOfferable(now, Duration.ofHours(36)))
    }

    @Test
    fun `the boundary is exclusive, so a draft exactly at the age is no longer offered`() {
        val exactlyAtAge = started.plus(Duration.ofHours(36))

        assertFalse(draft().isOfferable(exactlyAtAge, Duration.ofHours(36)))
    }

    @Test
    fun `a draft of pure whitespace is not progress worth resuming`() {
        val blank = AuthoringDraft(
            kind = DraftKind.TASK,
            rawBehavior = "   ",
            rawMinimum = "",
            anchor = null,
            step = 0,
            createdAt = started,
            updatedAt = started,
        )

        assertFalse(blank.hasContent())
    }

    @Test
    fun `any typed field or a chosen cue counts as content`() {
        assertTrue(draft(behavior = "R").hasContent())
        assertTrue(
            draft(behavior = "").copy(anchor = Anchor.AtDaySegment(DaySegment.MORNING)).hasContent(),
        )
    }

    @Test
    fun `the offer age is injected config with a sane range, not a literal`() {
        val field = DraftConfig.OFFER_MAX_AGE_HOURS

        assertEquals(listOf<ConfigField<*>>(field), DraftConfig.ALL)
        assertEquals(DraftConfig.DEFAULT_OFFER_MAX_AGE_HOURS, field.shippedDefault)
        assertTrue("below an hour a draft would vanish mid-session", field.min >= 1)
        assertTrue(field.min <= field.shippedDefault && field.shippedDefault <= field.max)
        assertTrue(
            "unexpected key shape: ${field.key}",
            Regex("""config_override\.draft\.[a-z_]+""").matches(field.key),
        )
    }

    @Test
    fun `the default config duration matches its descriptor`() {
        // The same two-places-one-value trap BalanceConfig had: the data class default and the
        // descriptor are two statements of one number and must agree.
        assertEquals(
            Duration.ofHours(DraftConfig.OFFER_MAX_AGE_HOURS.shippedDefault.toLong()),
            DraftConfig().offerMaxAge,
        )
    }
}
