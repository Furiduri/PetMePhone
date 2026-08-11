package com.gcatcode.petmephone.feature.overlay.onboarding

import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingPolicy
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Not `androidx.lifecycle.ViewModel`: no `hiltViewModel()`/`activity-compose` wiring exists in
 * this project yet (the Compose convention plugin deliberately excludes `activity-compose`, and
 * no `:app` Activity hosts this screen — the same gap `CharacterImportController` documents for
 * the import flow). Plain `@Inject`-constructed class instead, same pattern.
 *
 * `[ONBOARD-3]` `[ONBOARD-4]`: [onResume] is the only place [isGranted] is written, and it always
 * re-queries [OverlayPermissionChecker] live — this class never trusts a value it computed
 * earlier, because the grant can be revoked or changed outside the app's process at any time.
 */
class OverlayOnboardingViewModel @Inject constructor(
    private val permissionChecker: OverlayPermissionChecker,
    private val onboardingRepository: OverlayOnboardingRepository,
) {

    private val _isGranted = MutableStateFlow(false)
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    /** True after the primary action has sent the user to Settings and not yet re-resolved. */
    private var awaitingSettingsResult = false

    /** True once the app's own history says onboarding should not auto-show again. */
    val shouldAutoShow: Flow<Boolean> =
        onboardingRepository.history.map(OverlayOnboardingPolicy::shouldAutoShowOnboarding)

    /** Called by the primary action, before handing off to [com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher]. */
    fun onSettingsLaunched() {
        awaitingSettingsResult = true
    }

    /**
     * Re-queries the live permission check. `[ONBOARD-3]`: granting through Settings and
     * returning advances the flow automatically, with no manual refresh action needed, because
     * this is the same query the screen already re-runs on resume.
     *
     * `[ONBOARD-4]`: returning without granting leaves the app usable — this only records a
     * refusal (once, per settings round-trip) so future launches do not auto-show onboarding
     * again; it never blocks anything.
     */
    suspend fun onResume() {
        val granted = permissionChecker.canDrawOverlays()
        _isGranted.value = granted

        if (granted) {
            onboardingRepository.markOnboardingSeen()
        } else if (awaitingSettingsResult) {
            onboardingRepository.recordRefusal(System.currentTimeMillis())
        }
        awaitingSettingsResult = false
    }
}
