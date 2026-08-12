package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.pet.state.PetSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `hunger-metric` spec: "isHungry has no pet-state wiring in this change" / "isHungerPriority has
 * no pet-state wiring in this change". `isHungry` and `isHungerPriority` ship as pure functions
 * with no consumer — no `PetSnapshot` field, no sprite, no `PetStateProvider` change. Asserted
 * through [PetSnapshot]'s actual field set, never through reflection over unrelated code.
 *
 * The guard matches both word forms, `hunger` and `hungry`, because a breach field named
 * `isHungry` lowercases to `ishungry`, which does not contain `hunger` (no `e` after the `g`) — a
 * `contains("hunger")`-only guard would let that exact breach through.
 */
class HungerHasNoPetStateWiringTest {

    private val hungerRelated: (String) -> Boolean = { name ->
        val lower = name.lowercase()
        lower.contains("hunger") || lower.contains("hungry")
    }

    @Test
    fun `the matcher itself catches both word forms`() {
        // Self-check: prove the predicate would actually catch the breach it exists to forbid,
        // rather than assuming it. Fails if the predicate is ever narrowed back to one word form.
        assertTrue("isHungry", hungerRelated("isHungry"))
        assertTrue("hungerPercent", hungerRelated("hungerPercent"))
    }

    @Test
    fun `PetSnapshot declares no hunger-related field`() {
        val fieldNames = PetSnapshot::class.java.declaredFields.map { it.name }
        assertFalse(
            "PetSnapshot must not gain a hunger field until part B: $fieldNames",
            fieldNames.any(hungerRelated),
        )
    }
}
