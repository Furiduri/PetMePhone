package com.gcatcode.petmephone.core.domain.habit

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's own segmentation: validated, wall-clock, and unaffected by crossing time zones. */
class DaySegmentBoundariesTest {

    private fun boundaries(dayStart: String, afternoon: String, evening: String): DaySegmentBoundaries =
        (
            DaySegmentBoundaries.of(
                LocalTime.parse(dayStart),
                LocalTime.parse(afternoon),
                LocalTime.parse(evening),
            ) as DaySegmentBoundariesResult.Valid
            ).boundaries

    @Test
    fun `the shipped segmentation is 6, 12 and 18`() {
        assertEquals(LocalTime.of(6, 0), DaySegmentBoundaries.SHIPPED.dayStart)
        assertEquals(LocalTime.of(12, 0), DaySegmentBoundaries.SHIPPED.afternoonStart)
        assertEquals(LocalTime.of(18, 0), DaySegmentBoundaries.SHIPPED.eveningStart)
    }

    @Test
    fun `boundaries must be strictly ascending`() {
        val result = DaySegmentBoundaries.of(LocalTime.of(12, 0), LocalTime.of(6, 0), LocalTime.of(18, 0))

        assertTrue(result is DaySegmentBoundariesResult.Rejected.NotAscending)
    }

    @Test
    fun `equal boundaries are rejected, since one segment would be empty`() {
        // An empty segment is one an anchor could point at and never arrive in — the same silent
        // non-firing AnchorCompatibility refuses.
        val result = DaySegmentBoundaries.of(LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(12, 0))

        assertTrue(result is DaySegmentBoundariesResult.Rejected.NotAscending)
    }

    @Test
    fun `a user whose day starts at 10 gets a morning that starts at 10`() {
        val late = boundaries("10:00", "14:00", "20:00")

        assertEquals(DaySegment.MORNING, late.segmentAt(LocalTime.of(10, 30)).segment)
        assertEquals(DaySegment.AFTERNOON, late.segmentAt(LocalTime.of(14, 0)).segment)
        assertEquals(DaySegment.EVENING, late.segmentAt(LocalTime.of(23, 0)).segment)
    }

    @Test
    fun `each boundary is inclusive of the segment it opens`() {
        val shipped = DaySegmentBoundaries.SHIPPED

        assertEquals(DaySegment.MORNING, shipped.segmentAt(LocalTime.of(6, 0)).segment)
        assertEquals(DaySegment.AFTERNOON, shipped.segmentAt(LocalTime.of(12, 0)).segment)
        assertEquals(DaySegment.EVENING, shipped.segmentAt(LocalTime.of(18, 0)).segment)
    }

    @Test
    fun `the hours before the day starts belong to the previous day's evening`() {
        val shipped = DaySegmentBoundaries.SHIPPED

        val small = shipped.segmentAt(LocalTime.of(2, 0))

        assertEquals(DaySegment.EVENING, small.segment)
        assertTrue("02:00 is the tail of the day before, not this day's morning", small.belongsToPreviousDay)
    }

    @Test
    fun `a time after the day starts never belongs to the previous day`() {
        val shipped = DaySegmentBoundaries.SHIPPED

        assertFalse(shipped.segmentAt(LocalTime.of(6, 0)).belongsToPreviousDay)
        assertFalse(shipped.segmentAt(LocalTime.of(23, 59)).belongsToPreviousDay)
    }

    @Test
    fun `travelling from UTC-6 to UTC+2 does not move the user's start of day`() {
        // The traveller case, and the whole reason a boundary is a wall-clock time rather than an
        // instant. The same moment renders as a different local time in each zone, but 06:00 local
        // opens the morning in both — which is what "my day starts at six" means.
        val shipped = DaySegmentBoundaries.SHIPPED
        val mexicoCity = ZoneId.of("UTC-6")
        val berlin = ZoneId.of("UTC+2")
        val moment = Instant.parse("2026-08-27T12:00:00Z")

        val localInMexico = moment.atZone(mexicoCity).toLocalTime()
        val localInBerlin = moment.atZone(berlin).toLocalTime()

        assertEquals(LocalTime.of(6, 0), localInMexico)
        assertEquals(LocalTime.of(14, 0), localInBerlin)
        assertEquals(DaySegment.MORNING, shipped.segmentAt(localInMexico).segment)
        assertEquals(DaySegment.AFTERNOON, shipped.segmentAt(localInBerlin).segment)
        // The boundary itself never moved: 06:00 opens the morning in either zone.
        assertEquals(DaySegment.MORNING, shipped.segmentAt(LocalTime.of(6, 0)).segment)
    }

    @Test
    fun `ofMinutes matches the equivalent wall-clock construction`() {
        val fromMinutes = DaySegmentBoundaries.ofMinutes(10 * 60, 14 * 60, 20 * 60)

        assertTrue(fromMinutes is DaySegmentBoundariesResult.Valid)
        assertEquals(boundaries("10:00", "14:00", "20:00"), (fromMinutes as DaySegmentBoundariesResult.Valid).boundaries)
    }

    @Test
    fun `ofMinutes refuses a non-ascending stored triple`() {
        val result = DaySegmentBoundaries.ofMinutes(12 * 60, 6 * 60, 18 * 60)

        assertTrue(result is DaySegmentBoundariesResult.Rejected.NotAscending)
    }

    @Test
    fun `every descriptor is bounded, uniquely keyed, and defaults to the shipped segmentation`() {
        val keys = DaySegmentBoundaries.ALL.map { it.key }

        assertEquals(3, DaySegmentBoundaries.ALL.size)
        assertEquals(keys.size, keys.toSet().size)
        DaySegmentBoundaries.ALL.forEach { field ->
            field as com.gcatcode.petmephone.core.domain.config.ConfigField.IntField
            assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
            assertTrue("unexpected key shape: ${field.key}", Regex("""config_override\.day_segments\.[a-z_]+""").matches(field.key))
        }
        // The descriptors and SHIPPED are two statements of the same segmentation; they must agree,
        // for the same reason BalanceConfig's constructor and descriptors must.
        val fromDescriptors = DaySegmentBoundaries.ofMinutes(
            DaySegmentBoundaries.DAY_START_MINUTES.shippedDefault,
            DaySegmentBoundaries.AFTERNOON_START_MINUTES.shippedDefault,
            DaySegmentBoundaries.EVENING_START_MINUTES.shippedDefault,
        )
        assertEquals(
            DaySegmentBoundaries.SHIPPED,
            (fromDescriptors as DaySegmentBoundariesResult.Valid).boundaries,
        )
    }
}
