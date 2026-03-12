plugins {
    id("snapadb.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            api(libs.sqldelight.coroutines)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.jvm)
        }
    }
}

sqldelight {
    databases {
        create("SnapAdbDatabase") {
            packageName.set("com.ragaa.snapadb.database")
        }
    }
}
