# :feature:appmanager

App Manager feature module for SnapADB. Lists installed packages, provides search/filter, and supports app lifecycle actions.

## Features

- **List packages** — fetches all installed apps via `pm list packages -f`
- **Search** — filter by package name
- **Filter** — All / User / System app chips
- **App details** — tap any app to view version, installer, APK path, enabled state
- **Install APK** — file chooser to select and install `.apk` files
- **Uninstall** — remove user apps
- **Force stop** — stop a running app
- **Clear data** — wipe app storage and cache

## Architecture

- **Pattern**: MVI (sealed `AppManagerState` + `AppManagerIntent`)
- **States**: `NoDevice` → `Loading` → `Loaded` / `Error`
- **ViewModel**: `AppManagerViewModel` — reacts to device selection, manages app list and actions
- **DI**: `appManagerModule` registered in `AppModule`

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `pm list packages -f` | `ListPackages` | List all packages with APK paths |
| `dumpsys package <pkg>` | `GetPackageInfo` | Detailed package info |
| `adb install -r <path>` | `InstallApp` | Install APK |
| `adb uninstall <pkg>` | `UninstallApp` | Uninstall app |
| `am force-stop <pkg>` | `ForceStopApp` | Force stop app |
| `pm clear <pkg>` | `ClearAppData` | Clear app data |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands, models
