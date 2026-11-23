# GeoSentinel Mobile

App per chi va in montagna, progetto di tesi triennale: mappa dei sentieri, segnalazione dei pericoli, rifugi e SOS,
più le schermate per i gestori del parco e i soccorritori. Kotlin Multiplatform (Android e iOS) con la UI tutta in
Compose Multiplatform; unico client del backend in `../GeoSentinel-Backend`. Gira solo in locale, contro il backend
avviato sul PC.

- [`CLAUDE.md`](CLAUDE.md): stato, stack, vincoli e comandi.
- [`docs/MASTER_PLAN.md`](docs/MASTER_PLAN.md): milestone, contratti, checkpoint e cosa manca (§9).
- [`tools/ios-device/README.md`](tools/ios-device/README.md): installare l'app su un iPhone vero dal PC.

## Moduli

- [`shared`](shared/src): tutto il codice comune (feature, rete, sessione, database, UI); `androidMain` e `iosMain`
  per le parti di piattaforma.
- [`androidApp`](androidApp): ingresso Android (Koin, `MainActivity`, push).
- [`iosApp`](iosApp/iosApp): ingresso iOS in Swift (Firebase, APNs).
- [`apiClient`](apiClient): client generato da `api/openapi.json` a ogni build.

## Comandi

```bash
./gradlew :androidApp:assembleDebug        # app Android in debug, verso il backend locale (10.0.2.2:8080)
./gradlew :shared:testAndroidHostTest      # test sulla JVM
./gradlew :shared:iosSimulatorArm64Test    # test comuni sul simulatore iOS (serve un Mac)
```
