package com.yangzhiguo.mushroom.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mushroom ID design tokens. Aligned with `design_doc/prototype-design.md §1.1`.
 * Two rules from the doc:
 *  - Never use pure red/green (color-blind hostile + cheap feel).
 *  - No more than 5 colors per screen; status is encoded with icon + text, not color alone.
 */

// Brand
val ForestGreen = Color(0xFF2F6B4F)
val ForestGreenDark = Color(0xFF25563F)
val ForestGreenLight = Color(0xFFDCEBE2)
val EarthBrown = Color(0xFF3F657A)
val EarthBrownLight = Color(0xFFDCE8EE)
val MushroomTan = Color(0xFFA66A1F)
val MushroomTanLight = Color(0xFFF4E7D5)

// Surfaces
val Cream = Color(0xFFFFFFFF)
val CreamDark = Color(0xFF1B1E1C)
val OnSurfaceLight = Color(0xFF1F2421)
val OnSurfaceDark = Color(0xFFE8ECE9)
val OutlineLight = Color(0xFFE5E8E5)
val OutlineDark = Color(0xFF424743)

// Status (used in ToxicityBanner, OrderCard, SLA timer)
val ErrorRed = Color(0xFFB34237)
val ErrorRedLight = Color(0xFFF7E2DF)
val WarningAmber = Color(0xFFA66A1F)
val WarningAmberLight = Color(0xFFF4E7D5)
val SuccessGreen = Color(0xFF3F657A)
val SuccessGreenLight = Color(0xFFDCE8EE)
val NeutralGray = Color(0xFF6F7671)
val NeutralGrayLight = Color(0xFFE9ECE9)
