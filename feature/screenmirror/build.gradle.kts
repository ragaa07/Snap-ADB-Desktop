plugins {
    id("snapadb.kmp.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.theme)
            implementation(projects.core.ui)
            implementation(projects.core.adb)
            implementation(projects.lib.mirror)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }
    }
}
