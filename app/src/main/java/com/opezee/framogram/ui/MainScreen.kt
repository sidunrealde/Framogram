package com.opezee.framogram.ui

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.opezee.framogram.config.AppSettings
import com.opezee.framogram.config.RecentModel
import com.opezee.framogram.config.ScreenGeometry
import com.opezee.framogram.model.BundledModel
import com.opezee.framogram.render.DebugStats
import kotlinx.coroutines.launch

/**
 * Root screen: the Filament SurfaceView with two edge-swipe glass drawers on top.
 * Left (swipe from left): model library. Right (swipe from right): display settings —
 * the right drawer is a ModalNavigationDrawer flipped with an RTL layout direction,
 * with content direction restored to LTR inside.
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
    onPattern: (Int) -> Unit,
    onColorIndex: (Int) -> Unit,
    onDebugOverlay: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
) {
    val leftDrawer = rememberDrawerState(DrawerValue.Closed)
    val rightDrawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeLeft: () -> Unit = { scope.launch { leftDrawer.close() } }

    ModalNavigationDrawer(
        drawerState = leftDrawer,
        scrimColor = Color.Black.copy(alpha = 0.35f),
        drawerContent = {
            ModelDrawerContent(
                bundled = bundled,
                recents = recents,
                currentModelKey = settings.lastModel,
                onLoadBundled = { closeLeft(); onLoadBundled(it) },
                onLoadRecent = { closeLeft(); onLoadRecent(it) },
                onPickFile = { closeLeft(); onPickFile() },
            )
        },
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = rightDrawer,
                scrimColor = Color.Black.copy(alpha = 0.35f),
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SettingsDrawerContent(
                            settings = settings,
                            onToggleOrientation = onToggleOrientation,
                            onPattern = onPattern,
                            onColorIndex = onColorIndex,
                            onDebugOverlay = onDebugOverlay,
                            onCalibrate = onCalibrate,
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { surfaceView },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (settings.debugOverlay) {
                            DebugOverlay(stats = stats, geom = geometry)
                        }
                    }
                }
            }
        }
    }
}
