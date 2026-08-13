package com.petmephone.spike.imeviability

import android.graphics.Rect
import android.view.View
import android.view.WindowManager

/**
 * The moments a sample is taken at. Sampling only "after the keyboard is up" would leave nothing to
 * compare against, so the baseline before focus is a first-class point of its own.
 */
enum class SamplePoint(val label: String) {
    BEFORE_FOCUS("before focus"),
    AFTER_FOCUS_GAINED("after field gained focus"),
    AFTER_SHOW_SOFT_INPUT("after showSoftInput returned"),

    /**
     * A second timed sample, taken far later than [AFTER_SHOW_SOFT_INPUT].
     *
     * Round 1 saw the visible display frame shrink in exactly one run, and only in the
     * `after dismissal` sample at +13609ms — never in the +938ms one. That leaves two
     * indistinguishable explanations: either the resize takes longer than 938ms to propagate to this
     * window, or the earlier sample was simply taken too early. This point exists to separate them.
     */
    AFTER_LATE_SETTLE("after late settle window"),

    /**
     * Taken from `ViewTreeObserver.OnGlobalLayoutListener` rather than from a timer, so the moment
     * the frame actually changes is recorded instead of guessed at. Several of these can appear in
     * one run; their `+ms` column is what orders them.
     */
    ON_LAYOUT_CHANGE("on layout change"),

    AFTER_DISMISSAL("after dismissal"),
}

/**
 * One raw, directly observed reading of the overlay window's own geometry.
 *
 * Every field is recorded verbatim as the framework reported it. Nothing here is derived, and
 * nothing here defaults: a value that could not be read at all is `null`, which the findings file
 * renders as "not measured" — never as `0`, and never as `false`. The previous instrument recorded
 * a defaulted `false` for keyboard coverage and that defaulted value was then read as a
 * measurement, which is exactly the failure this type exists to make impossible.
 */
/**
 * Which view the content top in [KeyboardGeometrySample.contentTopOnScreenPx] was read from.
 *
 * It is recorded rather than inferred from the mode, because a field that exists but is not yet
 * attached falls back to the root view, and a reader comparing two rows must be able to see that the
 * two numbers were not read off the same view.
 */
enum class ContentTopSource(val label: String) {
    FIELD("field"),
    ROOT_VIEW("root view"),
}

/**
 * The device orientation a sample was taken in, derived from the measured window bounds only.
 *
 * Null is a real value here: bounds that could not be read yield no orientation, and that renders as
 * "not measured" rather than defaulting to portrait.
 */
enum class SampleOrientation(val label: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape"),
    ;

    companion object {
        fun ofBounds(bounds: Rect?): SampleOrientation? = bounds?.let {
            if (it.height() >= it.width()) PORTRAIT else LANDSCAPE
        }
    }
}

data class KeyboardGeometrySample(
    val point: SamplePoint,
    /** Milliseconds since the window was added, so the ordering of samples is observable. */
    val elapsedMillisSinceWindowAdded: Long,
    /**
     * `View.getWindowVisibleDisplayFrame` on the overlay's root view, recorded verbatim.
     *
     * KEPT AS EVIDENCE ONLY. Round 2 proved this frame is not a deterministic keyboard-height source
     * on this window class (see the round-3 note in `SpikeOverlayService.followKeyboardWithPet`), so
     * no behaviour reads it any more. It stays in the raw table precisely so its unreliability
     * remains visible to a later reader.
     */
    val visibleDisplayFrame: Rect?,
    /** `WindowMetrics.getBounds()` for the same moment. */
    val windowBounds: Rect?,
    /**
     * The on-screen top of the card's own content: the text field's when one is attached, the root
     * view's otherwise. This is the reading round 3's keyboard displacement is derived from, because
     * it is the one that moved reproducibly under `ADJUST_RESIZE` in both round-2 runs.
     *
     * Null when neither view was attached — never zero, which would read as "the content is at the
     * very top of the screen".
     */
    val contentTopOnScreenPx: Int?,
    /** Which view [contentTopOnScreenPx] came from, or null when it could not be read at all. */
    val contentTopSource: ContentTopSource?,
    /** The orientation the sample was taken in, so a mixed-orientation run is readable afterwards. */
    val orientation: SampleOrientation?,
    /** `getLocationOnScreen` plus measured width/height of the text field, or null when none exists. */
    val fieldBoundsOnScreen: Rect?,
    /** The `LayoutParams.y` actually in effect on the attached window at this moment. */
    val layoutParamsY: Int?,
    /**
     * CONTROL ONLY. The bottom of the `ime()` inset if an inset dispatch was ever delivered to this
     * window, null if none ever was. This exists solely to confirm the known-bad behaviour
     * reproduces on the device under test. It never gates any other measurement.
     */
    val controlImeInsetBottomPx: Int?,
) {
    companion object {

        /**
         * Reads every signal directly off the live view hierarchy. Each read is independently
         * guarded: a root view that is not attached yields a null frame rather than a zeroed [Rect],
         * so "could not read" and "read as zero" stay distinguishable downstream.
         */
        fun capture(
            point: SamplePoint,
            elapsedMillisSinceWindowAdded: Long,
            rootView: View,
            field: View?,
            windowManager: WindowManager?,
            controlImeInsetBottomPx: Int?,
        ): KeyboardGeometrySample {
            val visibleFrame = if (rootView.isAttachedToWindow) {
                Rect().also(rootView::getWindowVisibleDisplayFrame)
            } else {
                null
            }

            val bounds = windowManager?.currentWindowMetrics?.bounds?.let(::Rect)

            val fieldBounds = field?.takeIf { it.isAttachedToWindow }?.let { view ->
                val location = IntArray(2).also(view::getLocationOnScreen)
                Rect(
                    location[0],
                    location[1],
                    location[0] + view.width,
                    location[1] + view.height,
                )
            }

            val paramsY = (rootView.layoutParams as? WindowManager.LayoutParams)?.y

            // The field is preferred because it is the view the keyboard actually displaces; the
            // root view is the honest fallback for a mode that has no field at all. When neither is
            // attached the top stays null, so "could not read the content top" never becomes a top
            // of zero.
            val contentTop: Pair<Int, ContentTopSource>? = when {
                fieldBounds != null -> fieldBounds.top to ContentTopSource.FIELD
                rootView.isAttachedToWindow ->
                    IntArray(2).also(rootView::getLocationOnScreen)[1] to ContentTopSource.ROOT_VIEW
                else -> null
            }

            return KeyboardGeometrySample(
                point = point,
                elapsedMillisSinceWindowAdded = elapsedMillisSinceWindowAdded,
                visibleDisplayFrame = visibleFrame,
                windowBounds = bounds,
                contentTopOnScreenPx = contentTop?.first,
                contentTopSource = contentTop?.second,
                orientation = SampleOrientation.ofBounds(bounds),
                fieldBoundsOnScreen = fieldBounds,
                layoutParamsY = paramsY,
                controlImeInsetBottomPx = controlImeInsetBottomPx,
            )
        }
    }
}

