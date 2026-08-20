package com.gcatcode.petmephone.core.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `config-override-store` spec, "Validation rejects an out-of-range write" (the typed-reason half)
 * and "No whole-config write exists" (the interface-shape half, design decisions 1, 4).
 */
class ConfigWriteResultTest {

    @Test
    fun `OutOfRange carries key, min, max and offending typed to the field's T, not a string`() {
        val result = ConfigWriteResult.OutOfRange(key = "config_override.balance.daily_task_goal", min = 1, max = 100, offending = 0)

        assertEquals("config_override.balance.daily_task_goal", result.key)
        assertEquals(1, result.min)
        assertEquals(100, result.max)
        assertEquals(0, result.offending)
    }

    @Test
    fun `no ConfigOverrideStore method accepts a collection, an array (vararg) or a whole config object`() {
        // Plain java.lang.reflect: no kotlin-reflect dependency needed (matches BalanceConfigTest).
        val methods = ConfigOverrideStore::class.java.declaredMethods
        assertTrue(methods.isNotEmpty())

        for (method in methods) {
            for (parameterType in method.parameterTypes) {
                assertFalse("${method.name} must not accept a Collection", Collection::class.java.isAssignableFrom(parameterType))
                assertFalse("${method.name} must not accept an array (vararg)", parameterType.isArray)
                assertFalse("${method.name} must not accept a whole config object", parameterType.simpleName.endsWith("Config"))
            }
        }
    }
}
