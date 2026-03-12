# :feature:notification

Notification Tester feature module for SnapADB. Compose and send notifications to a connected device, and save reusable templates.

## Features

- **Send notifications** — post notifications via `cmd notification post` with tag, title, and text
- **Cancel notifications** — cancel a posted notification by tag
- **Save templates** — persist notification payloads as named templates in SQLite
- **Load templates** — populate the form from a saved template
- **Template management** — list, load, and delete saved templates

## Architecture

- **Pattern**: MVVM (sealed `NotificationState` + `NotificationIntent`)
- **States**: `NoDevice` → `Ready` (with templates list and optional loaded payload)
- **ViewModel**: `NotificationViewModel` — manages send/cancel, template CRUD
- **DI**: `notificationModule` registered in `AppModule`
- **Persistence**: `notification_template` table via SQLDelight (payload stored as JSON)

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `cmd notification post -S bigtext -t <title> <tag> <text>` | `PostNotification` | Post a notification |
| `cmd notification cancel <tag>` | `CancelNotification` | Cancel a notification |
| `am broadcast -a <action> [--es k v]` | `SendBroadcast` | Send a broadcast intent |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands
- `:core:database` — SnapAdbDatabase (notification_template table)
