package com.ragaa.snapadb.feature.appmanager.di

import com.ragaa.snapadb.feature.appmanager.AppManagerViewModel
import com.ragaa.snapadb.feature.appmanager.LabelResolutionService
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appManagerModule = module {
    single { LabelResolutionService(get(), get(), get(), get(), get()) }
    viewModel { AppManagerViewModel(get(), get(), get(), get()) }
}
