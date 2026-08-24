package com.opezee.framogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Liquid glass" styling: translucent gradient panels with a specular border.
 * Deliberately no live backdrop blur — that would force Filament onto a TextureView
 * and cost ~a frame of latency, and latency is what sells the hologram.
 */
object Glass {
    val textPrimary = Color.White.copy(alpha = 0.92f)
    val textSecondary = Color.White.copy(alpha = 0.60f)
    val accent = Color(0xFF33CCFF)
}

fun Modifier.glassPanel(corner: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.05f),
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.08f),
                )
            ),
            shape = shape,
        )
}

fun Modifier.glassRow(selected: Boolean = false): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return this
        .clip(shape)
        .background(
            if (selected) Glass.accent.copy(alpha = 0.22f)
            else Color.White.copy(alpha = 0.07f)
        )
        .border(
            1.dp,
            if (selected) Glass.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
            shape,
        )
}
