package com.ragaa.snapadb.feature.bugreporter.di

import com.ragaa.snapadb.feature.bugreporter.BugReporterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bugReporterModule = module {
    viewModel { BugReporterViewModel(get(), get(), get()) }
}
