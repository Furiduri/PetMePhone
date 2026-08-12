package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.pet.state.PetSnapshot
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `hunger-metric` spec: "isHungry has no pet-state wiring in this change" / "isHungerPriority has
 * no pet-state wiring in this change". `isHungry` and `isHungerPriority` ship as pure functions
 * with no consumer — no `PetSnapshot` field, no sprite, no `PetStateProvider` change. Asserted
 * through [PetSnapshot]'s actual field set, never through reflection over unrelated code.
 */
class HungerHasNoPetStateWiringTest {

    @Test
    fun `PetSnapshot declares no hunger-related field`() {
        val fieldNames = PetSnapshot::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            "PetSnapshot must not gain a hunger field until part B: $fieldNames",
            fieldNames.any { it.contains("hunger") },
        )
    }
}
