package com.gcatcode.petmephone

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Substitutes [HiltTestApplication] for [PetMePhoneApplication] during instrumented tests, so
 * `@HiltAndroidTest` can inject test doubles instead of the production Hilt graph.
 */
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
