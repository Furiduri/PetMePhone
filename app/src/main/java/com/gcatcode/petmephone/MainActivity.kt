package com.gcatcode.petmephone

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Placeholder entry point. This activity is intentionally blank while the
 * module skeleton is being established; the real UI lands with the feature
 * modules once the Compose convention plugin exists (see #2). `@AndroidEntryPoint`
 * makes it a Hilt entry point, per the dependency-injection spec (#6); Hilt requires a
 * `ComponentActivity` subclass to generate its base class, hence the switch from the
 * plain `android.app.Activity` used since PR 1.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
