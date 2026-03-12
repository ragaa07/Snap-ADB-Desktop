package com.ragaa.snapadb.feature.shell.di

import com.ragaa.snapadb.feature.shell.logcat.LogcatViewModel
import com.ragaa.snapadb.feature.shell.terminal.TerminalViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shellModule = module {
    viewModel { TerminalViewModel(get(), get(), get()) }
    viewModel { LogcatViewModel(get(), get(), get(), get()) }
}
