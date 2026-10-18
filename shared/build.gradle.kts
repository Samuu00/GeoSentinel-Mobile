import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // Room KMP vuole un expect object (AppDatabaseConstructor): le classi expect/actual sono ancora Beta
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "it.unical.geosentinel_mobile.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        // Albero separato da commonTest: i test comuni girano già sulla JVM e su iOS, e i loro nomi con gli
        // spazi non passano da D8 sotto Android 11 (minSdk 24)
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            // Photo Picker (M4.2): già nel catalogo, la usa anche androidApp
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServices)
            implementation(libs.googleid)
            // Su Android il motore di MapLibre va scelto (OpenGL ES o Vulkan): senza, la mappa va in crash all'apertura
            // (libmaplibre-native-c.so mancante). OpenGL ES: tutti i telefoni e l'emulatore. Su iOS arriva Metal da solo.
            implementation(libs.maplibre.native.runtime.opengl)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            // client generato dal contratto (M1.1)
            implementation(project(":apiClient"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)
            implementation(libs.maplibre.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        // test di UI di Compose (M9.1) sulla JVM con Robolectric: girano nel job android della CI
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTestJunit4)
            implementation(libs.robolectric)
        }
        // test sull'emulatore: ./gradlew :shared:connectedAndroidDeviceTest (Keystore vero, M2.1)
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.testExt.junit)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    // Room in KMP: il compilatore va aggiunto per ogni target
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}