/**
 * What the raw samples support saying about the visible display frame, and nothing more.
 *
 * ROUND 3: this is a DESCRIPTION of the visible display frame's own behaviour, kept because round 2
 * showed that behaviour is itself the interesting evidence. It is not a keyboard-height source and
 * nothing acts on it — the pet is driven by the card's content displacement instead.
 *
 * "The visible display frame did not change" is a real, first-class outcome here — it is the
 * honest result on a window class that is never told about the keyboard — and it is explicitly not
 * the same value as "the keyboard is absent" or "coverage measured zero".
 */
sealed interface KeyboardGeometrySignal {

    /** Fewer than two comparable samples were taken; nothing can be compared. */
    data object NotEnoughSamples : KeyboardGeometrySignal

    /** A baseline and a keyboard-up sample exist but one of them had no readable frame. */
    data object FrameUnreadable : KeyboardGeometrySignal

    /**
     * Both frames were read and they are identical: this window class reports no keyboard geometry
     * at all. This is a measurement, not a missing one, and not a zero.
     */
    data object NoGeometrySignalOnThisWindowClass : KeyboardGeometrySignal

    /** The visible display frame's bottom moved by [bottomDeltaPx] pixels between the two samples. */
    data class VisibleFrameChanged(val bottomDeltaPx: Int) : KeyboardGeometrySignal

    fun describe(): String = when (this) {
        NotEnoughSamples -> "not measured (fewer than two comparable samples)"
        FrameUnreadable -> "not measured (a frame could not be read)"
        NoGeometrySignalOnThisWindowClass ->
            "no keyboard geometry signal available on this window class " +
                "(visible display frame identical before focus and with the keyboard up)"
        is VisibleFrameChanged ->
            "visible display frame bottom moved by $bottomDeltaPx px with the keyboard up"
    }

    companion object {
        /**
         * Compares the pre-focus baseline against the post-`showSoftInput` sample. Deliberately
         * derives nothing else: no keyboard height is inferred, because no signal in this window
         * class has been shown to carry one.
         */
        fun from(samples: List<KeyboardGeometrySample>): KeyboardGeometrySignal {
            val baseline = samples.firstOrNull { it.point == SamplePoint.BEFORE_FOCUS }
            val keyboardUp = samples.firstOrNull { it.point == SamplePoint.AFTER_SHOW_SOFT_INPUT }
            if (baseline == null || keyboardUp == null) return NotEnoughSamples

            val before = baseline.visibleDisplayFrame
            val after = keyboardUp.visibleDisplayFrame
            if (before == null || after == null) return FrameUnreadable

            val delta = after.bottom - before.bottom
            return if (delta == 0) {
                NoGeometrySignalOnThisWindowClass
            } else {
                VisibleFrameChanged(delta)
            }
        }
    }
}
