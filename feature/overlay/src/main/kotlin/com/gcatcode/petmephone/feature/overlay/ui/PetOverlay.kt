package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * Draws the pet's IDLE row over other apps, or the visibly-broken placeholder shape if the sheet
 * failed to decode. Consumes an `@Inject`ed [PetOverlayStateHolder] — never `hiltViewModel()`.
 */
@Composable
fun PetOverlay(holder: PetOverlayStateHolder) {
    // Declared first, so it draws underneath: the glow is a backdrop the pet stands in front of,
    // never a wash over its face. Compose draws siblings in declaration order.
    PetFeedbackGlow(holder.feedback)

    // Sealed `when` with no `else`: a missing branch fails to compile rather than silently
    // falling through to a blank render.
    when (val result = holder.sheetResult) {
        is SpriteSheetResult.Loaded -> IdlePet(result, holder)
        is SpriteSheetResult.Failed -> BrokenPlaceholder()
    }
}

/**
 * A one-second radial glow behind the pet, in the colour of whatever [PetFeedback] arrives.
 *
 * `collectLatest` rather than `collect`: a second signal during the fade restarts the glow at full
 * strength instead of queueing behind the first, so the pet always reflects the most recent thing
 * that happened rather than replaying history.
 *
 * The glow is a backdrop, so it is drawn behind the sprite and sized to the window's shorter side.
 * It never grows beyond the pet's own bounds — an overlay that painted outside them would be
 * drawing on top of another app's UI, which the overlay contract forbids.
 */
@Composable
private fun PetFeedbackGlow(feedback: Flow<PetFeedback>) {
    val alpha = remember { Animatable(0f) }
    var color by remember { mutableStateOf(PetFeedback.WARNING.color) }

    LaunchedEffect(feedback) {
        feedback.collectLatest { signal ->
            color = signal.color
            alpha.snapTo(PEAK_GLOW_ALPHA)
            alpha.animateTo(0f, animationSpec = tween(durationMillis = GLOW_DURATION_MILLIS))
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val current = alpha.value
                if (current <= 0f) return@drawBehind // fully faded: no brush allocated at all
                val radius = minOf(size.width, size.height) / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        // Opaque at the centre, fully transparent at the rim, so the glow has no
                        // hard edge to read as a shape of its own.
                        colors = listOf(color.copy(alpha = current), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
    ) {}
}

private const val GLOW_DURATION_MILLIS = 1_000
private const val PEAK_GLOW_ALPHA = 0.55f

@Composable
private fun IdlePet(loaded: SpriteSheetResult.Loaded, holder: PetOverlayStateHolder) {
    val layout = loaded.layout
    val bitmap = loaded.bitmap

    // `mutableIntStateOf` (Int specialisation, no boxing) read only inside the draw lambda, never
    // in composition — recomposition never re-runs after the first frame.
    var frameIndex by remember { mutableIntStateOf(0) }

    // Collected into Compose state rather than read as `holder.screenOn.value` inside the draw
    // lambda. A plain StateFlow read is invisible to Compose: nothing invalidates the draw phase
    // when it flips, so the pet vanished at screen-off and never came back — the window stayed,
    // empty, until the process died.
    //
    // Held as the State object and read only inside `drawBehind`, never unwrapped here, so the
    // read is scoped to the draw phase: the screen toggling redraws without recomposing or
    // re-laying out.
    val screenOnState = holder.screenOn.collectAsState()

    LaunchedEffect(layout, holder.config) {
        holder.screenOn.collectLatest { on ->
            if (!on) return@collectLatest // true suspension: the loop is cancelled, not slowed.
            while (isActive) {
                kotlinx.coroutines.delay(holder.config.frameIntervalMillis)
                frameIndex = (frameIndex + 1) % layout.frameCount
            }
        }
    }

    val cellSizePx = layout.grid.cellSizePx
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // PR 0 finding (3): draw frames keep firing with the screen off, so this lambda
                // itself gates on the same screen-on signal the clock collects, rather than
                // relying on the clock alone to stop draw-attributable work.
                if (!screenOnState.value) return@drawBehind
                val left = layout.cellLeftPx(frameIndex)
                val top = 0 // One sheet is one row: the cell's top edge is always the image's top.

                // The cell is scaled up to fill the window rather than blitted at its native size.
                // A 32x32 cell is a legitimate pixel-art resolution, not a small pet: the sheet's
                // resolution decides how much detail the artist has, never how large the pet looks.
                // Drawing 1:1 made a 32px character a speck inside a 220px window.
                //
                // The cell is square by contract, so the window's shorter side sets the drawn size
                // and the result is centred. That keeps the pet undistorted if the window ever
                // stops being square.
                val side = minOf(size.width, size.height).toInt()
                val dstX = (size.width.toInt() - side) / 2
                val dstY = (size.height.toInt() - side) / 2

                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(left, top),
                    srcSize = IntSize(cellSizePx, cellSizePx),
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(side, side),
                    // Nearest-neighbour. Pixel art magnified with the default bilinear filter turns
                    // into a blur, which is the one thing an upscaled sprite must never do: the
                    // hard pixel edges ARE the art style, not an artefact of low resolution.
                    filterQuality = FilterQuality.None,
                )
            },
    ) {}
}

/**
 * Programmatically-drawn broken shape — never decoded from an asset, so it cannot itself fail to
 * decode the way a corrupt sprite sheet can (see `pet-overlay-rendering` spec). Internal, not
 * private, so instrumented tests can render it directly without going through the full Hilt
 * graph `PetOverlayStateHolder` requires.
 */
@Composable
internal fun BrokenPlaceholder() {
    Canvas(modifier = Modifier.fillMaxSize().drawBehind { drawBrokenShape() }) {}
}

private fun DrawScope.drawBrokenShape() {
    val strokeWidth = 6f
    drawRect(color = Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))
    drawLine(
        color = Color.Red,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.Red,
        start = Offset(size.width, 0f),
        end = Offset(0f, size.height),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}
