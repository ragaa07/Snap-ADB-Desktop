package com.ragaa.snapadb.feature.network.di

import com.ragaa.snapadb.feature.network.NetworkViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val networkModule = module {
    viewModel { NetworkViewModel(get(), get(), get()) }
}
