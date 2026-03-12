package com.ragaa.snapadb.common.di

import com.ragaa.snapadb.common.DefaultDispatcherProvider
import com.ragaa.snapadb.common.DispatcherProvider
import org.koin.dsl.module

val commonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
