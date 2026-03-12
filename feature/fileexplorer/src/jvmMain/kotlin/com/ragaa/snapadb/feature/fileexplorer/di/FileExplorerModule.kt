package com.ragaa.snapadb.feature.fileexplorer.di

import com.ragaa.snapadb.feature.fileexplorer.FileExplorerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val fileExplorerModule = module {
    viewModel { FileExplorerViewModel(get(), get(), get()) }
}
