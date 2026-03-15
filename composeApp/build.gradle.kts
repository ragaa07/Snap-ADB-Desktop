import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        // All dependencies in commonMain since this project targets JVM only.
        // Move platform-specific deps to jvmMain if other targets are added.
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.adb)
            implementation(projects.core.database)
            implementation(projects.core.navigation)
            implementation(projects.core.theme)
            implementation(projects.core.ui)

            implementation(projects.feature.dashboard)
            implementation(projects.feature.appmanager)
            implementation(projects.feature.fileexplorer)
            implementation(projects.feature.shell)
            implementation(projects.feature.screenmirror)
            implementation(projects.feature.devicecontrols)
            implementation(projects.feature.performance)
            implementation(projects.feature.multidevice)
            implementation(projects.feature.deeplink)
            implementation(projects.feature.notification)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ragaa.snapadb.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Snap ADB"
            packageVersion = "1.0.0"
            description = "A desktop ADB toolkit for Android developers"
            vendor = "ragaa"

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
                bundleID = "com.ragaa.snapadb"
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menuGroup = "Snap ADB"
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon_512.png"))
                packageName = "snap-adb"
                debMaintainer = "ragaa"
            }
        }
    }
}
