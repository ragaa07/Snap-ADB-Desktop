package com.ragaa.snapadb.feature.devicecontrols.di

import com.ragaa.snapadb.feature.devicecontrols.DeviceControlsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val deviceControlsModule = module {
    viewModel { DeviceControlsViewModel(get(), get(), get()) }
}
