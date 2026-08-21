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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gcatcode.petmephone.core.domain.pet.sprite.AnimationPacing
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * Draws the resolved [PetState]'s animation over other apps, falling back to IDLE when the active
 * character has no file for that state, or the visibly-broken placeholder shape when the active
 * character itself fails to decode. Consumes an `@Inject`ed [PetOverlayStateHolder] — never
 * `hiltViewModel()`.
 *
 * A `Loading` emission (mid-switch) never blanks the screen or flashes the broken placeholder
 * while a prior `Ready` value exists: the last `Ready` sheets stay on screen, keyed by their own
 * identity, until the new character resolves to `Ready` or `Broken` (`[RENDER-3]`).
 */
@Composable
fun PetOverlay(holder: PetOverlayStateHolder) {
    // Declared first, so it draws underneath: the glow is a backdrop the pet stands in front of,
    // never a wash over its face. Compose draws siblings in declaration order.
    PetFeedbackGlow(holder.feedback)

    val sheets by holder.sheets.collectAsState()
    val petState by holder.petState.collectAsState()

    // Only a `Ready` value is ever remembered — a `Loading`/`Broken` emission never overwrites a
    // prior good frame, so the switch never blanks or flashes the broken placeholder while the
    // previous character is still the last known-good render.
    var lastReady by remember { mutableStateOf<CharacterSheets.Ready?>(null) }
    val currentSheets = sheets
    if (currentSheets is CharacterSheets.Ready) {
        lastReady = currentSheets
    }

    // Sealed `when` with no `else`: a missing branch fails to compile rather than silently
    // falling through to a blank render.
    when (currentSheets) {
        is CharacterSheets.Ready -> ReadyPet(currentSheets, petState, holder)
        is CharacterSheets.Loading -> lastReady?.let { ReadyPet(it, petState, holder) }
        is CharacterSheets.Broken -> BrokenPlaceholder()
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

/**
 * Draws [petState]'s bound animation from [ready] — falling back to `ready.idle` when the active
 * character has no file for [petState] (`[RENDER-1]`).
 *
 * `frameIndex` is `remember`ed keyed on [ready] itself, not on the selected animation: a character
 * switch (a new [CharacterSheets.Ready] instance) resets it to zero so a stale index never reaches
 * past the new sheet's row count (`[RENDER-3]`); a state change on the *same* character
 * deliberately does not reset it, per `design.md`. The draw call still takes `frameIndex %
 * layout.frameCount` as a defensive bound, since two states on one character can have different
 * frame counts.
 *
 * [onFrameAdvance] is a test-only observation hook (default no-op, so every production call site
 * is unaffected): `frameIndex` itself is a draw-phase-only `MutableIntState` with nothing to poll
 * from outside the composition, and this project's JVM Compose tests cannot capture real pixels
 * (Robolectric never runs the hardware draw path), so it is the only way to prove — without a
 * physical device — that the animation clock is still advancing after a character switch.
 */
@Composable
internal fun ReadyPet(
    ready: CharacterSheets.Ready,
    petState: PetState,
    holder: PetOverlayStateHolder,
    onFrameAdvance: (CharacterSheets.Ready, Int) -> Unit = { _, _ -> },
) {
    val loaded = ready.byState[petState] ?: ready.idle
    val layout = loaded.layout
    val bitmap = loaded.bitmap

    // `mutableIntStateOf` (Int specialisation, no boxing) read only inside the draw lambda, never
    // in composition — recomposition never re-runs after the first frame. Keyed on `ready`'s own
    // identity so a switch resets the index instead of indexing into the new sheet with a stale
    // value left over from the previous character.
    var frameIndex by remember(ready) { mutableIntStateOf(0) }

    // Collected into Compose state rather than read as `holder.screenOn.value` inside the draw
    // lambda. A plain StateFlow read is invisible to Compose: nothing invalidates the draw phase
    // when it flips, so the pet vanished at screen-off and never came back — the window stayed,
    // empty, until the process died.
    //
    // Held as the State object and read only inside `drawBehind`, never unwrapped here, so the
    // read is scoped to the draw phase: the screen toggling redraws without recomposing or
    // re-laying out.
    val screenOnState = holder.screenOn.collectAsState()

    // Collected the same way as `screenOnState` above: `holder.config` is now an observable
    // `StateFlow` (design decision 6), and a plain read would be invisible to Compose since the
    // `StateFlow` reference itself never changes identity when a field is written.
    val animationConfigState = holder.config.collectAsState()

    // Keyed on `ready` (not just `layout`) so the clock restarts in lockstep with the
    // `remember(ready)` above: `SpriteLayout` is a data class with value equality, so a switch
    // to a character whose sheet happens to have the same grid/frameCount left this key
    // unchanged and the effect never relaunched — the old coroutine kept incrementing the
    // abandoned `MutableIntState` from the previous `remember`, while the draw lambda read the
    // new one, pinned at 0 forever. `layout` and `holder.config` stay in the key set: a state
    // change on the *same* character still legitimately changes `layout` and must still restart
    // the clock.
    val frameIntervalMillis = AnimationPacing.frameDurationMillis(
        declaredFrameDurationMillis = ready.frameDurationMillis,
        defaultFrameDurationMillis = animationConfigState.value.frameIntervalMillis,
        minFrameDurationMillis = animationConfigState.value.minFrameIntervalMillis,
    )

    LaunchedEffect(ready, layout, animationConfigState.value) {
        holder.screenOn.collectLatest { on ->
            if (!on) return@collectLatest // true suspension: the loop is cancelled, not slowed.
            while (isActive) {
                kotlinx.coroutines.delay(frameIntervalMillis)
                frameIndex = (frameIndex + 1) % layout.frameCount
                onFrameAdvance(ready, frameIndex)
            }
        }
    }

    val cellSizePx = layout.grid.cellSizePx
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(READY_PET_TEST_TAG)
            .drawBehind {
                // PR 0 finding (3): draw frames keep firing with the screen off, so this lambda
                // itself gates on the same screen-on signal the clock collects, rather than
                // relying on the clock alone to stop draw-attributable work.
                if (!screenOnState.value) return@drawBehind
                // Defensive modulo: a state switch on the same character can move to a layout with
                // a different (smaller) frame count without resetting the remembered index above.
                val boundedFrameIndex = frameIndex % layout.frameCount
                val left = layout.cellLeftPx(boundedFrameIndex)
                val top = layout.cellTopPx(boundedFrameIndex)

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

                // Drawn last, in this same `DrawScope`, after `drawImage` — no imported pixel can
                // ever paint over it (character-import's "persistent identity affordance" +
                // `design.md`'s explicit ordering requirement). A small, fixed, non-spoofable
                // corner badge: its shape and color come from code, never from imported content.
                drawIdentityAffordance()
            },
    ) {}
}

/**
 * The persistent identity affordance every character renders with, regardless of its own art —
 * a small solid badge in the top-left corner. Drawn from fixed shapes only, never from decoded
 * pixels, so an imported sheet cannot spoof or obscure it.
 */
private fun DrawScope.drawIdentityAffordance() {
    val radius = IDENTITY_BADGE_RADIUS_PX
    val center = Offset(radius + IDENTITY_BADGE_MARGIN_PX, radius + IDENTITY_BADGE_MARGIN_PX)
    drawCircle(color = Color.White, radius = radius, center = center)
    drawCircle(
        color = Color.Black,
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
    )
}

private const val IDENTITY_BADGE_RADIUS_PX = 10f
private const val IDENTITY_BADGE_MARGIN_PX = 6f

/**
 * Programmatically-drawn broken shape — never decoded from an asset, so it cannot itself fail to
 * decode the way a corrupt sprite sheet can (see `pet-overlay-rendering` spec). Internal, not
 * private, so instrumented tests can render it directly without going through the full Hilt
 * graph `PetOverlayStateHolder` requires.
 */
@Composable
internal fun BrokenPlaceholder() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(BROKEN_PLACEHOLDER_TEST_TAG)
            .drawBehind {
                drawBrokenShape()
                // The affordance is "persistent, regardless of the active character"
                // (`[IMPORT-15]`), and a character whose sheet failed to decode is still the active
                // character. Omitting it here made the badge conditional on a successful decode,
                // which is the one case where the user most needs to know what they are looking at.
                drawIdentityAffordance()
            },
    ) {}
}

/** Structural hooks for Robolectric tests, which cannot assert drawn pixels the way an
 * instrumented `captureToImage` pass can — see `PetOverlayTest`. */
internal const val READY_PET_TEST_TAG = "pet-overlay-ready"
internal const val BROKEN_PLACEHOLDER_TEST_TAG = "pet-overlay-broken"

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
