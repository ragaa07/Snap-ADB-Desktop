package com.ragaa.snapadb.feature.performance.di

import com.ragaa.snapadb.feature.performance.PerformanceViewModel
import com.ragaa.snapadb.feature.performance.session.PerformanceSessionRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val performanceModule = module {
    single { PerformanceSessionRepository(get(), get()) }
    viewModel { PerformanceViewModel(get(), get(), get(), get()) }
}
