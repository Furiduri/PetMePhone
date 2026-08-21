package com.gcatcode.petmephone.core.domain.config

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the resolved [BalanceConfig] as observable state, in `:core:domain` so `:feature:overlay`
 * can depend on it without a main-source `:core:data` dependency (design decision 6). Implemented in
 * `:core:data` by folding every [BalanceConfig.ALL] descriptor through [resolve] against a
 * [ConfigOverrideStore]; no code path can construct a partial config from this seam.
 */
interface BalanceConfigSource {
    val config: StateFlow<BalanceConfig>
}
