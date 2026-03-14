# :feature:performance

Performance Monitor feature module for SnapADB. Real-time monitoring of CPU, memory, and battery metrics on a connected Android device with live-updating charts and CSV export.

## Features

- **CPU monitoring** — overall and per-core usage computed from `/proc/stat` deltas
- **Memory monitoring** — system RAM usage percentage with total/used/available breakdown
- **Battery monitoring** — level, temperature, status, health, voltage, and current draw
- **Per-app memory** — track a specific package's PSS, Java heap, native heap, code, stack, graphics, and system memory via `dumpsys meminfo`
- **Live charts** — custom Canvas-based line charts with filled area, grid lines, and time-axis labels
- **Configurable polling** — 500ms, 1s, 2s, or 5s intervals
- **CSV export** — export collected time-series data (CPU, memory, battery) to a CSV file
- **Tabbed UI** — Overview (combined chart), CPU, Memory, and Battery tabs

## Architecture

- **Pattern**: MVI (sealed `PerformanceState` + `PerformanceIntent` + `PerformanceResult`)
- **States**: `NoDevice` -> `Ready` (with monitoring flag, polling interval, app filter, and snapshot)
- **ViewModel**: `PerformanceViewModel` — parallel ADB polling with coroutine-safe `Mutex`-guarded time-series data, dirty-flag-based UI emission throttling (100ms)
- **Data model**: `RollingTimeSeries` — fixed-size `ArrayDeque` (60 points) backing each metric
- **DI**: `performanceModule` registered in `AppModule`

## Chart System

| Class | Purpose |
|-------|---------|
| `LineChart` | Canvas-based composable rendering multiple series with fill, grid, and axis labels |
| `ChartSeries` | Single named series with color and data points |
| `ChartConfig` | Y-axis range, label suffix, and grid line count |
| `PerformanceDataPoint` | Timestamp + value pair used by all series |

## ADB Commands Used

| Command | Class | Description |
|---------|-------|-------------|
| `cat /proc/stat` | `GetCpuStats` | Raw CPU tick counters (two readings needed for delta) |
| `cat /proc/meminfo` | `GetMemoryInfo` | System memory stats |
| `dumpsys battery` | `GetBatteryInfo` | Battery level, temp, status, health, voltage, current |
| `dumpsys meminfo <pkg>` | `GetAppMemoryInfo` | Per-app memory breakdown (PSS, heaps, graphics) |

## Dependencies

- `:core:common` — DispatcherProvider
- `:core:adb` — AdbClient, AdbDeviceMonitor, commands, parsers, models
