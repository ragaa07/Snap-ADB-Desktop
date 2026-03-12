plugins {
    id("snapadb.kmp.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.theme)

            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
