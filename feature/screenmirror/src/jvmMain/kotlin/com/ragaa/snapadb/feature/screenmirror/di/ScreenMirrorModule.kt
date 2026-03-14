package com.ragaa.snapadb.feature.screenmirror.di

import com.ragaa.mirror.AdbExecutor
import com.ragaa.mirror.DeviceMirror
import com.ragaa.mirror.MirrorConfig
import com.ragaa.mirror.ScrcpyManager
import com.ragaa.snapadb.feature.screenmirror.AdbClientExecutor
import com.ragaa.snapadb.feature.screenmirror.ScreenMirrorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val screenMirrorModule = module {
    single<AdbExecutor> { AdbClientExecutor(get(), get()) }
    single { DeviceMirror(get(), MirrorConfig()) }
    single { ScrcpyManager() }
    viewModel { ScreenMirrorViewModel(get(), get(), get(), get(), get()) }
}
