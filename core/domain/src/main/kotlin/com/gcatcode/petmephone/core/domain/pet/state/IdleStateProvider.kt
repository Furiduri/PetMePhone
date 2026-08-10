package com.gcatcode.petmephone.core.domain.pet.state

/**
 * Lowest-priority provider. Always returns `null` — [PetStateResolver]'s own IDLE fallback
 * already covers "no provider matches". This class exists as the extension-point exemplar for
 * providers added in later slices, per `design.md`'s file-changes table.
 */
class IdleStateProvider : PetStateProvider {
    override val priority: Int = PRIORITY

    override fun evaluate(snapshot: PetSnapshot): PetState? = null

    companion object {
        const val PRIORITY = 0
    }
}
