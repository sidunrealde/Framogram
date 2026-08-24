# Framogram

A holographic display app for Android. A glTF model sits inside a virtual grid box behind the
screen; the front camera tracks the viewer's face (MediaPipe) and the renderer (Filament) applies
an off-axis projection so the screen behaves like a window into that box — a head-coupled
"hologram" illusion, recreated from the original Unreal Engine desktop prototype.

## Stack

- Kotlin single-activity app, Jetpack Compose for UI chrome
- [Google Filament](https://github.com/google/filament) — PBR renderer, glTF loading (`gltfio`),
  runtime material building (`filamat`)
- MediaPipe Tasks Vision Face Landmarker (LIVE_STREAM, GPU delegate)
- CameraX ImageAnalysis for the front camera feed

## Branching

`main` — stable. `develop` — integration. Features are built on `feature/*` branches and merged
into `develop` with `--no-ff` to keep history.

## Build

Requires JDK 17 and an Android SDK (platform 36). Set `sdk.dir` in `local.properties`, then:

```
gradlew.bat assembleDebug
```
