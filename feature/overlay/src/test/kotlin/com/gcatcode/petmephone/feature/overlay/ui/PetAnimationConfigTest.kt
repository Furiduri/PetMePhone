package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.config.ConfigField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same registry-invariant assertions as `BalanceConfigTest`, applied to [PetAnimationConfig.ALL]
 * (design decision 2a). `PetAnimationConfig` declares no version notion, so its group carries no
 * staleness stamp and is never stale.
 */
class PetAnimationConfigTest {

    @Test
    fun `GROUP has no staleness notion`() {
        assertNull(PetAnimationConfig.GROUP.currentVersion)
    }

    @Test
    fun `every ALL descriptor is bounded and uniquely keyed`() {
        val keys = PetAnimationConfig.ALL.map { it.key }

        for (field in PetAnimationConfig.ALL) {
            when (field) {
                is ConfigField.LongField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
                else -> error("unexpected field type for ${field.key}")
            }
            assertTrue("unexpected key shape: ${field.key}", Regex("""config_override\.pet_animation\.[a-z_]+""").matches(field.key))
        }
        assertEquals("duplicate key found in PetAnimationConfig.ALL", keys.size, keys.toSet().size)
    }
}
