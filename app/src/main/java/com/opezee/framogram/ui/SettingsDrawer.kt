package com.opezee.framogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opezee.framogram.config.AppSettings
import com.opezee.framogram.config.SettingsStore

/** Right drawer: orientation toggle, grid pattern, grid color, debug tools. */
@Composable
fun SettingsDrawerContent(
    settings: AppSettings,
    onToggleOrientation: () -> Unit,
    onLit: (Boolean) -> Unit,
    onPattern: (Int) -> Unit,
    onColorIndex: (Int) -> Unit,
    onDebugOverlay: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .padding(12.dp)
            .glassPanel()
            .padding(18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Display", color = Glass.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .glassRow()
                .clickable { onToggleOrientation() }
                .padding(14.dp)
        ) {
            Text(
                if (settings.landscape) "Switch to portrait" else "Switch to landscape",
                color = Glass.accent, fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(22.dp))
        Text("Lighting", color = Glass.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "Lit", false to "Unlit").forEach { (lit, label) ->
                Box(
                    Modifier
                        .glassRow(selected = settings.lit == lit)
                        .clickable { onLit(lit) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(label, color = Glass.textPrimary, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Grid pattern", color = Glass.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lines", "Dots", "Crosses").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .glassRow(selected = settings.gridPattern == i)
                        .clickable { onPattern(i) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(label, color = Glass.textPrimary, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Grid color", color = Glass.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsStore.GRID_COLORS.forEachIndexed { i, (r, g, b) ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(r, g, b))
                        .border(
                            if (settings.gridColorIndex == i) 3.dp else 1.dp,
                            if (settings.gridColorIndex == i) Color.White
                            else Color.White.copy(alpha = 0.3f),
                            CircleShape,
                        )
                        .clickable { onColorIndex(i) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Debug overlay", color = Glass.textPrimary, fontSize = 15.sp)
            Switch(checked = settings.debugOverlay, onCheckedChange = onDebugOverlay)
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .glassRow()
                .clickable { onCalibrate() }
                .padding(14.dp)
        ) {
            Column {
                Text("Calibrate distance", color = Glass.accent, fontSize = 15.sp)
                Text(
                    "Stand exactly 60 cm from the screen, then tap.",
                    color = Glass.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }
}
