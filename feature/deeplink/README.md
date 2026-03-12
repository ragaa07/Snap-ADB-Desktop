# :feature:deeplink

Deep Link Tester feature module for SnapADB. Fire deep links on a connected device, save links with labels, and manage favorites.

## Features

- **Fire deep links** — send any URI via `am start -a VIEW -d <uri>` with optional target package
- **Save links** — persist URIs with label and package to SQLite
- **Favorites** — toggle favorite status, filter to favorites only
- **History** — links sorted by last used timestamp
- **Search** — find saved links by URI or label

## Architecture

- **Pattern**: MVVM (sealed `DeepLinkState` + `DeepLinkIntent`)
- **States**: `NoDevice` → `Ready` (with links list and favorites filter)
- **ViewModel**: `DeepLinkViewModel` — manages link CRUD, fires links, tracks favorites
- **DI**: `deepLinkModule` registered in `AppModule`
- **Persistence**: `deep_link` table via SQLDelight

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `am start -a android.intent.action.VIEW -d <uri> [-p <pkg>]` | `FireDeepLink` | Fire a deep link |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands
- `:core:database` — SnapAdbDatabase (deep_link table)
