package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.ui.graphics.Color

/**
 * The three things the pet can acknowledge about something that just happened, rendered as a
 * one-second radial glow behind it (see `PetFeedbackGlow`).
 *
 * Kept to three deliberately. A signal the user has to decode is not a signal, and the overlay is
 * 220px with no room for text: whatever it says has to be readable at a glance, in peripheral
 * vision, while the user is doing something else. Three colours is near the limit of what that
 * allows.
 *
 * Choosing between them is a statement about *who* is responsible, not about how unusual the event
 * was:
 *
 * - [SUCCESS] — something the user did worked.
 * - [WARNING] — the pet adjusted something on its own. It still worked; the result is just not
 *   exactly what was asked for.
 * - [ERROR] — something failed and the user's intent was not carried out.
 *
 * Reaching for [ERROR] when the pet quietly corrected itself would blame the user for the app's
 * own housekeeping, which the project's cross-cutting rules forbid.
 */
enum class PetFeedback {
    SUCCESS,
    WARNING,
    ERROR,
    ;

    /**
     * Colour is not read alone: the glow is behind a pet that is itself animating, so hue is the
     * only channel available. These are the conventional three and are deliberately not themed —
     * the overlay draws over arbitrary apps, so it cannot inherit a surface colour to contrast
     * against.
     */
    val color: Color
        get() = when (this) {
            SUCCESS -> Color(0xFF2E7D32)
            WARNING -> Color(0xFFF9A825)
            ERROR -> Color(0xFFC62828)
        }
}
