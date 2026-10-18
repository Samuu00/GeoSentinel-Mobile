import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Push (M7.1): google-services.json non è nel repo (si scarica dalla console Firebase). Senza, l'app si compila lo
// stesso (CI, chi non ha il progetto Firebase) e le push restano spente.
if (file("google-services.json").exists()) apply(plugin = libs.plugins.googleServices.get().pluginId)

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
        // Login con Google: client ID "Web" della Google Cloud Console (è l'audience dell'ID token, il backend lo
        // accetta in GOOGLE_CLIENT_IDS). Non è un segreto, ma è del progetto Google di chi sviluppa: sta in
        // local.properties (google.webClientId=...). Vuoto: il pulsante "Continua con Google" non compare.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("google.webClientId", "")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            // 10.0.2.2 è il PC visto dall'emulatore
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            // segnaposto finché non c'è il dominio di produzione (M9.3)
            buildConfigField("String", "API_BASE_URL", "\"https://api.geosentinel.dev\"")
            // R8 (M9.3): le regole di kotlinx.serialization, Ktor, Room e Firebase arrivano con le librerie
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // La release con R8 da provare sull'emulatore contro il backend locale (M9.3): come la release, ma firmata con
        // la chiave di debug e con l'HTTP in chiaro della build di debug (manifest e risorse di src/debug).
        // ponytail: finché il progetto resta in locale; con un backend HTTPS vero si prova direttamente la release
        create("staging") {
            initWith(getByName("release"))
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    sourceSets {
        getByName("staging") {
            manifest.srcFile("src/debug/AndroidManifest.xml")
            res.srcDir("src/debug/res")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}