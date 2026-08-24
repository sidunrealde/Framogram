package com.opezee.framogram.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opezee.framogram.config.RecentModel
import com.opezee.framogram.model.BundledModel

/** Left drawer: preset models, recently opened models, and the file picker. */
@Composable
fun ModelDrawerContent(
    bundled: List<BundledModel>,
    recents: List<RecentModel>,
    currentModelKey: String,
    onLoadBundled: (BundledModel) -> Unit,
    onLoadRecent: (RecentModel) -> Unit,
    onPickFile: () -> Unit,
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
        Text("Models", color = Glass.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .glassRow()
                .clickable { onPickFile() }
                .padding(14.dp)
        ) {
            Text("Open file  (.glb)", color = Glass.accent, fontSize = 15.sp)
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Recent", color = Glass.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            recents.forEach { r ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .glassRow(selected = currentModelKey == "uri:${r.uri}")
                        .clickable { onLoadRecent(r) }
                        .padding(14.dp)
                ) {
                    Text(r.name, color = Glass.textPrimary, fontSize = 15.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Presets", color = Glass.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        bundled.forEach { m ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .glassRow(selected = currentModelKey == "asset:${m.file}")
                    .clickable { onLoadBundled(m) }
                    .padding(14.dp)
            ) {
                Text(m.name, color = Glass.textPrimary, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}
