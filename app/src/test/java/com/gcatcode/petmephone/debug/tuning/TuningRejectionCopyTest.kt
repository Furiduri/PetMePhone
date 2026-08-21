package com.gcatcode.petmephone.debug.tuning

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `debug-tuning-panel` spec: "A write goes through the store and is subject to its declared range"
 * — the typed-rejection-wording half, and the parser that decides what never reaches the store.
 */
class TuningRejectionCopyTest {

    private val intField = BalanceConfig.DAILY_TASK_GOAL
    private val doubleField = BalanceConfig.HUNGRY_THRESHOLD_RATIO

    @Test
    fun `blank text is unparseable`() {
        assertEquals(ParsedInput.Unparseable, parseTypedValue(intField, ""))
        assertEquals(ParsedInput.Unparseable, parseTypedValue(intField, "   "))
    }

    @Test
    fun `non-numeric text is unparseable`() {
        assertEquals(ParsedInput.Unparseable, parseTypedValue(intField, "abc"))
    }

    @Test
    fun `scientific notation into an IntField is unparseable`() {
        assertEquals(ParsedInput.Unparseable, parseTypedValue(intField, "1e9"))
    }

    @Test
    fun `a well-formed decimal into a DoubleField is valid`() {
        val parsed = parseTypedValue(doubleField, "0.6")

        assertEquals(ParsedInput.Valid(0.6), parsed)
    }

    @Test
    fun `rejectionMessage names the field, its declared min and max, and the offending value`() {
        val rejection = ConfigWriteResult.OutOfRange(
            key = intField.key,
            min = intField.min,
            max = intField.max,
            offending = 999,
        )

        val message = rejectionMessage(rejection)

        assertTrue(message.contains(intField.key))
        assertTrue(message.contains(intField.min.toString()))
        assertTrue(message.contains(intField.max.toString()))
        assertTrue(message.contains("999"))
        // Typed data as wording, borrowing no display copy from :core:domain.
        assertFalse(message.contains("ConfigWriteResult"))
    }

    @Test
    fun `unparseableMessage names the field and its expected range`() {
        val message = unparseableMessage(intField)

        assertTrue(message.contains(intField.key))
        assertTrue(message.contains(intField.min.toString()))
        assertTrue(message.contains(intField.max.toString()))
    }
}
