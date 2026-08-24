package com.opezee.framogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opezee.framogram.config.ScreenGeometry
import com.opezee.framogram.render.DebugStats
import kotlin.math.roundToInt

/**
 * Numbers for tuning plus a crosshair marking where the tracked eye projects onto the
 * screen plane — the tool for empirically verifying axis signs and the camera-offset
 * rotation table (move right → marker moves right, etc.).
 */
@Composable
fun DebugOverlay(stats: DebugStats, geom: ScreenGeometry) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(10.dp)
        ) {
            val cm = { v: Double -> (v * 100).roundToInt() }
            DebugLine("render  ${"%5.1f".format(stats.renderFps)} fps")
            DebugLine("track   ${"%5.1f".format(stats.trackingFps)} fps")
            DebugLine("eye     x ${cm(stats.eyeX)}  y ${cm(stats.eyeY)}  z ${cm(stats.eyeZ)} cm")
            DebugLine("dist    mtx ${cm(stats.rawMatrixDistM)}  ipd ${cm(stats.rawIpdDistM)} cm")
            DebugLine("age     ${"%4.0f".format(stats.sampleAgeMs)} ms  " +
                if (stats.tracking) "TRACKING" else "lost")
            DebugLine("screen  ${(geom.widthM * 1000).roundToInt()} x " +
                "${(geom.heightM * 1000).roundToInt()} mm  rot ${geom.rotation}")
            DebugLine("camOff  x ${cm(geom.camOffsetX.toDouble())}  " +
                "y ${cm(geom.camOffsetY.toDouble())} cm")
        }

        if (stats.tracking) {
            val pxPerMx = geom.widthPx / geom.widthM
            val pxPerMy = geom.heightPx / geom.heightM
            val ox = (stats.eyeX * pxPerMx).roundToInt()
            val oy = (-stats.eyeY * pxPerMy).roundToInt()
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(ox, oy) }
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF33CCFF).copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
private fun DebugLine(text: String) {
    Text(text, color = Color(0xFF9BE8FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
}
