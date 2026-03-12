package com.ragaa.snapadb.feature.dashboard.di

import com.ragaa.snapadb.feature.dashboard.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardModule = module {
    viewModel { DashboardViewModel(get(), get()) }
}
