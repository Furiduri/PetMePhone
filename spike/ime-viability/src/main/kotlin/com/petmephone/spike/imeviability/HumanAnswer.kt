package com.petmephone.spike.imeviability

/**
 * A cross-app observation the spike cannot measure itself (video playback state, whether the app
 * underneath visibly regained input). Never assumed and never left blank — the maintainer is
 * always prompted with an explicit question and must pick one of these three answers.
 */
enum class HumanAnswer(val label: String) {
    YES("Yes"),
    NO("No"),
    NOT_TESTED("Not tested"),
}
