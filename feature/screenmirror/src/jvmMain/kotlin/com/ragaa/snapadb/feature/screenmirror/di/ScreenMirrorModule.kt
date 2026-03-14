package com.ragaa.snapadb.feature.screenmirror.di

import com.ragaa.snapadb.feature.screenmirror.ScreenMirrorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val screenMirrorModule = module {
    viewModel { ScreenMirrorViewModel(get(), get(), get()) }
}
