package com.gcatcode.petmephone.core.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `config-override-store` spec, design decisions 1, 1a: the sealed hierarchy exposes exactly
 * [ConfigField.IntField], [ConfigField.LongField], [ConfigField.DoubleField]; a descriptor's `T`
 * constrains call sites at compile time; `ConfigGroup(id, currentVersion = null)` means no
 * staleness notion.
 */
class ConfigFieldTest {

    private val group = ConfigGroup(id = "test_group", currentVersion = 1)

    @Test
    fun `the sealed hierarchy exposes exactly IntField, LongField and DoubleField`() {
        // No kotlin-reflect dependency in this module (matches the existing QuickMenuContentTest
        // convention), so this is proven by the compiler: an exhaustive `when` with no `else`
        // branch only compiles if these three subclasses are the complete set.
        fun describe(field: ConfigField<*>): String = when (field) {
            is ConfigField.IntField -> "IntField"
            is ConfigField.LongField -> "LongField"
            is ConfigField.DoubleField -> "DoubleField"
        }

        assertEquals("IntField", describe(ConfigField.IntField("k", group, 0, 0, 1)))
        assertEquals("LongField", describe(ConfigField.LongField("k", group, 0L, 0L, 1L)))
        assertEquals("DoubleField", describe(ConfigField.DoubleField("k", group, 0.0, 0.0, 1.0)))
    }

    @Test
    fun `a descriptor carries its shipped default, min, max and empty previousKeys by default`() {
        val field = ConfigField.IntField(key = "config_override.test_group.count", group = group, shippedDefault = 5, min = 0, max = 10)

        assertEquals(5, field.shippedDefault)
        assertEquals(0, field.min)
        assertEquals(10, field.max)
        assertTrue(field.previousKeys.isEmpty())
    }

    @Test
    fun `a group with a null currentVersion carries no staleness notion`() {
        assertNull(ConfigGroup(id = "pet_animation", currentVersion = null).currentVersion)
    }

    @Test
    fun `StoredOverride is exactly Absent or Present`() {
        val absent: StoredOverride<Int> = StoredOverride.Absent
        val present: StoredOverride<Int> = StoredOverride.Present(value = 7, writtenUnderVersion = 1)

        assertTrue(absent is StoredOverride.Absent)
        assertTrue(present is StoredOverride.Present)
        assertEquals(7, (present as StoredOverride.Present<Int>).value)
    }
}
