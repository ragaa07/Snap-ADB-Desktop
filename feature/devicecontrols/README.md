# :feature:devicecontrols

Device Controls feature module for SnapADB. Provides input injection, display settings, network toggles, system reboot, and raw Android settings access.

## Features

- **Key events** — send common Android keycodes (Home, Back, Volume, Power, etc.)
- **Text input** — type text on the device remotely
- **Tap/Swipe** — send touch events by coordinates
- **Screen density** — get, set, or reset display DPI
- **Screen size** — override or reset display resolution
- **Network** — toggle WiFi, mobile data, airplane mode
- **System** — reboot to normal, bootloader, or recovery (with confirmation dialog)
- **Settings** — raw `settings get/put` for system, secure, and global namespaces

## Architecture

- **Pattern**: MVVM (sealed `DeviceControlsState` + `DeviceControlsIntent`)
- **States**: `NoDevice` → `Loading` → `Ready` / `Error`
- **ViewModel**: `DeviceControlsViewModel` — executes device commands, tracks display info
- **DI**: `deviceControlsModule` registered in `AppModule`

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `input keyevent <code>` | `SendKeyEvent` | Send key event |
| `input text <text>` | `SendText` | Type text |
| `input tap <x> <y>` | `SendTap` | Tap at coordinates |
| `input swipe <x1> <y1> <x2> <y2> <ms>` | `SendSwipe` | Swipe gesture |
| `settings get <ns> <key>` | `GetSetting` | Read a setting |
| `settings put <ns> <key> <val>` | `PutSetting` | Write a setting |
| `wm density` / `wm density <dpi>` | `GetScreenDensity` / `SetScreenDensity` | Screen density |
| `wm size` / `wm size <WxH>` | `GetScreenSize` / `SetScreenSize` | Screen size |
| `svc wifi enable/disable` | `SetWifiEnabled` | Toggle WiFi |
| `svc data enable/disable` | `SetMobileDataEnabled` | Toggle mobile data |
| `settings put global airplane_mode_on` | `SetAirplaneMode` | Toggle airplane mode |
| `adb reboot [mode]` | `RebootDevice` | Reboot device |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands
