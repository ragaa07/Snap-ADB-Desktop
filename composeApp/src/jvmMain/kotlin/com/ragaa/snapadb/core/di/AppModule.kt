package com.ragaa.snapadb.core.di

import com.ragaa.snapadb.common.di.commonModule
import com.ragaa.snapadb.core.adb.di.adbModule
import com.ragaa.snapadb.core.database.di.databaseModule
import com.ragaa.snapadb.core.navigation.di.navigationModule
import com.ragaa.snapadb.feature.dashboard.di.dashboardModule
import com.ragaa.snapadb.feature.multidevice.di.multiDeviceModule

val appModules = listOf(
    commonModule,
    adbModule,
    databaseModule,
    navigationModule,
    themeModule,
    multiDeviceModule,
    dashboardModule,
)
