package com.gcatcode.petmephone.core.domain.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** `balance-configuration` / decision 7: floor everywhere, never round-half-up. */
class MetricRoundingTest {

    @Test
    fun `7 out of 10 floors to 70`() {
        assertEquals(70, MetricRounding.percentOf(7, 10))
    }

    @Test
    fun `12 out of 10 clamps and floors to 100`() {
        assertEquals(100, MetricRounding.percentOf(12, 10))
    }

    @Test
    fun `9 out of 10 does not round up to 100`() {
        // 9.6-style near misses must not claim a target reached that is not.
        assertEquals(90, MetricRounding.percentOf(9, 10))
    }

    @Test
    fun `negative value clamps to 0`() {
        assertEquals(0, MetricRounding.percentOf(-5, 10))
    }

    @Test
    fun `goal of zero throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            MetricRounding.percentOf(5, 0)
        }
    }
}
