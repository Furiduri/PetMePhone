package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.feature.overlay.R

/**
 * One labelled metric row — a filled bar for [MetricReading.Available], a hatched (never empty,
 * never a spinner) bar for [MetricReading.Unavailable], and a plain text state for
 * [MetricReading.Loading] (transient, only ever true for Hunger before its first emission).
 *
 * `overlay-metric-display`'s "absence never renders as zero" requirement is why [Unavailable]
 * gets its own visually-distinct hatched track plus an explicit label, rather than reusing the
 * filled bar at `0%` — a `0%` filled bar reads as "empty", which is a claim about a real value the
 * app has not earned (this project's standing `absence-never-renders-as-zero` decision).
 *
 * Not itself an interactive element (no click action, no touch target requirement) — only the
 * card's launch button is. [Text] composables are accessible by default via their own text
 * semantics, so no explicit `contentDescription` is added here.
 */
@Composable
fun MetricRow(label: String, reading: MetricReading, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        when (reading) {
            is MetricReading.Available -> {
                LinearProgressIndicator(
                    progress = { reading.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(BAR_CORNER_RADIUS_DP.dp)),
                )
                Text("${reading.percent}%", style = MaterialTheme.typography.bodySmall)
            }

            MetricReading.Unavailable -> {
                HatchedTrack(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT_DP.dp))
                Text(
                    stringResource(R.string.feature_overlay_quickmenu_metric_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            MetricReading.Loading -> {
                HatchedTrack(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT_DP.dp))
                Text(
                    stringResource(R.string.feature_overlay_quickmenu_metric_loading),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * A diagonal-stripe track, visually distinct from both an empty bar and a filled one — never an
 * indeterminate spinner (`overlay-metric-display`'s explicit "MUST NOT render as a spinner"
 * requirement for [MetricReading.Unavailable]). A static [Brush.linearGradient] draws real, fixed
 * content on every frame — nothing here animates or promises an imminent value.
 */
@Composable
private fun HatchedTrack(modifier: Modifier = Modifier) {
    val stripeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(modifier = modifier.clip(RoundedCornerShape(BAR_CORNER_RADIUS_DP.dp))) {
        drawHatchedTrack(trackColor = trackColor, stripeColor = stripeColor)
    }
}

private fun DrawScope.drawHatchedTrack(trackColor: Color, stripeColor: Color) {
    drawRect(color = trackColor)
    val stripeWidthPx = HATCH_STRIPE_WIDTH_DP.dp.toPx()
    var startX = -size.height
    while (startX < size.width) {
        drawLine(
            color = stripeColor,
            start = Offset(startX, size.height),
            end = Offset(startX + size.height, 0f),
            strokeWidth = stripeWidthPx,
        )
        startX += stripeWidthPx * HATCH_STRIPE_SPACING_MULTIPLIER
    }
}

private const val BAR_HEIGHT_DP = 8
private const val BAR_CORNER_RADIUS_DP = 4
private const val HATCH_STRIPE_WIDTH_DP = 4
private const val HATCH_STRIPE_SPACING_MULTIPLIER = 3
