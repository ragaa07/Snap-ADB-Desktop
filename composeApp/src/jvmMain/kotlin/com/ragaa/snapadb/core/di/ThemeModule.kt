package com.ragaa.snapadb.core.di

import com.ragaa.snapadb.core.theme.ThemeRepository
import org.koin.dsl.module

val themeModule = module {
    single { ThemeRepository(get(), get()) }
}
