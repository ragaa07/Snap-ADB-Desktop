package com.ragaa.snapadb.core.navigation.di

import com.ragaa.snapadb.core.navigation.Router
import org.koin.dsl.module

val navigationModule = module {
    single { Router() }
}
