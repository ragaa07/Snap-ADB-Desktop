package com.ragaa.snapadb.feature.monkey.di

import com.ragaa.snapadb.feature.monkey.MonkeyViewModel
import com.ragaa.snapadb.feature.monkey.data.MonkeyRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val monkeyModule = module {
    single { MonkeyRepository(get(), get()) }
    viewModel { MonkeyViewModel(get(), get(), get(), get()) }
}
