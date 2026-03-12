package com.ragaa.snapadb.feature.notification.di

import com.ragaa.snapadb.feature.notification.NotificationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationModule = module {
    viewModel { NotificationViewModel(get(), get(), get(), get()) }
}
