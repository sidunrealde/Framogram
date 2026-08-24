package com.opezee.framogram.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.opezee.framogram.config.AppSettings
import com.opezee.framogram.config.RecentModel
import com.opezee.framogram.config.ScreenGeometry
import com.opezee.framogram.model.BundledModel
import com.opezee.framogram.render.DebugStats

private val DRAWER_WIDTH = 324.dp
private val EDGE_STRIP = 28.dp

/**
 * Root screen: the Filament SurfaceView with two hand-rolled edge-swipe glass drawers.
 * Left edge: model library. Right edge: display settings.
 *
 * Hand-rolled (not ModalNavigationDrawer) because nesting two Material drawers via the
 * RTL trick lets the inner drawer's full-width drag detector swallow the outer one's
 * left-edge swipe — only explicit edge strips keep both gestures independent.
 */
@Composable
fun MainScreen(
    surfaceView: SurfaceView,
    bundled: List<BundledModel>,
    recents: List<RecentModel>,
    settings: AppSettings,
    stats: DebugStats,
    geometry: ScreenGeometry,
    onLoadBundled: (BundledModel) -> Unit,
    onLoadRecent: (RecentModel) -> Unit,
    onPickFile: () -> Unit,
    onToggleOrientation: () -> Unit,
    onLit: (Boolean) -> Unit,
    onPattern: (Int) -> Unit,
    onColorIndex: (Int) -> Unit,
    onDebugOverlay: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
) {
    var leftOpen by remember { mutableStateOf(false) }
    var rightOpen by remember { mutableStateOf(false) }
    val anyOpen = leftOpen || rightOpen
    val closeAll = { leftOpen = false; rightOpen = false }

    val leftOffset by animateDpAsState(
        targetValue = if (leftOpen) 0.dp else -DRAWER_WIDTH,
        animationSpec = spring(stiffness = 400f),
        label = "leftDrawer",
    )
    val rightOffset by animateDpAsState(
        targetValue = if (rightOpen) 0.dp else DRAWER_WIDTH,
        animationSpec = spring(stiffness = 400f),
        label = "rightDrawer",
    )

    BackHandler(enabled = anyOpen) { closeAll() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize(),
        )
        if (settings.debugOverlay) {
            DebugOverlay(stats = stats, geom = geometry)
        }

        // Edge swipe catchers, only while everything is closed.
        if (!anyOpen) {
            EdgeStrip(Alignment.CenterStart) { drag -> if (drag > 0) leftOpen = true }
            EdgeStrip(Alignment.CenterEnd) { drag -> if (drag < 0) rightOpen = true }
        }

        if (anyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { closeAll() } }
            )
        }

        DrawerPanel(alignment = Alignment.CenterStart, xOffset = leftOffset) {
            ModelDrawerContent(
                bundled = bundled,
                recents = recents,
                currentModelKey = settings.lastModel,
                onLoadBundled = { closeAll(); onLoadBundled(it) },
                onLoadRecent = { closeAll(); onLoadRecent(it) },
                onPickFile = { closeAll(); onPickFile() },
            )
        }
        DrawerPanel(alignment = Alignment.CenterEnd, xOffset = rightOffset) {
            SettingsDrawerContent(
                settings = settings,
                onToggleOrientation = onToggleOrientation,
                onLit = onLit,
                onPattern = onPattern,
                onColorIndex = onColorIndex,
                onDebugOverlay = onDebugOverlay,
                onCalibrate = onCalibrate,
            )
        }
    }
}

@Composable
private fun BoxScope.EdgeStrip(
    alignment: Alignment,
    onHorizontalDrag: (Float) -> Unit,
) {
    Box(
        Modifier
            .align(alignment)
            .fillMaxHeight()
            .width(EDGE_STRIP)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onHorizontalDrag(dragAmount)
                }
            }
    )
}

@Composable
private fun BoxScope.DrawerPanel(
    alignment: Alignment,
    xOffset: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .align(alignment)
            .offset(x = xOffset)
            .width(DRAWER_WIDTH)
            .fillMaxHeight()
    ) {
        content()
    }
}
