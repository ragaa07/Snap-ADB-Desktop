package com.ragaa.snapadb.feature.dbinspector.di

import com.ragaa.snapadb.feature.dbinspector.DbInspectorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dbInspectorModule = module {
    viewModel { DbInspectorViewModel(get(), get(), get()) }
}
