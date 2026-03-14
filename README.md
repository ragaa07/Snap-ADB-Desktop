# SnapADB

A modern desktop ADB (Android Debug Bridge) client built with Kotlin Multiplatform and Compose Multiplatform. Manage Android devices from macOS, Windows, or Linux through an intuitive graphical interface.

## Features

| Feature | Description |
|---------|-------------|
| **Dashboard** | Device info, battery, storage, and memory at a glance |
| **Multi-Device** | Connect, pair, disconnect, and nickname multiple devices |
| **App Manager** | List, install, uninstall, force-stop, clear data, extract APKs |
| **File Explorer** | Browse device filesystem, push/pull files, create directories, delete |
| **Shell** | Interactive ADB shell terminal with command history |
| **Logcat** | Real-time log streaming with filters, search, pause/resume, export |
| **Screen Mirror** | In-app screen mirror via scrcpy, screenshots, screen recording |
| **Performance** | Live CPU, memory, battery, and temperature charts with CSV export |
| **Device Controls** | Input injection, display settings, reboot modes, network controls |
| **Deep Links** | Fire URIs, save favorites, browse history (persisted via SQLite) |
| **Notifications** | Compose and send test notifications, save templates |

### General
- Dark/Light/System theme with "Midnight Developer" palette
- Keyboard shortcuts (Cmd/Ctrl+1–9 for navigation)
- Window position and size persistence
- Global error handling (ADB not found screen, device disconnect notifications)

## Screenshots

*Coming soon*

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.3.0 |
| UI | Compose Multiplatform 1.10.0, Material 3 |
| DI | Koin 4.0.4 |
| Database | SQLDelight 2.0.2 (SQLite) |
| Async | Kotlinx Coroutines 1.10.2 |
| Lifecycle | AndroidX Lifecycle ViewModel 2.9.6 |
| Build | Gradle 8.14.3 with convention plugins |

## Architecture

```
composeApp/           App entry point, DI aggregation, navigation host
core/
  adb/                ADB binary resolution, process execution, device monitoring
  database/           SQLDelight schema and driver
  navigation/         Router with StateFlow-based back stack
  common/             DispatcherProvider, ActionResult, shared utilities
  theme/              Material 3 theming with System/Dark/Light modes
  ui/                 MainShell layout, Sidebar, shared UI components
feature/
  dashboard/          Device overview cards
  multidevice/        Device list, connect/pair dialogs, nicknames
  appmanager/         App management with install/uninstall/force-stop
  fileexplorer/       File browsing, push/pull, delete, symlink resolution
  shell/              Terminal (interactive commands) + Logcat (streaming viewer)
  screenmirror/       scrcpy integration, screenshots, screen recording
  devicecontrols/     Input injection, display, reboot, network settings
  performance/        Live Canvas charts with per-app memory monitoring
  deeplink/           Deep link testing with SQLite-persisted favorites/history
  notification/       Notification testing with saved templates
lib/
  mirror/             scrcpy integration library
build-logic/          Convention plugins (snapadb.kmp.library, snapadb.kmp.compose.library)
```

Each feature module follows the **MVI pattern**: sealed State + sealed Intent + ViewModel, with Koin-injected ViewModels and Compose screens that collect `StateFlow`.

## Prerequisites

- **JDK 17+**
- **Android SDK** with `adb` available via one of:
  - `ANDROID_HOME` environment variable
  - `ANDROID_SDK_ROOT` environment variable
  - System `PATH`
- A connected Android device or emulator with USB debugging enabled
- **scrcpy** (optional) — required for screen mirror feature; auto-downloaded if not found

## Build & Run

```bash
# Run the desktop app
./gradlew :composeApp:run

# Run with Compose hot reload
./gradlew :composeApp:hotRunJvm

# Run all tests
./gradlew jvmTest

# Full build (compile + test + check)
./gradlew build

# Package native installer for your OS (DMG / MSI / Deb)
./gradlew :composeApp:packageDistributionForCurrentOS

# Clean
./gradlew :composeApp:clean
```

On Windows, replace `./gradlew` with `.\gradlew.bat`.

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Cmd/Ctrl + 1 | Dashboard |
| Cmd/Ctrl + 2 | Devices |
| Cmd/Ctrl + 3 | Apps |
| Cmd/Ctrl + 4 | Files |
| Cmd/Ctrl + 5 | Shell |
| Cmd/Ctrl + 6 | Logcat |
| Cmd/Ctrl + 7 | Mirror |
| Cmd/Ctrl + 8 | Performance |
| Cmd/Ctrl + 9 | Controls |

## Distribution

Native installers are generated per platform:

| OS | Format | Task |
|----|--------|------|
| macOS | `.dmg` | `packageDmg` |
| Windows | `.msi` | `packageMsi` |
| Linux | `.deb` | `packageDeb` |

The packaged app version is **1.0.0** with bundle ID `com.ragaa.snapadb`.

## Project Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/AboElEla48/SnapADB.git
   cd SnapADB
   ```
2. Open in IntelliJ IDEA or Android Studio.
3. Sync Gradle and run `./gradlew :composeApp:run`.

## License

This project is not yet licensed. All rights reserved.
