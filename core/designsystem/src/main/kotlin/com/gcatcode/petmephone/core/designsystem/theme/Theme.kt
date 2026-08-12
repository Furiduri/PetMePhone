package com.gcatcode.petmephone.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The app's colours, in one place.
 *
 * Every screen reads its colours from here rather than naming them inline, so changing the app's
 * palette is an edit to this file and nothing else — the same reason balance values are injected
 * rather than written into the code that uses them.
 *
 * Green because the pet is a small living thing sharing the screen with whatever else you are
 * doing; the greens below are warm and slightly desaturated rather than clinical, so the overlay
 * reads as friendly in peripheral vision instead of as a system alert.
 */
private val LightGreen = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EFC9),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D6),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF7FBF4),
    onBackground = Color(0xFF191D19),
    surface = Color(0xFFF7FBF4),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFDBE5DB),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717971),
)

private val DarkGreen = darkColorScheme(
    primary = Color(0xFF99D5AF),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF10512F),
    onPrimaryContainer = Color(0xFFB4EFC9),
    secondary = Color(0xFFB5CCBB),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD0E8D6),
    tertiary = Color(0xFFA3CEDC),
    onTertiary = Color(0xFF033542),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBEEAF8),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8B938B),
)

/**
 * [dynamicColor] lets Android 12 and up derive the palette from the user's wallpaper instead. It is
 * off by default: the green above is the app's identity, and a wallpaper-derived scheme would
 * replace it with whatever the phone happens to be wearing. Turning it on is a one-argument change
 * if that is ever what you want.
 */
@Composable
fun PetMePhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkGreen
        else -> LightGreen
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
