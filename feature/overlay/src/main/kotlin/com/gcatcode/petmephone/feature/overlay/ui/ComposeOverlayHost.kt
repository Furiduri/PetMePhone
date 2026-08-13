package com.gcatcode.petmephone.feature.overlay.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gcatcode.petmephone.core.designsystem.theme.PetMePhoneTheme

/**
 * Hosts a Compose composition inside the [android.view.WindowManager] window that
 * `PetOverlayService` (#13) creates and adds this view to.
 *
 * A [android.view.View] attached through `WindowManager` from a `Service` has **no tree
 * owners**: `ViewTreeLifecycleOwner` and `ViewTreeSavedStateRegistryOwner` resolve by walking up
 * the view hierarchy, and that walk terminates at nothing outside an Activity.
 * `ComposeView.setContent` throws immediately if either is missing, so this class supplies both
 * itself rather than relying on an ancestor that does not exist.
 *
 * The [LifecycleRegistry] here is deliberately its own instance, **not** reused from
 * `LifecycleService`: the service's lifecycle maps to the *process* (`Service.onCreate` /
 * `onDestroy`), while what this tree needs is a lifecycle representing the *window's*
 * visibility. Reusing the service lifecycle would fire `ON_RESUME` at `Service.onCreate`,
 * before the window is even attached to `WindowManager`. Instead, this registry is driven purely
 * by this view's own [onAttachedToWindow] / [onDetachedFromWindow] plus the explicit [destroy]
 * call the owning service makes on real teardown.
 *
 * `ON_CREATE` → `ON_START` → `ON_RESUME` are dispatched in that exact order on attach, because
 * Compose does not start composing until the lifecycle reaches at least `STARTED`. Get the order
 * wrong and there is **no exception** — the view stays blank forever, because `setContent` runs
 * but the `Recomposer` never starts running frames. That silent failure mode is why the
 * regression tests for this class dispatch the sequence and assert composition actually ran,
 * rather than merely asserting no crash.
 *
 * The wired-in [SavedStateRegistry] is structurally required (`setContent` throws without a
 * `ViewTreeSavedStateRegistryOwner`) but **semantically inert**: nothing under this composition
 * is ever persisted to or restored from it. State comes from Room and DataStore, per the
 * architecture rule that this tree holds no state of its own. If a future bug looks fixable with
 * `rememberSaveable`, it isn't — that call would silently do nothing here.
 *
 * There is deliberately no `ViewTreeViewModelStoreOwner`. Nothing under this tree may call
 * `viewModel()` or `hiltViewModel()`; the overlay consumes `@Inject`ed dependencies directly.
 *
 * [ViewCompositionStrategy.DisposeOnLifecycleDestroyed] is used instead of the default
 * `DisposeOnDetachedFromWindow`. If the window is ever repositioned by removing and re-adding
 * the view rather than `WindowManager.updateViewLayout`, the default would dispose and rebuild
 * the whole composition on every reposition. This strategy only disposes when [destroy] moves
 * the registry to `DESTROYED`, which happens exactly once, on genuine teardown.
 */
class ComposeOverlayHost(
    context: Context,
    private val content: @Composable () -> Unit,
) : AbstractComposeView(context), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))
    }

    /**
     * Every overlay surface is themed here rather than at each call site, so a future one cannot
     * forget. Without it Compose falls back to Material's baseline palette — the app's own
     * `PetMePhoneTheme` is applied by `MainActivity`, which no `WindowManager`-hosted view passes
     * through. The pet never showed the difference because it is a sprite; the quick-menu card is
     * the first overlay surface with real chrome, and it surfaced the gap immediately on device.
     *
     * `isSystemInDarkTheme()` reads the configuration, so light and dark follow the system here
     * exactly as they do inside the Activity.
     */
    @Composable
    override fun Content() = PetMePhoneTheme { content() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // performAttach() before performRestore(), and both while still INITIALIZED (before
        // ON_CREATE). Verified against the actual androidx.savedstate 1.3.2 implementation
        // (BOM 2026.02.01), not assumed: performAttach() registers a Recreator lifecycle
        // observer that consumes restored state the moment ON_CREATE is dispatched, so skipping
        // performRestore() before that point throws — see ComposeOverlayHostTest. Restoring
        // with a null bundle is intentional: this registry never has anything saved to restore.
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    // Deliberately does not lower the lifecycle state on detach. An ordinary detach — for
    // instance a reposition performed by remove-then-add instead of updateViewLayout — is not
    // teardown, and DisposeOnLifecycleDestroyed only reacts to DESTROYED. Real teardown goes
    // through the explicit destroy() call below instead.

    /**
     * Genuine, one-time teardown. Moves the lifecycle to `DESTROYED`, which disposes the
     * composition via [ViewCompositionStrategy.DisposeOnLifecycleDestroyed] and stops the
     * `Recomposer`. Must be called exactly once, from the owning service's teardown path, right
     * before the view is removed from `WindowManager`. Never call this for an ordinary detach —
     * a lifecycle that never reaches `DESTROYED` leaks the `Recomposer` coroutine and every Flow
     * collector it holds; calling this more than once, or before real teardown, tears the
     * composition down early.
     */
    fun destroy() {
        // LifecycleRegistry refuses to move directly from INITIALIZED to DESTROYED (it requires
        // at least CREATED first). That state is reachable here if the host is torn down before
        // WindowManager ever actually dispatched onAttachedToWindow — addView succeeding is not
        // a guarantee attach already ran synchronously. Bump through CREATED defensively so
        // teardown is always safe to call, attached or not.
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
