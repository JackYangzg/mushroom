package com.yangzhiguo.mushroom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * M3 doesn't model "success" or "warning" as first-class role colors.
 * Per `design_doc §1.1` the app needs:
 *  - 剧毒 (deadly)  → error role
 *  - 有毒 (toxic)   → warning role (amber)
 *  - 可食 (edible)  → success role (green)
 *  - 不可食 (inedible) → neutral role (gray)
 * We expose them through a CompositionLocal so screens can read `MaterialTheme.extended.warning`
 * without thinking about it. Each role provides a `container` (background) + `onContainer` (text) pair.
 */
@Immutable
data class MushroomExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutral: Color,
    val onNeutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

val LocalExtendedColors = compositionLocalOf {
    // Defaults are light-mode tokens; Theme.kt will override via provider.
    MushroomExtendedColors(
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
}

/** Ergonomic access: `MaterialTheme.extended.warning` */
val MaterialTheme.extended: MushroomExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
