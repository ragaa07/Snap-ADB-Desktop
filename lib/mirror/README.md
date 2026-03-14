# Mirror Library

A pure Kotlin library for Android device screen mirroring and touch input via ADB. Designed as a standalone, reusable module with zero framework dependencies beyond `kotlinx-coroutines`.

## Features

- **Live Screen Capture** — streams device screen as PNG frames via `adb exec-out screencap -p`
- **Touch Input** — tap, swipe, and key events forwarded to device via `adb shell input`
- **scrcpy Auto-Download** — download, extract, and manage scrcpy binaries for high-FPS external mirroring
- **Cross-Platform** — works on macOS, Windows, and Linux
- **Framework Agnostic** — no Compose, Android, or SnapADB dependencies

## Installation

Add the module dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(projects.lib.mirror)
}
```

## Quick Start

### Screen Mirroring

```kotlin
val mirror = DeviceMirror(
    adbExecutor = DefaultAdbExecutor(),         // uses ADB from PATH
    config = MirrorConfig(refreshIntervalMs = 300),
)

// Collect frames
coroutineScope.launch {
    mirror.start("DEVICE_SERIAL").collect { frame ->
        // frame.pngBytes — raw PNG image data
        // frame.width, frame.height — device resolution
        // frame.timestampMs — capture timestamp
        renderFrame(frame)
    }
}

// Stop by cancelling the collecting job
job.cancel()
```

### Touch Input

```kotlin
// Tap at device coordinates (x=500, y=1000)
mirror.sendTap("DEVICE_SERIAL", 500f, 1000f)

// Swipe from (100,500) to (100,1500) over 300ms
mirror.sendSwipe("DEVICE_SERIAL", 100f, 500f, 100f, 1500f, durationMs = 300)

// Send key event (Back, Home, Recents)
mirror.sendKeyEvent("DEVICE_SERIAL", TouchInput.KeyEvent.KEYCODE_BACK)    // 4
mirror.sendKeyEvent("DEVICE_SERIAL", TouchInput.KeyEvent.KEYCODE_HOME)    // 3
mirror.sendKeyEvent("DEVICE_SERIAL", TouchInput.KeyEvent.KEYCODE_RECENTS) // 187
```

### scrcpy Management

```kotlin
val scrcpyManager = ScrcpyManager()

// Check if scrcpy is available
if (scrcpyManager.isInstalled()) {
    val path = scrcpyManager.resolveScrcpyPath()
    // Launch scrcpy externally
}

