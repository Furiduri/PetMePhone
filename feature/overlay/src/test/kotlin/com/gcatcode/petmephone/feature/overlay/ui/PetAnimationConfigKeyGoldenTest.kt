package com.gcatcode.petmephone.feature.overlay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The [BalanceConfigKeyGoldenTest][com.gcatcode.petmephone.core.domain.balance.BalanceConfigKeyGoldenTest]
 * counterpart for [PetAnimationConfig] (design decision 8). See that test for why uniqueness is not
 * enough and only pinned literals catch a silent override-discarding rename.
 *
 * If this test fails, the change under review is a storage migration. Restore the literal, or add the
 * old string to that descriptor's `previousKeys`.
 */
class PetAnimationConfigKeyGoldenTest {
    @Test
    fun `the frozen storage keys are exactly these literals`() {
        assertEquals(
            listOf(
                "config_override.pet_animation.frame_interval_millis",
                "config_override.pet_animation.min_frame_interval_millis",
                "config_override.pet_animation.state_sharing_timeout_millis",
            ),
            PetAnimationConfig.ALL.map { it.key },
        )
    }

    @Test
    fun `the override group id is frozen too`() {
        assertEquals("pet_animation", PetAnimationConfig.GROUP.id)
    }
}
