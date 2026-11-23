// Script di build dell'app Android: il modulo che produce l'APK. Contiene solo il punto d'ingresso Android
// (Application, Activity, servizio delle push); tutta la logica e l'interfaccia arrivano dal modulo shared.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Le push usano Firebase, configurato da google-services.json. Il file non è nel repository (si scarica dalla
// console di Firebase): se manca, l'app si compila comunque (in CI o per chi non ha il progetto Firebase) e le
// push restano disattivate.
if (file("google-services.json").exists()) apply(plugin = libs.plugins.googleServices.get().pluginId)

// Il bytecode Kotlin è compatibile con Java 11, come le opzioni Java più sotto.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "it.unical.geosentinel_mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "it.unical.geosentinel_mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // Login con Google: serve il client ID di tipo "Web" della Google Cloud Console, che è l'audience dell'ID token
        // e che il backend accetta nella sua configurazione. Non è un segreto, ma appartiene al progetto Google di chi
        // sviluppa, quindi si legge da local.properties (google.webClientId=...). Se è vuoto, il pulsante "Continua con
        // Google" non compare.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("google.webClientId", "")}\"")
    }
    // Esclude dall'APK file di licenza duplicati presenti in più librerie, che altrimenti farebbero fallire il packaging.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            // Nell'emulatore Android l'indirizzo 10.0.2.2 corrisponde al PC su cui gira il backend locale.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            // Indirizzo segnaposto: il progetto gira solo con il backend locale, quindi la release non lo usa mai.
            // R8 si prova con la build staging qui sotto.
            buildConfigField("String", "API_BASE_URL", "\"https://api.geosentinel.dev\"")
            // R8 riduce e offusca il codice; le regole per kotlinx.serialization, Ktor, Room e Firebase sono incluse nelle
            // librerie stesse.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Build "staging": la release con R8 da provare sull'emulatore contro il backend locale. È uguale alla release,
        // ma firmata con la chiave di debug e con l'HTTP in chiaro della build di debug (manifest e risorse di src/debug).
        create("staging") {
            initWith(getByName("release"))
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // La build staging riusa manifest e risorse di debug, che permettono l'HTTP in chiaro verso il backend locale.
    sourceSets {
        getByName("staging") {
            manifest.srcFile("src/debug/AndroidManifest.xml")
            res.srcDir("src/debug/res")
        }
    }
    // Sorgenti e bytecode Java compatibili con Java 11.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Compose per l'interfaccia e BuildConfig per i valori di configurazione (indirizzo dell'API, client ID di Google).
    buildFeatures {
        compose = true
        buildConfig = true
    }
}