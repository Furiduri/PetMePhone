package com.gcatcode.petmephone.feature.overlay.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.R

/**
 * States all four required claims (`[ONBOARD-1]`) and delegates the whole settings hand-off to
 * [OverlaySettingsLauncher] — this file never constructs its own Settings-launch `Intent`
 * (`[ONBOARD-2]`), which any caller can confirm by grepping this file.
 *
 * `onResume` fires once per composition of this screen, via [LaunchedEffect]. No `:app` Activity
 * hosts this screen yet (same gap `ImportScreen`/`PreviewScreen` already document), so there is no
 * real `Activity.onResume` to hook into; whoever wires the host Activity should also call
 * [OverlayOnboardingViewModel.onResume] from its own `onResume`, in addition to (or instead of)
 * this composition-time call, once that host exists.
 */
@Composable
fun OverlayOnboardingScreen(
    viewModel: OverlayOnboardingViewModel,
    settingsLauncher: OverlaySettingsLauncher,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        viewModel.onResume()
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.feature_overlay_onboarding_claim_what_appears))
        Text(stringResource(R.string.feature_overlay_onboarding_claim_no_access))
        Text(stringResource(R.string.feature_overlay_onboarding_claim_no_data_leaves))
        Text(stringResource(R.string.feature_overlay_onboarding_claim_revocable))

        val actionDescription = stringResource(R.string.feature_overlay_onboarding_primary_action_description)
        Button(
            onClick = {
                viewModel.onSettingsLaunched()
                settingsLauncher.launchOverlaySettings()
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .semantics { contentDescription = actionDescription }
                .testTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.feature_overlay_onboarding_primary_action))
        }
    }
}

const val ONBOARDING_PRIMARY_ACTION_TEST_TAG = "onboarding_primary_action"
