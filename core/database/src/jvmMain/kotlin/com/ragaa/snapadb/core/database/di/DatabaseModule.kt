package com.ragaa.snapadb.core.database.di

import com.ragaa.snapadb.core.database.DatabaseFactory
import com.ragaa.snapadb.database.SnapAdbDatabase
import org.koin.dsl.module

val databaseModule = module {
    single<SnapAdbDatabase> { DatabaseFactory.create() }
}
