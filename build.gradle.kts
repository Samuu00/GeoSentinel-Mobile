// Script di build principale del progetto Gradle. Non configura moduli: dichiara soltanto i plugin usati dai
// sottoprogetti (androidApp, shared, apiClient) con le versioni del catalogo gradle/libs.versions.toml.
plugins {
    // "apply false" carica ogni plugin una sola volta nel classloader della radice: senza, ogni sottoprogetto ne
    // caricherebbe una copia propria. I moduli poi li applicano dove servono.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.openapiGenerator) apply false
    alias(libs.plugins.googleServices) apply false
}