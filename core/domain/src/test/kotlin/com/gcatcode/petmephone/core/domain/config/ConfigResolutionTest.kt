package com.gcatcode.petmephone.core.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `config-override-store` spec: the pure halves of every resolution/staleness/range requirement.
 * Total over the matrix: absent, present in/out of range, present under an older/current group
 * version, boundary values, and every field type ([Int], [Long], [Double]).
 */
class ConfigResolutionTest {

    private val versionedGroup = ConfigGroup(id = "balance", currentVersion = 2)
    private val staticGroup = ConfigGroup(id = "pet_animation", currentVersion = null)

    private val intField = ConfigField.IntField("config_override.balance.count", versionedGroup, 10, 1, 100)
    private val longField = ConfigField.LongField("config_override.pet_animation.duration", staticGroup, 150L, 16L, 2_000L)
    private val doubleField = ConfigField.DoubleField("config_override.balance.ratio", versionedGroup, 0.6, 0.0, 1.0)

    @Test
    fun `absence resolves to the shipped default with SHIPPED_DEFAULT and no staleness`() {
        val resolved = resolve(intField, StoredOverride.Absent)

        assertEquals(10, resolved.value)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT, resolved.source)
        assertNull(resolved.staleFrom)
    }

    @Test
    fun `a present in-range value resolves to itself with OVERRIDE`() {
        val resolved = resolve(intField, StoredOverride.Present(value = 42, writtenUnderVersion = 2))

        assertEquals(42, resolved.value)
        assertEquals(ResolutionSource.OVERRIDE, resolved.source)
    }

    @Test
    fun `a present out-of-range value falls back to the shipped default with SHIPPED_DEFAULT_RANGE_NARROWED, never zero`() {
        val resolved = resolve(intField, StoredOverride.Present(value = 999, writtenUnderVersion = 2))

        assertEquals(10, resolved.value)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolved.source)
        assertNull(resolved.staleFrom)
    }

    @Test
    fun `a present value written under an older group version is kept and flagged stale, one under the current version is not`() {
        val stale = resolve(intField, StoredOverride.Present(value = 42, writtenUnderVersion = 1))
        val fresh = resolve(intField, StoredOverride.Present(value = 42, writtenUnderVersion = 2))

        assertEquals(42, stale.value)
        assertEquals(1, stale.staleFrom)
        assertNull(fresh.staleFrom)
    }

    @Test
    fun `a field whose group has no staleness notion is never flagged stale`() {
        val resolved = resolve(longField, StoredOverride.Present(value = 500L, writtenUnderVersion = 99))

        assertEquals(ResolutionSource.OVERRIDE, resolved.source)
        assertNull(resolved.staleFrom)
    }

    @Test
    fun `Int boundaries at min and max are accepted, one step outside is rejected`() {
        assertEquals(ResolutionSource.OVERRIDE, resolve(intField, StoredOverride.Present(1, 2)).source)
        assertEquals(ResolutionSource.OVERRIDE, resolve(intField, StoredOverride.Present(100, 2)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(intField, StoredOverride.Present(0, 2)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(intField, StoredOverride.Present(101, 2)).source)
    }

    @Test
    fun `Long boundaries at min and max are accepted, one step outside is rejected`() {
        assertEquals(ResolutionSource.OVERRIDE, resolve(longField, StoredOverride.Present(16L, null)).source)
        assertEquals(ResolutionSource.OVERRIDE, resolve(longField, StoredOverride.Present(2_000L, null)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(longField, StoredOverride.Present(15L, null)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(longField, StoredOverride.Present(2_001L, null)).source)
    }

    @Test
    fun `Double boundaries at min and max are accepted, just outside is rejected, NaN and infinities are rejected`() {
        assertEquals(ResolutionSource.OVERRIDE, resolve(doubleField, StoredOverride.Present(0.0, 2)).source)
        assertEquals(ResolutionSource.OVERRIDE, resolve(doubleField, StoredOverride.Present(1.0, 2)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(doubleField, StoredOverride.Present(-0.0001, 2)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(doubleField, StoredOverride.Present(1.0001, 2)).source)

        val nan = resolve(doubleField, StoredOverride.Present(Double.NaN, 2))
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, nan.source)
        assertEquals(0.6, nan.value, 0.0)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(doubleField, StoredOverride.Present(Double.POSITIVE_INFINITY, 2)).source)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolve(doubleField, StoredOverride.Present(Double.NEGATIVE_INFINITY, 2)).source)
    }

    @Test
    fun `resolve never returns zero for an out-of-range Present(0, …) when the shipped default is non-zero`() {
        val resolved = resolve(intField, StoredOverride.Present(value = 0, writtenUnderVersion = 2))

        assertEquals(10, resolved.value)
        assertEquals(ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED, resolved.source)
    }
}
