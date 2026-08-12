package com.gcatcode.petmephone.core.domain.pet.state

/**
 * Extension point for resolving [PetState] from a [PetSnapshot]. `evaluate` MUST be a pure
 * function of its argument: no repository read, no `Flow` collection, no other side effect. This
 * keeps [PetStateResolver] able to evaluate providers in any order with an identical result.
 *
 * [priority] MUST be unique across the full registered set — [PetStateResolver] fails fast at
 * construction if two providers share a priority.
 */
interface PetStateProvider {
    val priority: Int

    /** Returns the resolved state this provider claims for [snapshot], or `null` if it does not apply. */
    fun evaluate(snapshot: PetSnapshot): PetState?
}
