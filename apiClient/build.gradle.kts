// Script di build del modulo apiClient: genera il client Kotlin Multiplatform dell'API REST a partire dal
// contratto OpenAPI in api/openapi.json, con openapi-generator. Il codice generato sta in build/, si rigenera a
// ogni build quando il contratto cambia e non va mai modificato a mano: si cambia la configurazione qui sotto.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapiGenerator)
}

/** Cartella in cui openapi-generator scrive il codice del client. */
val generatedDir = layout.buildDirectory.dir("generated/openapi")

/** Configurazione del task che genera il client: generatore "kotlin" nella variante multiplatform (Ktor). */
val generateApiClient = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    // Il contratto si passa come URI file:///, perché su Windows un percorso "C:\..." verrebbe letto come URI non valido.
    inputSpec.set(rootProject.layout.projectDirectory.file("api/openapi.json").asFile.toURI().toString())
    // Passato come stringa URI, il contratto non sarebbe un input controllato da Gradle: senza questa riga, dopo una
    // modifica di api/openapi.json il task risulterebbe aggiornato e il client non verrebbe rigenerato.
    inputs.file(rootProject.layout.projectDirectory.file("api/openapi.json"))
    outputDir.set(generatedDir.get().asFile.path)
    packageName.set("it.unical.geosentinel_mobile.api")
    apiPackage.set("it.unical.geosentinel_mobile.api")
    modelPackage.set("it.unical.geosentinel_mobile.api.model")
    configOptions.set(
        mapOf(
            "dateLibrary" to "kotlinx-datetime",
            "sourceFolder" to "src/commonMain/kotlin",
        ),
    )
    // Ogni enum generato ha un ultimo caso unknown_default_open_api per i valori nuovi che il server potrebbe
    // introdurre, così l'app non va in crash.
    additionalProperties.set(mapOf("enumUnknownDefaultCase" to "true"))
    // Template data_class.mustache modificato: aggiunge il serializer per il caso sconosciuto anche nella variante
    // multiplatform (il template originale lo fa solo con kotlinx_serialization). Quando si aggiorna il generatore va
    // ricopiato dal jar nuovo e corretto di nuovo: i due blocchi modificati sono marcati "GeoSentinel".
    templateDir.set(layout.projectDirectory.dir("templates").asFile.path)
    // kotlinx.datetime.Instant è deprecato: le date-ora si mappano su kotlin.time.Instant della libreria standard.
    typeMappings.set(mapOf("DateTime" to "kotlin.time.Instant"))
    // Si genera solo il codice: niente test, documentazione o file di progetto.
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// Task checkApiContract (./gradlew :apiClient:checkApiContract): segnala se api/openapi.json non è più uguale al
// contratto pubblicato dal backend nel repository accanto. Confronta il contenuto JSON e non i byte, perché i fine
// riga cambiano tra Windows e Linux. Se il repository del backend non c'è (per esempio in CI) non fa nulla.
tasks.register("checkApiContract") {
    val local = rootProject.layout.projectDirectory.file("api/openapi.json").asFile
    val backend = rootProject.layout.projectDirectory.file("../GeoSentinel-Backend/docs/openapi.json").asFile
    doLast {
        if (!backend.exists()) {
            logger.lifecycle("Contratto del backend non trovato in ${backend.path}: confronto saltato")
            return@doLast
        }
        val json = groovy.json.JsonSlurper()
        if (json.parse(local) != json.parse(backend)) {
            throw GradleException(
                "api/openapi.json è diverso da ${backend.path}: copia il file del backend e ricompila (il client si rigenera da solo)",
            )
        }
        logger.lifecycle("api/openapi.json è allineato al contratto del backend")
    }
}

// Target del modulo: iOS (dispositivo e simulatore Apple Silicon) e Android. I sorgenti comuni sono quelli generati.
kotlin {
    listOf(iosArm64(), iosSimulatorArm64())

    android {
        namespace = "it.unical.geosentinel_mobile.api"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateApiClient.map { "${it.outputDir.get()}/src/commonMain/kotlin" })
            dependencies {
                api(libs.ktor.client.core)
                api(libs.kotlinx.datetime)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinxJson)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// AGP cerca i baseline profile accanto ai sorgenti, quindi anche nella cartella generata: il task che li legge
// deve aspettare la generazione del client. Succede solo nelle build con R8 (release e staging).
tasks.matching { it.name.endsWith("ArtProfile") }.configureEach { dependsOn(generateApiClient) }
