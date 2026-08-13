package com.petmephone.spike.imeviability

import android.graphics.Rect
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

/**
 * One run's complete, self-contained record: raw geometry samples plus the human-answered questions
 * the instrument cannot answer for itself, never left blank.
 *
 * The previous version of this type carried `keyboardAppeared` and `keyboardCoversField`, both
 * derived from an `ime()` inset that a `TYPE_APPLICATION_OVERLAY` window never receives. Both are
 * gone rather than fixed in place: the first recorded `false` on seven runs while the keyboard was
 * demonstrably in use, and the second was only ever computed inside `if (keyboardAppeared)`, so its
 * recorded value was an untouched default rather than a measurement. Their replacements are the raw
 * [samples] below, plus [keyboardAppeared] and [fieldVisibility] answered by the human who watched
 * the screen.
 */
data class FindingsEntry(
    val timestamp: LocalDateTime,
    val mode: SpikeMode,
    val device: DeviceInfo,
    /** The `LayoutParams.y` the window was added at, or null if the bounds could not be read. */
    val startYPx: Int?,
    val samples: List<KeyboardGeometrySample>,
    val geometrySignal: KeyboardGeometrySignal,
    /**
     * CONTROL ONLY: whether any inset dispatch reached the window at all. Kept to confirm the
     * known-bad behaviour reproduces on this device. It gates nothing.
     */
    val controlImeInsetDispatchFired: Boolean,
    val windowEverReceivedFocus: Boolean,
    val windowRemovedCleanly: Boolean,
    /** Whether layout-driven sampling was truncated by its cap. See `MAX_LAYOUT_DRIVEN_SAMPLES`. */
    val layoutDrivenSampleCapReached: Boolean,
    /** What the run could say about moving a second, non-focusable window by a measured height. */
    val petFollow: PetFollowRecord,
    val keyboardAppeared: HumanAnswer,
    val fieldVisibility: FieldVisibility,
    val cardMovedOnFocus: HumanAnswer,
    val placementAcceptable: HumanAnswer,
    val videoPausedOnFocus: HumanAnswer,
    val focusReturnedAfterDismissal: HumanAnswer,
    val petVisibleAboveKeyboard: HumanAnswer,
    val petMovementQuality: PetMovementQuality,
    val petReturnedToOriginalPosition: HumanAnswer,
) {
    fun toMarkdown(): String {
        val sb = StringBuilder()
        val ts = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        sb.appendLine("## Run: $ts — Mode: ${mode.label}")
        sb.appendLine(
            "- Device: ${device.manufacturer} ${device.model}, Android ${device.androidRelease} " +
                "(API ${device.apiLevel})",
        )
        sb.appendLine("- Strategy: softInputMode ${mode.softInputModeLabel()}, " +
            "repositions on focus: ${mode.repositionsOnFocus}")
        sb.appendLine("- Start LayoutParams.y: ${startYPx?.toString() ?: NOT_MEASURED}")
        sb.appendLine(
            "- CONTROL — visible display frame signal: ${geometrySignal.describe()} " +
                "(round 2 showed this frame reports the resize on some runs and not others, so it " +
                "is recorded as evidence only and drives nothing)",
        )
        sb.appendLine(
            "- CONTROL — any inset dispatch reached the window: $controlImeInsetDispatchFired " +
                "(known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)",
        )
        sb.appendLine("- Window ever received focus (observed via onWindowFocusChanged): $windowEverReceivedFocus")
        sb.appendLine("- Window removed cleanly (no leaked focusable state): $windowRemovedCleanly")
        sb.appendLine("- Keyboard appeared at all (human): ${keyboardAppeared.label}")
        sb.appendLine("- Text field visibility while typing (human): ${fieldVisibility.label}")
        sb.appendLine("- Card visibly jumped or moved when the field was focused (human): ${cardMovedOnFocus.label}")
        sb.appendLine("- Resulting placement acceptable to use (human): ${placementAcceptable.label}")
        sb.appendLine("- Video paused when window took focus (human): ${videoPausedOnFocus.label}")
        sb.appendLine(
            "- Focus returned to the app underneath after dismissal (human): " +
                focusReturnedAfterDismissal.label,
        )
        sb.appendLine("- Pet visible above the keyboard while typing (human): ${petVisibleAboveKeyboard.label}")
        sb.appendLine("- Pet movement quality (human): ${petMovementQuality.label}")
        sb.appendLine(
            "- Pet returned to its original position after the keyboard closed (human): " +
                petReturnedToOriginalPosition.label,
        )
        sb.appendLine("- Pet-follow: ${petFollow.outcome.describe()}")
        sb.appendLine("- Pet start LayoutParams.y: ${petFollow.originalYPx?.toString() ?: NOT_MEASURED}")
        sb.appendLine(
            "- Pet restored to its original y (observed): " +
                (
                    petFollow.restoredToOriginalY?.toString()
                        ?: "$NOT_MEASURED (the pet never moved, so there was nothing to restore)"
                    ),
        )
        if (petFollow.moves.isEmpty()) {
            sb.appendLine("- Pet moves: none (no move was made)")
        } else {
            sb.appendLine("- Pet moves:")
            sb.appendLine("  | +ms | measured displacement px | moved to y |")
            sb.appendLine("  |---|---|---|")
            petFollow.moves.forEach { move ->
                sb.appendLine(
                    "  | ${move.elapsedMillisSinceWindowAdded} | ${move.measuredDisplacementPx} | " +
                        "${move.movedToYPx} |",
                )
            }
        }
        if (petFollow.baselineResets.isEmpty()) {
            sb.appendLine("- Displacement baseline resets: none (the baseline held for the whole run)")
        } else {
            sb.appendLine("- Displacement baseline resets:")
            sb.appendLine("  | +ms | cause |")
            sb.appendLine("  |---|---|")
            petFollow.baselineResets.forEach { reset ->
                sb.appendLine("  | ${reset.elapsedMillisSinceWindowAdded} | ${reset.cause} |")
            }
        }
        if (petFollow.observations.isEmpty()) {
            sb.appendLine("- Content displacement observations: none (no content top was readable)")
        } else {
            sb.appendLine(
                "- Content displacement observations (EVERY observation, including ones that never " +
                    "agreed with the previous sample and therefore never caused a move):",
            )
            sb.appendLine("  | +ms | displacement px | agreed with previous | caused a pet move |")
            sb.appendLine("  |---|---|---|---|")
            petFollow.observations.forEach { observation ->
                sb.appendLine(
                    "  | ${observation.elapsedMillisSinceWindowAdded} | " +
                        "${observation.displacementPx} | " +
                        "${observation.agreedWithPreviousObservation} | " +
                        "${observation.causedPetMove} |",
                )
            }
        }
        sb.appendLine(
            "- Displacement observation recording hit its cap: ${petFollow.observationsTruncated} " +
                "(true means the observation table above is TRUNCATED; the follow behaviour kept " +
                "running regardless)",
        )
        sb.appendLine(
            "- Layout-driven sampling hit its cap: $layoutDrivenSampleCapReached " +
                "(true means the raw series below is TRUNCATED, not that it stopped changing, and " +
                "not that the pet stopped following — the cap bounds recording only)",
        )
        sb.appendLine("- Raw samples:")
        if (samples.isEmpty()) {
            sb.appendLine("  - $NOT_MEASURED (no sample was taken)")
        } else {
            sb.appendLine(
                "  | Point | +ms | orientation | contentTopOnScreen | CONTROL visibleDisplayFrame | " +
                    "windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |",
            )
            sb.appendLine("  |---|---|---|---|---|---|---|---|---|")
            samples.forEach { sample ->
                sb.appendLine(
                    "  | ${sample.point.label} | ${sample.elapsedMillisSinceWindowAdded} | " +
                        "${sample.orientation?.label ?: NOT_MEASURED} | " +
                        "${formatContentTop(sample)} | " +
                        "${formatRect(sample.visibleDisplayFrame)} | " +
                        "${formatRect(sample.windowBounds)} | " +
                        "${formatRect(sample.fieldBoundsOnScreen)} | " +
                        "${sample.layoutParamsY?.toString() ?: NOT_MEASURED} | " +
                        "${sample.controlImeInsetBottomPx?.toString() ?: NOT_MEASURED} |",
                )
            }
        }
        sb.appendLine()
        return sb.toString()
    }

    /**
     * A missing reading renders as an explicit "not measured". It never renders as `0,0,0,0`, which
     * would read like a real rectangle that happened to be empty.
     */
    private fun formatRect(rect: Rect?): String =
        rect?.let { "[${it.left},${it.top},${it.right},${it.bottom}]" } ?: NOT_MEASURED

    /**
     * The content top with the view it came from, so two rows read off different views are never
     * silently compared. An unreadable top renders as "not measured", never as `0`.
     */
    private fun formatContentTop(sample: KeyboardGeometrySample): String {
        val top = sample.contentTopOnScreenPx ?: return NOT_MEASURED
        val source = sample.contentTopSource?.label ?: NOT_MEASURED
        return "$top ($source)"
    }

    private companion object {
        const val NOT_MEASURED = "not measured"
    }
}
