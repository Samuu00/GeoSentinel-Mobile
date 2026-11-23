// Impostazioni del build Gradle: nome del progetto, repository da cui scaricare plugin e dipendenze e moduli che
// compongono l'app (l'app Android, il modulo condiviso Kotlin Multiplatform e il client generato dell'API).
rootProject.name = "geosentinel_mobile"

// Repository dei plugin Gradle. Dal repository di Google si scaricano solo i gruppi androidx, com.android e
// com.google, così le altre librerie non possono arrivare da lì per errore.
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Repository delle dipendenze dei moduli, con lo stesso filtro sui gruppi del repository di Google.
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Moduli del progetto: l'app Android, la logica e l'interfaccia condivise tra Android e iOS, il client dell'API.
include(":androidApp")
include(":shared")
include(":apiClient")