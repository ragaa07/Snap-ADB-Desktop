package com.ragaa.snapadb.core.sidebar

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sidebarModule = module {
    singleOf(::SidebarRepository)
    viewModel { SidebarViewModel(get(), get()) }
}
