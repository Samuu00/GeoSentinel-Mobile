import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// Client dell'API generato da api/openapi.json (M1.1). Il codice generato non si modifica a mano:
// sta in build/ e si rigenera a ogni build quando il contratto cambia.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapiGenerator)
}

val generatedDir = layout.buildDirectory.dir("generated/openapi")

val generateApiClient = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    // URI file:/// e non il percorso: su Windows "C:\..." viene letto come un URI non valido
    inputSpec.set(rootProject.layout.projectDirectory.file("api/openapi.json").asFile.toURI().toString())
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
    // ogni enum ha un ultimo caso unknown_default_open_api per i valori nuovi del server (M1.2)
    additionalProperties.set(mapOf("enumUnknownDefaultCase" to "true"))
    // data_class.mustache di openapi-generator 7.14.0 con il serializer per il caso sconosciuto anche in
    // multiplatform (il template originale lo mette solo con kotlinx_serialization). Quando si aggiorna il
    // generatore va ricopiato dal jar nuovo e ripatchato: i due blocchi sono marcati "GeoSentinel"
    templateDir.set(layout.projectDirectory.dir("templates").asFile.path)
    // kotlinx.datetime.Instant è deprecato da datetime 0.7: le date-ora usano kotlin.time.Instant
    typeMappings.set(mapOf("DateTime" to "kotlin.time.Instant"))
    // solo il codice: niente test, documentazione o file di progetto generati
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// ./gradlew :apiClient:checkApiContract — segnala se api/openapi.json non è più uguale al contratto del backend.
// Confronta il contenuto JSON e non i byte (i fine riga cambiano tra Windows e Linux).
// Senza il repo del backend accanto (es. in CI) non fa nulla.
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

// AGP cerca i baseline profile accanto ai sorgenti (src/commonMain/baselineProfiles), quindi anche nella cartella
// generata: il task che li legge deve aspettare la generazione. Succede solo nelle build con R8 (release, M9.3)
tasks.matching { it.name.endsWith("ArtProfile") }.configureEach { dependsOn(generateApiClient) }
