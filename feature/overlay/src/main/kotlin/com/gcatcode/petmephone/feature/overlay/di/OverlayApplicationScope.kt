package com.gcatcode.petmephone.feature.overlay.di

import javax.inject.Qualifier

/**
 * Qualifies the process-lifetime `CoroutineScope` used by [com.gcatcode.petmephone.feature.overlay
 * .system.ScreenStateMonitor]'s `stateIn(WhileSubscribed)`. Not tied to the service's own scope —
 * the service holds no state and may be recreated while the app process (and this scope) survives.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class OverlayApplicationScope