// Download scrcpy with progress tracking
scrcpyManager.download().collect { progress ->
    // progress.fraction — 0.0 to 1.0 (or -1 if size unknown)
    // progress.bytesDownloaded / progress.totalBytes
    updateProgressBar(progress.fraction)
}
```

## API Reference

### `DeviceMirror`

The main entry point for screen mirroring and touch forwarding.

```kotlin
class DeviceMirror(
    adbExecutor: AdbExecutor,
    config: MirrorConfig = MirrorConfig(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
)
```

| Method | Description |
|--------|-------------|
| `start(deviceSerial: String): Flow<Frame>` | Starts capturing frames. Emits PNG frames continuously until the collecting coroutine is cancelled. |
| `stop()` | No-op. Stopping is handled by cancelling the collecting coroutine's job. |
| `sendTap(deviceSerial, x, y)` | Sends a tap event at device coordinates. |
| `sendSwipe(deviceSerial, x1, y1, x2, y2, durationMs)` | Sends a swipe gesture between two points. |
| `sendKeyEvent(deviceSerial, keyCode)` | Sends an Android key event (e.g., Back, Home). |

### `Frame`

Represents a single captured screen frame.

```kotlin
data class Frame(
    val pngBytes: ByteArray,    // Raw PNG image data
    val width: Int,             // Device screen width in pixels
    val height: Int,            // Device screen height in pixels
    val timestampMs: Long,      // Capture timestamp (epoch ms)
)
```

### `MirrorConfig`

Configuration for the screen capture loop.

```kotlin
data class MirrorConfig(
    val refreshIntervalMs: Long = 300,  // Minimum interval between frames
    val downscale: Int = 2,             // Reserved for future use
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `refreshIntervalMs` | `300` | Minimum time between frame captures. Lower = higher FPS but more CPU/USB load. Actual frame rate depends on device speed (~200-500ms per screencap). |
| `downscale` | `2` | Reserved for future downscaling support. |

### `AdbExecutor`

Interface for running ADB commands. Implement this to provide custom ADB path resolution or process management.

```kotlin
interface AdbExecutor {
    suspend fun exec(args: List<String>): ByteArray      // stdout as raw bytes
    suspend fun execText(args: List<String>): String      // stdout as text
}
```

The `args` parameter contains ADB arguments **without** the `adb` binary itself. For example, `["-s", "SERIAL", "exec-out", "screencap", "-p"]`.

### `DefaultAdbExecutor`

Default implementation that finds ADB on the system PATH or via `ANDROID_HOME`/`ANDROID_SDK_ROOT` environment variables.

```kotlin
class DefaultAdbExecutor(
    adbPath: String = findAdb(),  // Auto-detects ADB location
)
```

### `TouchInput`

Sealed class defining input types and key code constants.

```kotlin
sealed class TouchInput {
    data class Tap(val x: Float, val y: Float)
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long = 300)
    data class KeyEvent(val code: Int) {
        companion object {
            const val KEYCODE_BACK = 4
            const val KEYCODE_HOME = 3
            const val KEYCODE_RECENTS = 187
        }
    }
}
```

### `ScrcpyManager`

Manages scrcpy binary discovery, download, and extraction.

```kotlin
class ScrcpyManager(
    installDir: File = defaultInstallDir(),  // ~/.snapadb/scrcpy
)
```

| Method | Description |
|--------|-------------|
| `resolveScrcpyPath(): String?` | Returns the absolute path to the scrcpy binary, checking the install directory first, then system PATH. Returns `null` if not found. |
| `isInstalled(): Boolean` | Returns `true` if scrcpy is available. |
| `download(version: String): Flow<DownloadProgress>` | Downloads and extracts scrcpy from GitHub releases. Emits progress updates. |

### `DownloadProgress`

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,        // -1 if unknown (chunked transfer)
) {
    val fraction: Float           // 0.0-1.0, or -1.0 if totalBytes unknown
}
```

## Architecture

```
lib/mirror/
├── AdbExecutor.kt          # Interface — abstraction for ADB process execution
├── DefaultAdbExecutor.kt   # Default implementation using ProcessBuilder
├── DeviceMirror.kt         # Main API — frame capture loop + touch forwarding
├── Frame.kt                # Data model for captured frames
├── MirrorConfig.kt         # Configuration for capture behavior
├── TouchInput.kt           # Sealed class for input types + key constants
└── ScrcpyManager.kt        # scrcpy binary management (download/extract/resolve)
```

### Design Decisions

- **No SnapADB dependencies**: The library depends only on `kotlinx-coroutines-core`, making it reusable in any Kotlin/JVM project.
- **`AdbExecutor` interface**: Decouples ADB process execution from the library. Host apps can provide their own implementation with custom ADB path resolution, logging, or timeout handling.
- **Coroutine-based lifecycle**: Frame capture uses Kotlin `Flow`. Stopping is handled by coroutine cancellation — no explicit stop flags needed.
- **`CancellationException` propagation**: The frame capture loop properly re-throws `CancellationException` to respect structured concurrency.
- **Configurable dispatcher**: `DeviceMirror` accepts an `ioDispatcher` parameter for testability (inject `TestDispatcher` in tests).

## Coordinate Mapping

The library operates in **device coordinate space**. When building a UI, you need to map from display coordinates to device coordinates:

```kotlin
// Example: Compose UI coordinate mapping
val scaleX = deviceWidth.toFloat() / uiImageWidth
val scaleY = deviceHeight.toFloat() / uiImageHeight

// On tap at UI coordinates (uiX, uiY):
mirror.sendTap(serial, uiX * scaleX, uiY * scaleY)
```

Note: If using `ContentScale.Fit` in Compose, the image may be letterboxed. Account for the offset when mapping coordinates.

## Performance Characteristics

| Metric | Typical Value | Notes |
|--------|--------------|-------|
| Frame capture latency | 200-500ms | Depends on device, resolution, USB speed |
| Effective FPS | 2-4 FPS | Limited by `screencap` command execution time |
| Touch input latency | ~100-200ms | Single ADB command per touch event |
| PNG frame size | 1-5 MB | Varies with resolution and screen content |

For smooth 60 FPS mirroring, use scrcpy (external window) via `ScrcpyManager`.

## scrcpy Download Details

`ScrcpyManager.download()` performs these steps:

1. Detects OS (Windows/macOS/Linux) and architecture
2. Downloads the matching archive from GitHub releases (`scrcpy v3.3.4` by default)
3. Extracts the archive (ZIP for Windows, tar.gz for Unix)
4. Flattens the archive subdirectory structure
5. Sets executable permission (Unix) and clears macOS Gatekeeper quarantine

Default install location: `~/.snapadb/scrcpy/`

### Security

- **Zip Slip protection**: All extracted file paths are validated to stay within the install directory
- **Gatekeeper handling**: macOS quarantine attributes are cleared via `xattr -cr`
- **Process timeout**: ADB commands have a 30-second timeout to prevent hangs

## Thread Safety

- `DeviceMirror.start()` returns a cold `Flow` — each collector gets its own capture loop
- Touch input methods (`sendTap`, `sendSwipe`, `sendKeyEvent`) are suspend functions safe to call from any coroutine
- `ScrcpyManager` methods are synchronous (except `download()` which returns a `Flow`)

## Requirements

- JDK 17+
- ADB available on PATH or via `ANDROID_HOME`
- Android device with USB debugging enabled
- For scrcpy: download via `ScrcpyManager` or install separately

## License

Part of the SnapADB project.
