package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

/**
 * Stylized mushroom glyph used as:
 *  - S1 splash icon
 *  - S11/S12 image fallback (when the local asset doesn't exist)
 *  - Tab-bar "图鉴" tab icon variant (if needed)
 *
 * Two-tone (cap + stem), drawn in pure Compose Canvas — no bitmap assets.
 */
@Composable
fun MushroomIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    capColor: Color = MaterialTheme.colorScheme.primary,
    stemColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val description = stringResource(R.string.cd_mushroom_icon)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        drawMushroom(capColor, stemColor, accentColor)
    }
}

private fun DrawScope.drawMushroom(
    cap: Color,
    stem: Color,
    accent: Color,
) {
    val w = size.width
    val h = size.height

    // Cap (dome, top 55% of the icon)
    val capPath = Path().apply {
        moveTo(w * 0.5f, h * 0.05f)
        cubicTo(w * 0.95f, h * 0.05f, w * 0.95f, h * 0.50f, w * 0.5f, h * 0.50f)
        cubicTo(w * 0.05f, h * 0.50f, w * 0.05f, h * 0.05f, w * 0.5f, h * 0.05f)
        close()
    }
    drawPath(capPath, color = cap)

    // Cap-stem join band (accent)
    drawRect(
        color = accent,
        topLeft = Offset(w * 0.18f, h * 0.48f),
        size = Size(w * 0.64f, h * 0.06f),
    )

    // Stem (rectangle with rounded bottom)
    val stemPath = Path().apply {
        moveTo(w * 0.32f, h * 0.54f)
        lineTo(w * 0.68f, h * 0.54f)
        lineTo(w * 0.62f, h * 0.92f)
        cubicTo(w * 0.62f, h * 0.96f, w * 0.58f, h * 0.96f, w * 0.55f, h * 0.96f)
        lineTo(w * 0.45f, h * 0.96f)
        cubicTo(w * 0.42f, h * 0.96f, w * 0.38f, h * 0.96f, w * 0.38f, h * 0.92f)
        close()
    }
    drawPath(stemPath, color = stem)

    // Subtle cap outline for definition
    drawPath(capPath, color = cap.copy(alpha = 0.3f), style = Stroke(width = 2f))
}
