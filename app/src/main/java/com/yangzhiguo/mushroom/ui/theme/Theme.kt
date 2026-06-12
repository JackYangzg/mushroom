package com.yangzhiguo.mushroom.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Mushroom ID theme. Notes vs the M3 default:
 *  - `dynamicColor` is forced off — on Android 12+ M3 would otherwise pull wallpaper colors
 *    and override our forest-green brand.
 *  - Status colors (success / warning / neutral) are provided through `MushroomExtendedColors`
 *    and exposed via `MaterialTheme.extended.*` (see [ExtendedColors.kt]).
 */

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = ForestGreenLight,
    onPrimaryContainer = Color(0xFF0A2E15),
    secondary = EarthBrown,
    onSecondary = Color.White,
    secondaryContainer = EarthBrownLight,
    onSecondaryContainer = Color(0xFF3D2510),
    tertiary = MushroomTan,
    onTertiary = Color.White,
    tertiaryContainer = MushroomTanLight,
    onTertiaryContainer = Color(0xFF4A2E10),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedLight,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F1E8),
    onBackground = OnSurfaceLight,
    surface = Cream,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFF1ECE0),
    onSurfaceVariant = Color(0xFF555555),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE0DDD5),
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color(0xFF0A2E15),
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = Color(0xFFB7E4C7),
    secondary = EarthBrownLight,
    onSecondary = Color(0xFF3D2510),
    secondaryContainer = Color(0xFF5A3B1F),
    onSecondaryContainer = Color(0xFFDDC4A8),
    tertiary = MushroomTanLight,
    onTertiary = Color(0xFF4A2E10),
    tertiaryContainer = Color(0xFF6E4A1F),
    onTertiaryContainer = Color(0xFFF2DDC2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = CreamDark,
    onBackground = OnSurfaceDark,
    surface = CreamDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF2A2E29),
    onSurfaceVariant = Color(0xFFC4C0B8),
    outline = OutlineDark,
    outlineVariant = Color(0xFF3A3A3A),
)

private val LightExtended = MushroomExtendedColors(
    success = SuccessGreen,
    onSuccess = Color.White,
    successContainer = SuccessGreenLight,
    onSuccessContainer = Color(0xFF0A3D1F),
    warning = WarningAmber,
    onWarning = Color.White,
    warningContainer = WarningAmberLight,
    onWarningContainer = Color(0xFF5C3B00),
    neutral = NeutralGray,
    onNeutral = Color.White,
    neutralContainer = NeutralGrayLight,
    onNeutralContainer = Color(0xFF333333),
)

private val DarkExtended = MushroomExtendedColors(
    success = Color(0xFF6BBF85),
    onSuccess = Color(0xFF003918),
    successContainer = Color(0xFF1F4D2D),
    onSuccessContainer = Color(0xFFCFEFD9),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF402300),
    warningContainer = Color(0xFF5C3B00),
    onWarningContainer = Color(0xFFFFE4B5),
    neutral = Color(0xFFB8BCC2),
    onNeutral = Color(0xFF1B1B1B),
    neutralContainer = Color(0xFF3A3D42),
    onNeutralContainer = Color(0xFFE2E2E2),
)

@Composable
fun MushroomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false, // forced off per design
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
