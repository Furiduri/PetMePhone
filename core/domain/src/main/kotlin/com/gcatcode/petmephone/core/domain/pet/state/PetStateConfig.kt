package com.gcatcode.petmephone.core.domain.pet.state

/**
 * Injected configuration for [PetStateResolver]. [minimumDwellMillis] is never a literal inside
 * the resolver itself — it is provided from the DI graph so it stays a value change, not a code
 * hunt.
 */
data class PetStateConfig(val minimumDwellMillis: Long)
