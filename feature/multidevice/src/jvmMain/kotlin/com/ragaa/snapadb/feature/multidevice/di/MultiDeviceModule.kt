package com.ragaa.snapadb.feature.multidevice.di

import com.ragaa.snapadb.feature.multidevice.MultiDeviceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val multiDeviceModule = module {
    viewModel { MultiDeviceViewModel(get(), get(), get(), get()) }
}
