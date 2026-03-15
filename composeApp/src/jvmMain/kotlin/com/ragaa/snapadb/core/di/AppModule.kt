package com.ragaa.snapadb.core.di

import com.ragaa.snapadb.common.di.commonModule
import com.ragaa.snapadb.core.adb.di.adbModule
import com.ragaa.snapadb.core.database.di.databaseModule
import com.ragaa.snapadb.core.navigation.di.navigationModule
import com.ragaa.snapadb.feature.dashboard.di.dashboardModule
import com.ragaa.snapadb.feature.multidevice.di.multiDeviceModule
import com.ragaa.snapadb.feature.shell.di.shellModule
import com.ragaa.snapadb.feature.appmanager.di.appManagerModule
import com.ragaa.snapadb.feature.fileexplorer.di.fileExplorerModule
import com.ragaa.snapadb.feature.devicecontrols.di.deviceControlsModule
import com.ragaa.snapadb.feature.deeplink.di.deepLinkModule
import com.ragaa.snapadb.feature.notification.di.notificationModule
import com.ragaa.snapadb.feature.screenmirror.di.screenMirrorModule
import com.ragaa.snapadb.feature.appdata.di.appDataModule
import com.ragaa.snapadb.feature.performance.di.performanceModule

val appModules = listOf(
    commonModule,
    adbModule,
    databaseModule,
    navigationModule,
    themeModule,
    multiDeviceModule,
    dashboardModule,
    shellModule,
    appManagerModule,
    fileExplorerModule,
    deviceControlsModule,
    deepLinkModule,
    notificationModule,
    screenMirrorModule,
    performanceModule,
    appDataModule,
)
