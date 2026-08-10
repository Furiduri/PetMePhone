package com.gcatcode.petmephone.core.data.permission

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `OverlayPermissionCheckerImpl` holds no field to go stale: it forwards straight to
 * `Settings.canDrawOverlays` on every call. A fresh Robolectric application has never been granted
 * the special access, so this pins the un-granted default; the live, always-re-queried behaviour
 * against a real grant/revoke cycle is proven on-device (`adb shell appops set ... allow|deny`),
 * since Robolectric does not model the underlying `AppOpsManager` op faithfully enough to assert
 * that transition here.
 */
// See PetMePhoneApplicationWorkManagerTest: Robolectric 4.16.1 has no SDK 37 shadows yet.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayPermissionCheckerImplTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val checker = OverlayPermissionCheckerImpl(context)

    @Test
    fun `overlay permission is not granted by default`() {
        assertFalse(checker.canDrawOverlays())
    }
}
