package com.gcatcode.petmephone.debug.tuning

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `debug-tuning-panel` spec: the row-state matrix over `tuningRowOf(field, stored)`. No Android
 * import, no Compose import, no coroutines — `TuningRowState.kt` never re-implements `resolve`.
 */
class TuningRowStateTest {

    private val field = BalanceConfig.DAILY_TASK_GOAL

    @Test
    fun `absent stored value is not overridden and current equals the shipped default`() {
        val row = tuningRowOf(field, StoredOverride.Absent)

        assertFalse(row.overridden)
        assertEquals(row.shippedDefault, row.currentValue)
    }

    @Test
    fun `present in-range value is overridden and current equals the stored value`() {
        val row = tuningRowOf(field, StoredOverride.Present(value = 42, writtenUnderVersion = 1))

        assertTrue(row.overridden)
        assertEquals("42", row.currentValue)
    }

    @Test
    fun `present out-of-range value falls back to the shipped default but is still overridden`() {
        val row = tuningRowOf(field, StoredOverride.Present(value = 999, writtenUnderVersion = 1))

        assertTrue(row.overridden)
        assertEquals(field.shippedDefault.toString(), row.currentValue)
    }

    @Test
    fun `a value written under an older BalanceConfig version renders as stale`() {
        val row = tuningRowOf(field, StoredOverride.Present(value = 42, writtenUnderVersion = 0))

        assertEquals(Staleness.Stale(0), row.staleness)
    }

    @Test
    fun `a value written under the current BalanceConfig version renders as fresh`() {
        val row = tuningRowOf(field, StoredOverride.Present(value = 42, writtenUnderVersion = 1))

        assertEquals(Staleness.Fresh, row.staleness)
    }

    @Test
    fun `every PetAnimationConfig field renders as not versioned, overridden or not, never fresh`() {
        for (petField in PetAnimationConfig.ALL) {
            val absentRow = tuningRowOf(petField, StoredOverride.Absent)
            assertEquals(Staleness.NotVersioned, absentRow.staleness)

            @Suppress("UNCHECKED_CAST")
            val overriddenRow = tuningRowOf(
                petField as com.gcatcode.petmephone.core.domain.config.ConfigField<Comparable<Any>>,
                StoredOverride.Present(petField.shippedDefault as Comparable<Any>, writtenUnderVersion = null),
            )
            assertEquals(Staleness.NotVersioned, overriddenRow.staleness)
        }
    }
}
