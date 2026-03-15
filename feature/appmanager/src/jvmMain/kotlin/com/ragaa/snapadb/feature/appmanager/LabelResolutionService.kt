package com.ragaa.snapadb.feature.appmanager

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.LabelResolver
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.database.AppLabelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LabelResolutionService(
    private val deviceMonitor: AdbDeviceMonitor,
    private val adbClient: AdbClient,
    private val labelResolver: LabelResolver,
    private val appLabelRepository: AppLabelRepository,
    private val dispatchers: DispatcherProvider,
) {

    private var scope: CoroutineScope? = null

    fun start() {
        if (scope != null) return
        val serviceScope = CoroutineScope(SupervisorJob() + dispatchers.io)
        scope = serviceScope
        serviceScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                if (device == null) return@collectLatest
                resolveLabelsForDevice(device.serial)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private suspend fun resolveLabelsForDevice(serial: String) {
        val apps = withContext(dispatchers.io) {
            adbClient.execute(ListPackages(), serial)
        }.getOrNull() ?: return

        // Find packages not yet in DB
        val uncached = apps
            .filter { it.apkPath.isNotEmpty() }
            .mapNotNull { app ->
                val existing = appLabelRepository.getLabel(app.packageName)
                if (existing == null) app.packageName to app.apkPath else null
            }

        if (uncached.isEmpty()) return

        labelResolver.resolveLabels(uncached, serial) { batchLabels ->
            appLabelRepository.upsertLabels(batchLabels)
        }
    }
}
