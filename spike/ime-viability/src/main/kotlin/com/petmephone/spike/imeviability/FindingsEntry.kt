package com.petmephone.spike.imeviability

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

/**
 * One run's complete, self-contained record: automatic measurements plus the two human-answered
 * questions decision 14/14a require, never left blank.
 */
data class FindingsEntry(
    val timestamp: LocalDateTime,
    val mode: SpikeMode,
    val device: DeviceInfo,
    val keyboardAppeared: Boolean?,
    val keyboardCoversField: Boolean?,
    val imeInsetCallbackFired: Boolean?,
    val windowEverReceivedFocus: Boolean,
    val windowRemovedCleanly: Boolean,
    val videoPausedOnFocus: HumanAnswer,
    val focusReturnedAfterDismissal: HumanAnswer,
) {
    fun toMarkdown(): String {
        val sb = StringBuilder()
        val ts = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        sb.appendLine("## Run: $ts — Mode: ${mode.label}")
        sb.appendLine(
            "- Device: ${device.manufacturer} ${device.model}, Android ${device.androidRelease} " +
                "(API ${device.apiLevel})",
        )
        sb.appendLine("- Keyboard appeared: ${formatTri(keyboardAppeared, mode == SpikeMode.FULL_IME)}")
        sb.appendLine(
            "- Keyboard covers field: ${formatTri(keyboardCoversField, mode == SpikeMode.FULL_IME)}",
        )
        sb.appendLine(
            "- IME inset callback fired (without relying on imePadding()): " +
                formatTri(imeInsetCallbackFired, mode == SpikeMode.FULL_IME),
        )
        sb.appendLine("- Window ever received focus (observed via onWindowFocusChanged): $windowEverReceivedFocus")
        sb.appendLine("- Window removed cleanly (no leaked focusable state): $windowRemovedCleanly")
        sb.appendLine("- Video paused when window took focus (human): ${videoPausedOnFocus.label}")
        sb.appendLine(
            "- Focus returned to the app underneath after dismissal (human): " +
                focusReturnedAfterDismissal.label,
        )
        sb.appendLine()
        return sb.toString()
    }

    private fun formatTri(value: Boolean?, applicable: Boolean): String {
        if (!applicable) return "N/A (${SpikeMode.FOCUS_ONLY.label} mode raises no keyboard)"
        return value?.toString() ?: "not captured"
    }
}
