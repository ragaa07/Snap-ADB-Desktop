package com.ragaa.snapadb.feature.devoptions.di

import com.ragaa.snapadb.feature.devoptions.DevOptionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val devOptionsModule = module {
    viewModel { DevOptionsViewModel(get(), get(), get()) }
}
