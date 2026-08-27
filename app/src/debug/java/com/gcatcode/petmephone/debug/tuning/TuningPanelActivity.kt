package com.gcatcode.petmephone.debug.tuning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.gcatcode.petmephone.core.designsystem.theme.PetMePhoneTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The debug-only launcher entry point. No debug/release runtime flag check anywhere in this file
 * or anywhere under `debug/tuning/` (design decision 6): the source set this class lives in is the
 * only gate — it simply does not exist in a release compile.
 */
@AndroidEntryPoint
class TuningPanelActivity : ComponentActivity() {

    private val viewModel: TuningPanelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PetMePhoneTheme {
                TuningPanelScreen(viewModel = viewModel)
            }
        }
    }
}
