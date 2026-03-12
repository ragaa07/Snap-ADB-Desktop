# :feature:fileexplorer

File Explorer feature module for SnapADB. Browse, transfer, and manage files on connected Android devices.

## Features

- **Browse directories** — navigate the device filesystem with `ls -la`
- **Breadcrumb navigation** — clickable path segments for quick traversal
- **Pull files** — download files from device to local machine (save dialog)
- **Push files** — upload local files to current device directory (file chooser)
- **Delete** — remove files or directories (`rm -rf`)
- **Create folder** — `mkdir -p` with dialog input
- **Symlink support** — follows symlinks on click, displays link targets

## Architecture

- **Pattern**: MVI (sealed `FileExplorerState` + `FileExplorerIntent`)
- **States**: `NoDevice` → `Loading` → `Loaded` / `Error`
- **ViewModel**: `FileExplorerViewModel` — breadcrumb state, directory navigation, file operations
- **DI**: `fileExplorerModule` registered in `AppModule`

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `shell ls -la <path>` | `ListFiles` | List directory contents |
| `adb pull <remote> <local>` | `PullFile` | Download file from device |
| `adb push <local> <remote>` | `PushFile` | Upload file to device |
| `shell rm -rf <path>` | `DeleteFile` | Delete file or directory |
| `shell mkdir -p <path>` | `MakeDirectory` | Create directory |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands, models
