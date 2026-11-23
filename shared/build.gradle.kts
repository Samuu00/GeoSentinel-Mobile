// Script di build del modulo shared, il cuore dell'app in Kotlin Multiplatform: contiene logica, accesso ai dati e
// interfaccia in Compose, condivisi tra Android e iOS. Su iOS diventa il framework statico "Shared" usato da Xcode.
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
    compilerOptions {
        // Room in KMP richiede un expect object (AppDatabaseConstructor), e le classi expect/actual sono ancora in Beta:
        // l'opzione abilita esplicitamente la funzionalità.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    listOf(
        // Target iOS (dispositivo e simulatore Apple Silicon): ognuno produce il framework statico "Shared".
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
        // Le risorse Android sono abilitate e disponibili anche ai test host (Robolectric).
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        // Test sul dispositivo in un albero separato da commonTest: i test comuni girano già sulla JVM e su iOS, e i loro
        // nomi con gli spazi non passano da D8 per le versioni di Android precedenti alla 11 (minSdk 24).
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        // Dipendenze solo Android: motore HTTP OkHttp, Credential Manager e ID token di Google per il login.
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            // Activity Compose serve al selettore di foto di sistema (Photo Picker).
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServices)
            implementation(libs.googleid)
            // Su Android il motore di MapLibre va scelto esplicitamente (OpenGL ES o Vulkan): senza, la mappa va in crash
            // all'apertura perché manca libmaplibre-native-c.so. OpenGL ES funziona su tutti i telefoni e sull'emulatore;
            // su iOS il motore Metal arriva da solo.
            implementation(libs.maplibre.native.runtime.opengl)
        }
        // Dipendenze solo iOS: motore HTTP Darwin (URLSession).
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // Dipendenze comuni: client generato dell'API, Compose, ciclo di vita, Ktor, kotlinx, Koin, navigazione, Room,
        // DataStore, MapLibre, Coil e Kermit.
        commonMain.dependencies {
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
        // Dipendenze dei test comuni: kotlin-test, coroutine di test, Turbine e motore HTTP finto di Ktor.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        // Test di UI di Compose sulla JVM con Robolectric: girano nel job android della CI.
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTestJunit4)
            implementation(libs.robolectric)
        }
        // Test sull'emulatore o su un dispositivo (./gradlew :shared:connectedAndroidDeviceTest), con il Keystore vero.
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.testExt.junit)
        }
    }
}

// Cartella in cui Room esporta lo schema del database a ogni versione; va salvata nel repository per le migrazioni.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    // Room in KMP: il compilatore delle annotazioni va aggiunto a KSP per ogni target.
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}