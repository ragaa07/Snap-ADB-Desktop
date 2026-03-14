package com.ragaa.snapadb.feature.performance.di

import com.ragaa.snapadb.feature.performance.PerformanceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val performanceModule = module {
    viewModel { PerformanceViewModel(get(), get(), get()) }
}
