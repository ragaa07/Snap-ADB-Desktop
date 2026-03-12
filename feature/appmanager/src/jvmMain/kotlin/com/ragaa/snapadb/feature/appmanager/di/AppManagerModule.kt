package com.ragaa.snapadb.feature.appmanager.di

import com.ragaa.snapadb.feature.appmanager.AppManagerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appManagerModule = module {
    viewModel { AppManagerViewModel(get(), get(), get()) }
}
