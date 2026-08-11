package com.gcatcode.petmephone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ui.CharacterImportController
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingViewModel
import com.gcatcode.petmephone.feature.overlay.service.PetOverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry point and the app's single activity.
 *
 * Dependencies are injected here and handed down to [PetMeApp] as plain parameters, because that is
 * already how every screen declares them. Routing them through `hiltViewModel()` would mean giving
 * five tested composables real `ViewModel` supertypes — the thing slice 1 deliberately avoided —
 * and rewriting their tests to reach behaviour that has not changed.
 *
 * Starting the service on every `onStart` is intentional and cheap. [PetOverlayService] is
 * idempotent — `onStartCommand` reconstructs everything from live and persisted state and adds no
 * second window when one already exists — so this is a resume, not a duplicate.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var overlayPermissionChecker: OverlayPermissionChecker

    @Inject
    lateinit var overlaySettingsLauncher: OverlaySettingsLauncher

    @Inject
    lateinit var onboardingViewModel: OverlayOnboardingViewModel

    @Inject
    lateinit var characterRepository: CharacterRepository

    @Inject
    lateinit var activeCharacterRepository: ActiveCharacterRepository

    @Inject
    lateinit var libraryConfig: CharacterLibraryConfig

    @Inject
    lateinit var importController: CharacterImportController

    @Inject
    lateinit var importer: CharacterImporter

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // A cancelled picker returns null. That is the user changing their mind, not a failure, so
        // the controller is simply never advanced and the import screen stays where it was.
        if (uri != null) importController.onImagePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PetMeApp(
                    onboardingViewModel = onboardingViewModel,
                    settingsLauncher = overlaySettingsLauncher,
                    characterRepository = characterRepository,
                    activeCharacterRepository = activeCharacterRepository,
                    libraryConfig = libraryConfig,
                    importController = importController,
                    importer = importer,
                    onPickImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    permissionGranted = overlayPermissionChecker.canDrawOverlays(),
                    onPermissionGranted = ::startOverlayService,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Queried live, never remembered: on some devices the grant is revoked by the system
        // itself, not by the user, so a cached answer would be wrong within seconds.
        if (overlayPermissionChecker.canDrawOverlays()) startOverlayService()
    }

    private fun startOverlayService() {
        startService(Intent(this, PetOverlayService::class.java))
    }
}
