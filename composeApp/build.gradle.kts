import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Версия берётся из тега GitHub (v1.2.3) или по умолчанию 1.0.0
val appVersion: String = (System.getenv("APP_VERSION") ?: "1.0.0").removePrefix("v")
val appVersionCode: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.ui)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.vlcj)
        }
    }
}

android {
    namespace = "com.adultaristocrat.cinema"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.adultaristocrat.cinema"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Постоянный ключ подписи, чтобы новые версии ставились поверх старых.
    // Для публичного распространения замените ключ и пароли (см. README).
    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = "aristocrat"
            keyAlias = "aa"
            keyPassword = "aristocrat"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/io.netty.versions.properties",
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.adultaristocrat.cinema.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx1024m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "AdultAristocrat"
            packageVersion = appVersion
            description = "Adult Aristocrat — домашний кинотеатр"
            vendor = "Adult Aristocrat"
            includeAllModules = true
            // Сюда CI кладёт VLC (папка desktopResources/windows/vlc)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("desktopResources"))

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Adult Aristocrat"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "8d3c6f0e-6b1a-4f7e-9d4c-2a7e5b1c9f30"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "adult-aristocrat"
                debMaintainer = "admin@adultaristocrat.local"
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
