package com.ragaa.snapadb.core.adb.di

import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.AdbPath
import com.ragaa.snapadb.core.adb.AdbProcess
import org.koin.dsl.module

val adbModule = module {
    single { AdbPath() }
    single { AdbProcess(get()) }
    single { AdbClient(get(), get()) }
    single { AdbDeviceMonitor(get(), get()) }
}
