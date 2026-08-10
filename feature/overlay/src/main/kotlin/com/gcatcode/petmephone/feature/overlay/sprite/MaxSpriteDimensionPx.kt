package com.gcatcode.petmephone.feature.overlay.sprite

import javax.inject.Qualifier

/** Qualifies the injected `maxDimensionPx` safety bound so a plain `Int` binding is unambiguous. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MaxSpriteDimensionPx
