# :feature:screenmirror

Screen Mirror feature module for SnapADB. Capture screenshots, record the screen, launch scrcpy for live mirroring, and control device rotation and display settings.

## Features

- **Screenshot capture** — take a screenshot, preview it inline, and save as PNG
- **Screen recording** — record up to 180 seconds with configurable time limit, then save as MP4
- **scrcpy integration** — launch scrcpy with configurable resolution, bitrate, FPS, and display options (borderless, always-on-top, show touches, stay awake, screen off)
- **Auto-rotate toggle** — enable/disable accelerometer rotation
- **Manual rotation** — set screen orientation to 0/90/180/270 degrees
- **Stay awake toggle** — keep screen on while plugged in

## Architecture

- **Pattern**: MVI (sealed `ScreenMirrorState` + `ScreenMirrorIntent` + `ScreenMirrorResult`)
- **States**: `NoDevice` → `Loading` → `Ready` | `Error`
- **ViewModel**: `ScreenMirrorViewModel` — processes intents, manages scrcpy process lifecycle, handles temp file cleanup
- **DI**: `screenMirrorModule` registered in `AppModule`
- **Model**: `ScrcpyConfig` — maps UI options to scrcpy CLI arguments

## Key Classes

| Class | Role |
|-------|------|
| `ScreenMirrorScreen` | Compose UI with screenshot, recording, scrcpy, and quick actions sections |
| `ScreenMirrorViewModel` | Processes intents, orchestrates ADB commands and scrcpy process |
| `ScreenMirrorState` | Sealed class: `NoDevice`, `Loading`, `Error`, `Ready` |
| `ScreenMirrorIntent` | Sealed class of user actions (capture, record, launch scrcpy, rotate, etc.) |
| `ScreenMirrorResult` | One-shot success/failure feedback shown via Snackbar |
| `ScrcpyConfig` | Data class that converts UI options to scrcpy CLI args |

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `shell screencap -p <path>` | `TakeScreenshot` | Capture screenshot to device |
| `shell screenrecord [options] <path>` | `StartScreenRecord` | Start screen recording (1-180s) |
| `shell pkill -2 screenrecord` | `StopScreenRecord` | Stop an active recording |
| `pull <remote> <local>` | `PullFile` | Pull screenshot/recording to host |
| `shell rm '<path>'` | `RemoveRemoteFile` | Clean up temp files on device |
| `shell settings get system accelerometer_rotation` | `GetAutoRotateSetting` | Read auto-rotate state |
| `shell settings put system accelerometer_rotation <0\|1>` | `SetAutoRotate` | Toggle auto-rotate |
| `shell settings put system user_rotation <0-3>` | `SetUserRotation` | Set manual rotation |
| `shell settings get global stay_on_while_plugged_in` | `GetStayAwakeSetting` | Read stay-awake state |
| `shell settings put global stay_on_while_plugged_in <0-7>` | `SetStayAwake` | Toggle stay awake |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands
- External: [scrcpy](https://github.com/Genymobile/scrcpy) (optional, for live mirroring)
