# SnapADB

A modern desktop ADB (Android Debug Bridge) client built with Kotlin Multiplatform and Compose Multiplatform. Manage Android devices from macOS, Windows, or Linux through an intuitive graphical interface.

## Features

| Feature | Status | Description |
|---------|--------|-------------|
| Dashboard | Done | Device info, battery, storage, and memory at a glance |
| Multi-Device | Done | Connect, pair, disconnect, and nickname multiple devices |
| Shell & Logs | Done | Interactive ADB shell terminal and streaming logcat viewer |
| App Manager | Planned | Install, uninstall, and manage apps |
| File Explorer | Planned | Browse and transfer files on-device |
| Screen Mirror | Planned | Mirror and control the device screen |
| Device Controls | Planned | Quick actions (reboot, screenshot, input) |
| Performance | Planned | CPU, memory, and network monitoring |
| Deep Links | Planned | Manage and fire deep links/intents |
| Notifications | Planned | Push custom notification payloads |

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
  ui/                 MainShell layout, Sidebar navigation rail
feature/
  dashboard/          Device overview cards
  multidevice/        Device list, connect/pair dialogs, nicknames
  shell/              Terminal (one-shot commands) + Logcat (streaming viewer)
  appmanager/         (placeholder)
  fileexplorer/       (placeholder)
  screenmirror/       (placeholder)
  devicecontrols/     (placeholder)
  performance/        (placeholder)
  deeplink/           (placeholder)
  notification/       (placeholder)
build-logic/          Convention plugins (snapadb.kmp.library, snapadb.kmp.compose.library)
```

Each feature module follows the **MVI pattern**: sealed State + sealed Intent + ViewModel, with Koin-injected ViewModels and Compose screens that collect `StateFlow`.

## Prerequisites

- **JDK 17+**
- **Android SDK** with `adb` on your PATH (or `ANDROID_HOME` / `ANDROID_SDK_ROOT` set)
- A connected Android device or emulator with USB debugging enabled

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
