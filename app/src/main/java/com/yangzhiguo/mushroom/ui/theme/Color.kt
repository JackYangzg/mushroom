package com.yangzhiguo.mushroom.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mushroom ID design tokens. Aligned with `design_doc/prototype-design.md §1.1`.
 * Two rules from the doc:
 *  - Never use pure red/green (color-blind hostile + cheap feel).
 *  - No more than 5 colors per screen; status is encoded with icon + text, not color alone.
 */

// Brand
val ForestGreen = Color(0xFF2E5D3A)            // primary
val ForestGreenDark = Color(0xFF1F4028)        // dark scheme primary
val ForestGreenLight = Color(0xFFB7E4C7)       // primary container / selected state
val EarthBrown = Color(0xFF8B5A2B)             // secondary
val EarthBrownLight = Color(0xFFDDC4A8)        // secondary container
val MushroomTan = Color(0xFFD4A574)            // tertiary
val MushroomTanLight = Color(0xFFF2DDC2)       // tertiary container

// Surfaces
val Cream = Color(0xFFFAF8F3)                  // surface (light)
val CreamDark = Color(0xFF1B1F1A)              // surface (dark)
val OnSurfaceLight = Color(0xFF1B1B1B)         // body text
val OnSurfaceDark = Color(0xFFEDEAE2)          // body text on dark
val OutlineLight = Color(0xFFCFCFCF)
val OutlineDark = Color(0xFF555555)

// Status (used in ToxicityBanner, OrderCard, SLA timer)
val ErrorRed = Color(0xFFB3261E)               // 剧毒 (deadly)
val ErrorRedLight = Color(0xFFFFDAD6)
val WarningAmber = Color(0xFFE8A33D)            // 有毒 / 待处理
val WarningAmberLight = Color(0xFFFFE4B5)
val SuccessGreen = Color(0xFF3D8B5A)           // 可食 / 已鉴别
val SuccessGreenLight = Color(0xFFCFEFD9)
val NeutralGray = Color(0xFF9AA0A6)            // 不可食 / 中性
val NeutralGrayLight = Color(0xFFE2E2E2)
