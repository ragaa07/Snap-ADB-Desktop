package com.ragaa.snapadb.feature.deeplink.di

import com.ragaa.snapadb.feature.deeplink.DeepLinkViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val deepLinkModule = module {
    viewModel { DeepLinkViewModel(get(), get(), get(), get()) }
}
