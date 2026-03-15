package com.ragaa.snapadb.feature.appdata.di

import com.ragaa.snapadb.feature.appdata.AppDataViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appDataModule = module {
    viewModel { AppDataViewModel(get(), get(), get()) }
}
