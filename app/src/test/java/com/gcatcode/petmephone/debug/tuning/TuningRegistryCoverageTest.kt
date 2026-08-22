package com.gcatcode.petmephone.debug.tuning

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `debug-tuning-panel` spec: "Every registered field is enumerated" and "A not-versioned row is
 * never reported as fresh". Walks the real registries, not a hand-copied list.
 */
class TuningRegistryCoverageTest {

    @Suppress("UNCHECKED_CAST")
    private fun rowFor(field: ConfigField<*>): TuningRow =
        tuningRowOf(field as ConfigField<Comparable<Any>>, StoredOverride.Absent)

    @Test
    fun `tuningRowOf produces exactly eight rows with unique keys, one per registered field`() {
        val allFields: List<ConfigField<*>> = BalanceConfig.ALL + PetAnimationConfig.ALL

        assertEquals(8, allFields.size)
        assertEquals(5, BalanceConfig.ALL.size)
        assertEquals(3, PetAnimationConfig.ALL.size)

        val rows = allFields.map(::rowFor)
        val keys = rows.map { it.key }
        assertEquals(allFields.map { it.key }.toSet(), keys.toSet())
        assertEquals(8, keys.toSet().size)
    }

    @Test
    fun `NotVersioned occurs if and only if the field's group has no current version`() {
        val allFields: List<ConfigField<*>> = BalanceConfig.ALL + PetAnimationConfig.ALL

        allFields.forEach { field ->
            val row = rowFor(field)
            if (field.group.currentVersion == null) {
                assertEquals("expected ${field.key} not versioned", Staleness.NotVersioned, row.staleness)
            } else {
                assertTrue("expected ${field.key} not to be NotVersioned", row.staleness !is Staleness.NotVersioned)
            }
        }
    }
}
