package com.opezee.framogram

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.opezee.framogram.config.AppSettings
import com.opezee.framogram.config.RecentModel
import com.opezee.framogram.config.ScreenGeometry
import com.opezee.framogram.config.SettingsStore
import com.opezee.framogram.model.BundledModel
import com.opezee.framogram.model.ModelRepository
import com.opezee.framogram.render.DebugStats
import com.opezee.framogram.render.HologramRenderer
import com.opezee.framogram.tracking.EyeState
import com.opezee.framogram.tracking.FaceTracker
import com.opezee.framogram.ui.MainScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: ModelRepository
    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: HologramRenderer
    private lateinit var tracker: FaceTracker
    private val eyeState = EyeState()

    private var settings by mutableStateOf(AppSettings())
    private var stats by mutableStateOf(DebugStats())
    private var geometry by mutableStateOf<ScreenGeometry?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                tracker.bind(this, currentRotation())
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required for the holographic effect",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadFromUri(uri, persist = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(applicationContext)
        repository = ModelRepository(applicationContext)

        // Initial settings decide orientation before the first layout.
        val initial = runBlocking { settingsStore.flow.first() }
        settings = initial
        requestedOrientation = if (initial.landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val geom = ScreenGeometry.compute(this, currentRotation())
        geometry = geom

        surfaceView = SurfaceView(this)
        tracker = FaceTracker(
            context = applicationContext,
            eyeState = eyeState,
            geomProvider = { renderer.geometry },
            kCalProvider = { settings.kCal },
        )
        renderer = HologramRenderer(
            surfaceView = surfaceView,
            context = this,
            eyeState = eyeState,
            initialGeometry = geom,
            trackingFpsProvider = { tracker.trackingFps },
            onDebugStats = { stats = it },
        )
        renderer.setGridPattern(initial.gridPattern)
        applyGridColor(initial.gridColorIndex)
        renderer.setLit(initial.lit)

        // Keep settings state fresh and push render-affecting changes to the renderer.
        lifecycleScope.launch {
            settingsStore.flow.collect { s ->
                if (s.gridPattern != settings.gridPattern) renderer.setGridPattern(s.gridPattern)
                if (s.gridColorIndex != settings.gridColorIndex) applyGridColor(s.gridColorIndex)
                if (s.lit != settings.lit) renderer.setLit(s.lit)
                settings = s
            }
        }

        setContent {
            geometry?.let { g ->
                MainScreen(
                    surfaceView = surfaceView,
                    bundled = repository.bundled,
                    recents = settings.recents,
                    settings = settings,
                    stats = stats,
                    geometry = g,
                    onLoadBundled = ::loadBundled,
                    onLoadRecent = { loadFromUri(Uri.parse(it.uri), persist = false, knownName = it.name) },
                    onPickFile = {
                        openDocument.launch(arrayOf("model/gltf-binary", "application/octet-stream"))
                    },
                    onToggleOrientation = ::toggleOrientation,
                    onLit = { lit -> lifecycleScope.launch { settingsStore.setLit(lit) } },
                    onPattern = { p -> lifecycleScope.launch { settingsStore.setGridPattern(p) } },
                    onColorIndex = { c -> lifecycleScope.launch { settingsStore.setGridColorIndex(c) } },
                    onDebugOverlay = { d -> lifecycleScope.launch { settingsStore.setDebugOverlay(d) } },
                    onCalibrate = ::calibrate,
                )
            }
        }

        loadInitialModel(initial)

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> tracker.bind(this, currentRotation())
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        renderer.start()
    }

    override fun onPause() {
        renderer.stop()
        super.onPause()
    }

    override fun onDestroy() {
        renderer.destroy()
        tracker.shutdown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val geom = ScreenGeometry.compute(this, currentRotation())
        geometry = geom
        renderer.onGeometryChanged(geom)
        tracker.setTargetRotation(currentRotation())
    }

    private fun currentRotation(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    private fun applyGridColor(index: Int) {
        val (r, g, b) = SettingsStore.GRID_COLORS[index.coerceIn(0, SettingsStore.GRID_COLORS.size - 1)]
        renderer.setGridColor(r, g, b)
    }

    private fun toggleOrientation() {
        val toLandscape = !settings.landscape
        lifecycleScope.launch { settingsStore.setLandscape(toLandscape) }
        requestedOrientation = if (toLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun calibrate() {
        val raw = tracker.lastRawMatrixDist
        if (raw > 0.05) {
            val k = (0.60 / raw).toFloat().coerceIn(0.3f, 3f)
            lifecycleScope.launch { settingsStore.setKCal(k) }
            Toast.makeText(this, "Calibrated (k = %.2f)".format(k), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No face tracked — stand in view first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInitialModel(initial: AppSettings) {
        val last = initial.lastModel
        when {
            last.startsWith("asset:") -> {
                val file = last.removePrefix("asset:")
                repository.bundled.find { it.file == file }?.let { loadBundled(it) }
                    ?: loadDefault()
            }
            last.startsWith("uri:") -> loadFromUri(
                Uri.parse(last.removePrefix("uri:")), persist = false, fallbackToDefault = true,
            )
            else -> loadDefault()
        }
    }

    private fun loadDefault() {
        repository.bundled.firstOrNull()?.let(::loadBundled)
    }

    private fun loadBundled(model: BundledModel) {
        lifecycleScope.launch {
            try {
                val buffer = repository.readAsset(model.file)
                if (renderer.loadModel(buffer)) {
                    settingsStore.setLastModel("asset:${model.file}")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load ${model.name}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun loadFromUri(
        uri: Uri,
        persist: Boolean,
        knownName: String? = null,
        fallbackToDefault: Boolean = false,
    ) {
        lifecycleScope.launch {
            try {
                if (persist) {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val buffer = repository.readUri(uri)
                if (renderer.loadModel(buffer)) {
                    val name = knownName ?: repository.displayName(uri)
                    settingsStore.addRecent(RecentModel(name, uri.toString()))
                    settingsStore.setLastModel("uri:$uri")
                } else {
                    Toast.makeText(this@MainActivity, "Not a valid .glb file", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Could not open model", Toast.LENGTH_SHORT).show()
                if (fallbackToDefault) loadDefault()
            }
        }
    }
}
