package com.gcatcode.petmephone.feature.overlay.ui

import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression coverage for #14: a [ComposeOverlayHost] attached through `WindowManager` from a
 * service has no tree owners, and the highest-risk failure mode there is silent — a wrong
 * lifecycle dispatch order produces no exception at all, just a permanently blank view. These
 * tests exercise the owner-and-lifecycle wiring directly rather than relying on
 * `createComposeRule`/`createAndroidComposeRule`, both of which need an Activity harness that
 * defeats the point of this class.
 *
 * Owners resolve by a view-tree tag walk, but Compose's window-scoped `Recomposer` specifically
 * resolves the owner from [android.view.View.getRootView] — verified against the actual
 * `WindowRecomposer.android.kt` source, not assumed. That means the regression test must attach
 * [ComposeOverlayHost] itself as the *root* of a window (no Activity, no wrapping ViewGroup
 * above it) exactly as `PetOverlayService` does via `WindowManager.addView`, rather than merely
 * nesting it under an arbitrary `FrameLayout` inside an Activity: nesting one level down under
 * an Activity's own window root would make the root-view walk land on the Activity's decor view
 * — which still supplies no owner of its own here — and miss the tag [ComposeOverlayHost] sets
 * on itself, failing for the wrong reason.
 */
// Robolectric 4.16.1 ships no SDK 37 shadows yet — see PetOverlayServiceTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeOverlayHostTest {

    private fun windowManager(): WindowManager =
        RuntimeEnvironment.getApplication().getSystemService(WindowManager::class.java)

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )

    private fun idleMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `composition actually runs once attached, with no owner from outside the class`() {
        var composed = false
        val host = ComposeOverlayHost(RuntimeEnvironment.getApplication()) { composed = true }

        windowManager().addView(host, overlayLayoutParams())
        idleMainLooper()

        assertTrue("expected setContent's composable to have actually run", composed)
    }

    /**
     * Design decision 8 (#18): the card window has no ancestor to resolve
     * `ViewTreeOnBackPressedDispatcherOwner` from, exactly the same structural gap as the
     * lifecycle and saved-state owners above. Wiring it here does not add any back handling by
     * itself — a composable under this tree still needs its own `BackHandler` — but a dispatcher
     * must exist on the tree before that `BackHandler` can attach to anything.
     */
    @Test
    fun `an OnBackPressedDispatcherOwner is resolvable from the host's own view tree`() {
        val host = ComposeOverlayHost(RuntimeEnvironment.getApplication()) { }

        windowManager().addView(host, overlayLayoutParams())
        idleMainLooper()

        assertTrue(
            "expected host.findViewTreeOnBackPressedDispatcherOwner() to resolve a non-null owner",
            host.findViewTreeOnBackPressedDispatcherOwner() != null,
        )
    }

    @Test
    fun `lifecycle reaches RESUMED after the attach dispatch sequence`() {
        val host = ComposeOverlayHost(RuntimeEnvironment.getApplication()) { }

        windowManager().addView(host, overlayLayoutParams())
        idleMainLooper()

        assertEquals(Lifecycle.State.RESUMED, host.lifecycle.currentState)
    }

    /**
     * Verified against the actual androidx.savedstate 1.3.2 implementation (BOM 2026.02.01),
     * rather than assumed per the issue's explicit risk callout: `performAttach()` registers a
     * `Recreator` lifecycle observer that consumes restored state the instant `ON_CREATE` is
     * dispatched. Skip `performRestore()` beforehand and that observer throws the moment the
     * lifecycle reaches `CREATED` — which is exactly the ordering constraint
     * [ComposeOverlayHost.onAttachedToWindow] respects.
     */
    @Test
    fun `dispatching ON_CREATE without a prior performRestore throws`() {
        val owner = FakeSavedStateOwner()
        val controller = SavedStateRegistryController.create(owner)
        owner.registryController = controller
        controller.performAttach()
        // performRestore(null) intentionally skipped here.

        assertThrows(IllegalStateException::class.java) {
            owner.lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    /** Minimal [SavedStateRegistryOwner] used only to exercise [SavedStateRegistryController] in isolation. */
    private class FakeSavedStateOwner : SavedStateRegistryOwner, LifecycleOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry

        lateinit var registryController: SavedStateRegistryController
        override val savedStateRegistry: SavedStateRegistry get() = registryController.savedStateRegistry
    }
}
