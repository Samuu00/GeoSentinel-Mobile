# GeoSentinel Mobile: piano di lavoro dell'app

> Claude fa da mentore, architetto e reviewer: **il codice lo scrivi tu.** Qui ci sono struttura, contratti (firme),
> vincoli e checkpoint. Un task si chiude quando passano tutti i suoi checkpoint; poi chiedi la review (vedi §6).
>
> **Stato (02/10/2026):** il backend è completo (da M0 a M7, `./mvnw clean verify` verde due volte, 42 unit + 104 IT).
> App: **M0.1–M0.5 scritte** (convenzioni e dipendenze l'01/10; configurazione per ambiente, tema, navigazione,
> Koin e CI il 02/10). L'app ha la shell completa: tema "foresta" chiaro/scuro selezionabile, barra in basso per
> ruolo, pulsante SOS, schermate segnaposto, safe area anche in orizzontale. Checkpoint ancora aperti di M0:
> Accessibility Scanner (M0.4), job verdi su una PR di prova e verifica sul simulatore iPhone (M0.5). La parte iOS
> si compila per la prima volta nel job `ios` della CI.
> Prossimo: aprire la PR di prova per chiudere M0.5, poi la catena di review di M0 (§6) e M1.1.

---

## 0. Cosa deve fare l'app

GeoSentinel serve a chi va in montagna. Il backend è in `../GeoSentinel-Backend`, il contratto in
`../GeoSentinel-Backend/docs/openapi.json` e i comportamenti che lo schema non dice in
`../GeoSentinel-Backend/docs/api-guide.md`. Leggi la guida prima di M1: retry, refresh e upload hanno regole precise.

L'app ha quattro tipi di utente, e il ruolo arriva da `GET /users/me` (campo `role`):

| Ruolo | Cosa fa nell'app |
|---|---|
| Ospite (non loggato) | Mappa con sentieri e POI (sono le uniche GET pubbliche), dettaglio del sentiero, registrazione e login |
| `HIKER` | Tutto quello dell'ospite, più: pericoli su un sentiero, segnalazione con foto, rifugi e prenotazioni, **SOS**, profilo e cancellazione dell'account |
| `PARK_MANAGER` | Verifica delle segnalazioni, stato dei sentieri, POI, ticket di manutenzione, capacità dei propri rifugi |
| `RESCUE_OPERATOR` | Push sui nuovi SOS, lista e mappa delle allerte, invio di una squadra, chiusura |
| `ADMIN` | Tutto il personale, più la creazione delle squadre di soccorso |

La gerarchia è quella del backend: `ADMIN` include `PARK_MANAGER` e `RESCUE_OPERATOR`, che includono `HIKER`.
L'app usa il ruolo solo per decidere cosa mostrare: è il server a decidere cosa si può fare.

Il caso d'uso che conta più di tutti è l'SOS con poca rete. Tutto il resto si può rimandare, quello no.

---

## 1. Decisioni

| # | Decisione | Motivo |
|---|---|---|
| A1 | **UI tutta in Compose Multiplatform**, Swift solo per il punto d'ingresso iOS e per l'SDK Firebase | Una sola UI da scrivere. Il template è già impostato così. |
| A2 | **Package per feature** in `shared` (`auth`, `trail`, `hazard`, `refuge`, `sos`, `rescue`, `staff`, `media`, `profile`, più `core`). Dentro ogni feature: `data` (repository) e `ui` (schermate e ViewModel) | Lo stesso criterio del backend. Niente strato `domain` con use case vuoti che chiamano il repository e basta: si aggiunge solo dove c'è logica vera (outbox dell'SOS, upload). |
| A3 | **Client generato da `openapi.json`** con openapi-generator (`kotlin`, `--library multiplatform`, `dateLibrary=kotlinx-datetime`) | È il motivo per cui il backend ha `operationId` stabili e campi `required`. I DTO non si scrivono a mano. Il file si copia in `api/openapi.json` e si rigenera quando il backend cambia. |
| A4 | **Ktor 3** (OkHttp su Android, Darwin su iOS) + kotlinx.serialization con `ignoreUnknownKeys = true` | È quello che usa il client generato. Il plugin `Auth` serializza i refresh da solo, come chiede il contratto. |
| A5 | **ViewModel + `StateFlow<UiState>`**, flusso unidirezionale | `lifecycle-viewmodel-compose` è già nel template. Un `UiState` immutabile per schermata, eventi come funzioni del ViewModel. |
| A6 | **Koin** per la dependency injection | Funziona su KMP senza annotation processor. |
| A7 | **Navigation Compose Multiplatform** con rotte tipizzate (`@Serializable`) | Libreria ufficiale JetBrains per CMP. |
| A8 | **Room KMP** (driver SQLite bundled) per cache del catalogo e **outbox** delle azioni da ritentare; **DataStore** per le preferenze | L'SOS e la prenotazione devono sopravvivere alla chiusura dell'app con la loro `Idempotency-Key` (ADR-0012 del backend). |
| A9 | **Token in un `SecureTokenStore` expect/actual**: Keystore su Android, Keychain su iOS | Lo chiede la guida all'API. Mai in DataStore in chiaro né nei log. |
| A10 | **Mappa con MapLibre Compose** (`maplibre-compose`) e tile vettoriali di **OpenFreeMap** | Funziona su Android e iOS, non serve una chiave Google, le tile OSM vanno bene per i sentieri. In M3 va verificata la policy d'uso delle tile. |
| A11 | **Posizione expect/actual**: `FusedLocationProviderClient` su Android, `CLLocationManager` su iOS | Serve una sola funzione (posizione corrente più aggiornamenti mentre l'SOS è attivo): una libreria in più non vale la pena. |
| A12 | **Push con gli SDK Firebase nativi**: `FirebaseMessagingService` su Android, Firebase via Swift Package Manager su iOS. Lo `shared` riceve solo il token e il payload | Il payload contiene solo `type` e `alertId` (ADR-0016 del backend): l'app rilegge i dettagli dall'API. |
| A13 | **Foto dai picker di sistema** (Photo Picker su Android, `PHPickerViewController` su iOS), **ricodificate in JPEG** nel codice di piattaforma | Il backend accetta solo JPEG, PNG e WebP fino a 10 MB: l'HEIC dell'iPhone va convertito qui. La ricodifica toglie anche gli EXIF, compresa la posizione GPS. |
| A14 | **Errori come `sealed interface ApiError`** costruito dallo slug del `ProblemDetail` | Il client decide solo sullo slug (`type`), mai su `title` o `detail`. I messaggi per l'utente sono in italiano, nelle risorse. |
| A15 | **Stringhe in `composeResources`**, italiano come lingua base | L'app è italiana; l'inglese si aggiunge dopo senza toccare il codice. |
| A16 | **Configurazione per ambiente passata dalla piattaforma**: `BuildConfig` su Android, `Info.plist` (da `Config.xcconfig`) su iOS, letti in un `AppConfig` | Niente plugin Gradle in più per due valori (URL dell'API e ambiente). |
| A17 | **Stile visivo "foresta", minimale, uguale su Android e iOS** (02/10): palette nebbia/bosco di notte con accento verde pino/salvia, card morbide, illustrazione di montagne e pini disegnata con i colori del tema, barra in basso fluttuante | Scelto dopo tre giri di prova sull'emulatore (Material 3 Expressive e stile iOS scartati). Un'identità legata alla montagna, poche decorazioni, nessuna immagine da caricare. |
| A18 | **Tema scelto dall'utente** (Sistema/Chiaro/Scuro) salvato in DataStore (`ThemeSettings`), con le barre di sistema allineate (`SystemBarsAppearance`, expect/actual) | Al sole serve il chiaro, di notte e con la batteria scarica lo scuro: la scelta non può dipendere solo dal sistema. |
| A19 | **Errori in prugna** (ruolo `error` del tema), non in rosso | Il rosso resta solo a SOS e `CRITICAL` (§2). La prugna si distingue dal rosso anche con la protanopia. |

### Vincolo dell'ambiente di sviluppo
Il PC è **Windows**: Android si compila e si prova sull'emulatore, **iOS no**. Per iOS servono un Mac con Xcode
(per il simulatore e per la configurazione APNs/Firebase) oppure, almeno per la compilazione, un runner macOS di
GitHub Actions. Ogni checkpoint "iOS" del piano si chiude solo con uno dei due. Da decidere prima di M7, quando le
push iOS diventano necessarie.

### Lacune del contratto (decidere quando si arriva lì)
Non sono bug del backend, sono cose che l'app vorrebbe e l'API oggi non dà:
- **Nessun elenco del personale.** `PATCH /maintenance-tickets/{id}/assign` vuole un `operatorId`, ma non c'è una rotta
  per cercare gli utenti `PARK_MANAGER`. Per ora l'app permette solo "assegna a me" (id da `/users/me`). Se si vuole
  scegliere qualcun altro serve una rotta nuova nel backend.
- **Nessun elenco "le mie segnalazioni"** né "pericoli vicino a me": i pericoli si leggono solo per sentiero
  (`/hazards/trail/{trailId}`).
- **I rifugi richiedono il login** (`/refuges/**` non è pubblico): l'ospite non li vede sulla mappa.
- **L'SOS richiede il login.** Un ospite, o un utente con la sessione scaduta (oltre i 30 giorni) e senza rete, non
  può lanciarlo: per questo c'è sempre il pulsante "Chiama 112" (M6).

---

## 2. Tema e linee guida dell'interfaccia

L'app si usa all'aperto: sole sullo schermo, guanti, batteria che cala, rete a tratti. Le scelte grafiche vengono da lì.

- **Material 3** (1.9.0, ultima stabile) come base dei componenti, con un tema proprio "foresta" (A17), chiaro e
  scuro. Il tema lo sceglie l'utente nel Profilo: Sistema (default), Chiaro o Scuro (A18).
- **Dove stanno i token:** `core/ui/theme/` (`Color.kt`, `Theme.kt`). I componenti leggono i colori da
  `MaterialTheme.colorScheme`, mai valori esadecimali; unica eccezione `EmergencyRed`, uguale nei due temi.
  Ruoli usati: `background` sfondo, `surfaceContainerLowest` card, `surfaceContainer` barra in basso,
  `surfaceContainerHigh` menu, `surfaceContainerHighest` binari (es. selettore del tema), `primaryContainer`
  "muschio", `onSurfaceVariant` testo secondario, `outlineVariant` separatori.
- **Colori:** nebbia `F3F5F0` e bosco di notte `0E1612` come sfondi, accento verde pino `1F5B3F` (chiaro) e salvia
  `8FD4A8` (scuro). Il **rosso (`D32F2F`) è riservato all'SOS e ai pericoli `CRITICAL`**, così il pulsante di
  emergenza non si confonde con nient'altro; gli **errori** (form, rete) usano la **prugna** del ruolo `error` (A19).
  Stato dei sentieri: `OPEN` verde, `WARNING` ambra (`B45309`), `CLOSED` rosso, sempre con un'icona o un'etichetta
  oltre al colore (per chi non distingue i colori).
- **Forme e testo:** card a 20 dp, illustrazioni e barra a 28 dp; titoli SemiBold, corpo 15–17 sp. Le illustrazioni
  sono disegnate in Compose con i colori del tema (`ForestIllustration`), così cambiano da sole con chiaro e scuro.
- **Movimento:** cambio di scheda con una dissolvenza breve; le schermate "spinte" (es. SOS) entrano da destra e la
  schermata sotto scivola via in parallasse. Ogni schermata ha uno **sfondo pieno**, altrimenti durante la
  transizione si vedono entrambe.
- **Contrasto alto** (almeno WCAG AA, AAA sul testo della mappa e sull'SOS): sotto il sole i grigi chiari spariscono.
- **Bersagli grandi:** minimo 48 dp, 72 dp per le azioni principali. Il pulsante SOS si tiene premuto 3 secondi con un
  anello che si riempie, così non parte un SOS dalla tasca.
- **Etichette di montagna:** difficoltà con la scala CAI che il backend già documenta (`EASY` = T, `MODERATE` = E,
  `DIFFICULT` = EE, `EXPERT` = EEA); pericoli e POI con nomi italiani (frana, albero caduto, bivacco, sorgente...).
  Lunghezze in km con un decimale, distanze in metri.
- **Offline visibile:** un banner quando non c'è rete, la data dell'ultimo aggiornamento sui dati in cache, le azioni
  in coda mostrate come "in invio".
- **Accessibilità:** `contentDescription` su ogni icona che porta un significato da sola (es. i simboli del
  selettore del tema); `null` sulle icone decorative accanto a un testo che dice già la stessa cosa (barra in basso,
  stati vuoti), altrimenti TalkBack legge due volte. Testo scalabile senza tagli fino al 200%, l'SOS annunciato da
  TalkBack e VoiceOver.

### Navigazione
Barra in basso, con le voci che dipendono dal ruolo:

| Voce | Chi la vede |
|---|---|
| Mappa (sentieri, POI, rifugi se loggato) | Tutti |
| Rifugi e prenotazioni | `HIKER` e superiori |
| Allerte | `RESCUE_OPERATOR`, `ADMIN` |
| Gestione (segnalazioni, ticket, rifugi) | `PARK_MANAGER`, `ADMIN` |
| Profilo (o "Accedi" per l'ospite) | Tutti |

La barra è fluttuante (capsula staccata dai bordi), con la voce attiva su una pillola color muschio; al massimo 5
voci, che vede tutte solo `ADMIN`.

Il pulsante SOS è una capsula rossa con la scritta "SOS" (72 dp) sopra la mappa, visibile a tutti. Apre la
schermata SOS, che si "spinge" sopra la mappa e ha il suo pulsante Indietro (su iPhone non c'è quello di sistema);
mentre è aperta la scheda Mappa resta attiva. Per l'ospite la schermata propone "Chiama 112" e "Accedi" (M6).

---

## 3. Struttura del progetto

```
GeoSentinel-Mobile/
├── CLAUDE.md                     # regole del progetto (M0.1)
├── .github/
│   ├── workflows/ci.yml          # job android, ios, secrets (M0.5)
│   └── dependabot.yml            # aggiornamenti settimanali di Gradle e GitHub Actions (M0.5)
├── api/openapi.json              # copia del contratto del backend (M1.1)
├── apiClient/                    # client generato (M1.1), mai modificato a mano
├── docs/MASTER_PLAN.md
├── gradle/libs.versions.toml
├── androidApp/                   # GeoSentinelApplication (Koin), MainActivity, FirebaseMessagingService
│   └── src/debug/                # network_security_config: HTTP in chiaro solo in debug
├── iosApp/                       # iOSApp.swift, AppDelegate per Firebase/APNs, Info.plist, Config.xcconfig
└── shared/src/
    ├── commonMain/kotlin/it/unical/geosentinel_mobile/
    │   ├── App.kt                # tema + Scaffold (barra, SOS) + NavHost
    │   ├── core/
    │   │   ├── config/           # AppConfig
    │   │   ├── network/          # HttpClientFactory, ApiError, RetryPolicy
    │   │   ├── session/          # UserRole; SessionManager, SecureTokenStore (expect)
    │   │   ├── db/               # AppDatabase (Room), OutboxDao
    │   │   ├── location/         # LocationProvider (expect)
    │   │   ├── push/             # PushTokenSink, PushEvent
    │   │   ├── ui/
    │   │   │   ├── theme/        # Color, Theme (GeoSentinelTheme), ThemeSettings, SystemBars (expect)
    │   │   │   ├── navigation/   # Route, BottomNavItems
    │   │   │   └── ...           # ForestIllustration, schermate segnaposto, componenti comuni
    │   │   └── di/               # KoinModules (initKoin)
    │   ├── auth/  trail/  hazard/  media/  refuge/  sos/  rescue/  staff/  profile/
    │   │   ├── data/             # <Feature>Repository
    │   │   └── ui/               # <Schermata>Screen + <Schermata>ViewModel
    │   └── composeResources/     # stringhe (values/strings.xml), icone
    ├── androidMain/              # actual: SecureTokenStore, LocationProvider, ImageEncoder, driver Room
    ├── iosMain/                  # actual: Keychain, CLLocationManager, UIImage in JPEG, driver Room
    └── commonTest/               # test con MockEngine, Turbine, Room in memoria
```

Il client generato vive nel modulo `:apiClient`, che `shared` usa come dipendenza: così una rigenerazione non tocca il
codice scritto a mano.

---

## 4. Vincoli trasversali (valgono per ogni task)

- **Il server decide.** L'app nasconde i pulsanti che il ruolo non permette, ma gestisce comunque 403 e 404.
- **Coordinate:** `GeoPointDto` ha `lat` e `lon`; i percorsi GeoJSON sono `[lon, lat]`. MapLibre usa
  `Position(longitude, latitude)`. C'è un test di regressione come quello del backend (M3).
- **Nessun dato personale nei log:** niente token, posizione, note mediche, email. Log di `Debug` solo nelle build di
  sviluppo.
- **Retry solo dove il contratto lo permette:** `429`, `409 resource-busy`, `503 storage-unavailable`, aspettando
  `Retry-After`. Gli altri 4xx non si ripetono uguali. Un errore di rete puro si ritenta solo se la richiesta è
  idempotente o ha una `Idempotency-Key`.
- **`Idempotency-Key` per azione dell'utente**, salvata nell'outbox prima di mandare la richiesta e cancellata solo con
  una risposta definitiva (2xx, oppure un 4xx che non si ritenta).
- **Dispatcher:** sul main thread solo la UI; rete e database su `Dispatchers.IO` (`Dispatchers.Default` dove IO non
  esiste).
- **Valori futuri degli enum:** un valore nuovo dal server non deve far crashare l'app (gli enum generati sono rigidi:
  vedi M1.2).
- **Commenti in italiano**, nomi in inglese, come nel backend.

---

## 5. Milestone e task

### Milestone 0: fondamenta del progetto

**M0.1 `CLAUDE.md` e convenzioni**
- `CLAUDE.md` del repo mobile con i ruoli (scrivi tu, Claude revisiona), lo stack, la struttura, le convenzioni di §4
  e i comandi.
- `.gitignore` con `local.properties`, `google-services.json`, `GoogleService-Info.plist`, `*.jks`, `build/`.
- Checkpoint:
  - [x] `local.properties` non è tracciato da git (verificato l'01/10 con `git ls-files`).
  - [x] nessun file di configurazione Firebase o keystore tracciato (verificato l'01/10 con `git check-ignore` e
    `git ls-files`).

**M0.2 Dipendenze in `libs.versions.toml`**
- Aggiungi, all'ultima versione stabile compatibile con Kotlin 2.4.20: Ktor (core, okhttp, darwin, auth,
  content-negotiation, serialization-kotlinx-json, logging), kotlinx-serialization, kotlinx-coroutines,
  kotlinx-datetime, Koin (core, compose-viewmodel), Navigation Compose Multiplatform, Room KMP con `sqlite-bundled` e
  KSP, DataStore preferences, MapLibre Compose, Coil 3 (con `coil-network-ktor3`), Kermit per i log. Nei test:
  kotlinx-coroutines-test, Turbine, `ktor-client-mock`.
- Material 3 dall'alpha alla stabile.
- Fatto l'01/10: Ktor 3.6.0, Koin 4.2.2, Navigation 2.9.2, Room 2.8.5 (+ `sqlite-bundled` 2.7.1), DataStore 1.2.1,
  MapLibre Compose 0.19.0, Coil 3.6.3, Kermit 2.2.0, coroutines 1.11.0, serialization 1.11.0, datetime 0.8.0,
  Turbine 1.2.1, KSP 2.3.12. Material 3 è a **1.9.0**: le 1.10–1.12 sono ancora alpha; è compilata per Compose 1.9 ma
  la build con Compose 1.12.1 passa. Il compilatore di Room è aggiunto per target (`kspAndroid`, `kspIosArm64`,
  `kspIosSimulatorArm64`), schemi in `shared/schemas`. Parte iOS non verificata (serve il Mac, e MapLibre su iOS
  richiede di collegare il framework nativo).
- Checkpoint:
  - [x] `./gradlew :androidApp:assembleDebug` verde (01/10).
  - [x] `./gradlew :shared:testAndroidHostTest` verde (01/10).

**M0.3 Configurazione per ambiente**
```kotlin
// core/config/AppConfig.kt
data class AppConfig(val apiBaseUrl: String, val isDebug: Boolean)
```
- Android: `buildConfigField` per `API_BASE_URL` (debug: `http://10.0.2.2:8080`), passato a Koin da
  `GeoSentinelApplication` (da M0.4; vedi sotto).
- iOS: chiave `API_BASE_URL` in `Info.plist`, letta in `MainViewController`.
- HTTP in chiaro **solo in debug**: Android con un `network_security_config` che permette `10.0.2.2` e l'IP del PC,
  iOS con `NSAllowsLocalNetworking`. In release solo HTTPS.
- Fatto il 02/10: Koin parte in M0.4, quindi per ora `MainActivity` e `MainViewController` passano la config ad
  `App(config)`; in M0.4 diventa un `single` di Koin. Cleartext Android nel source set `debug` (manifest +
  `res/xml/network_security_config.xml`), così la release non lo contiene. iOS: `API_BASE_URL[config=Debug|Release]`
  in `Config.xcconfig` (con `$()` perché `//` è un commento), `NSAllowsLocalNetworking` nell'unico `Info.plist`:
  apre solo indirizzi locali, mai HTTP verso internet. URL di release segnaposto fino a M9.3.
- Checkpoint:
  - [x] l'app debug sull'emulatore raggiunge `GET http://10.0.2.2:8080/actuator/health` (backend locale acceso):
    `200 {"status":"UP"}` dall'app su `Medium_Phone`, 02/10, con una chiamata di prova poi rimossa.
  - [x] una build release non ha il cleartext abilitato (manifest unito senza `networkSecurityConfig` né
    `usesCleartextTraffic`, 02/10).

**M0.4 Tema, navigazione e DI**
- `GeoSentinelTheme` (chiaro e scuro, colori di §2), `NavHost` con rotte tipizzate e barra in basso, `startKoin`.
- Schermate vuote per ogni voce della barra.
- Fatto il 02/10:
  - **DI:** Koin parte una volta per processo, in `GeoSentinelApplication` (Android; in `MainActivity` andrebbe in
    crash a ogni rotazione) e in `MainViewController` (iOS). `initKoin(config, preferencesPath)` restituisce `Unit`
    (`androidApp` non dipende da Koin); il modulo registra `AppConfig`, il DataStore delle preferenze e
    `ThemeSettings`. Il file delle preferenze sta in `filesDir` (Android) e in Documents (iOS).
  - **Navigazione:** `Route` sealed con `@Serializable`, `BottomNavItems` con la visibilità per ruolo
    (`UserRole`, con `GUEST` per l'utente non loggato). La voce attiva si riconosce con `hierarchy`/`hasRoute`.
  - **Tema:** stile "foresta" (A17), schema colori completo (i ruoli lasciati ai default di Material 3 sono viola),
    errori in prugna (A19), tema scelto dall'utente in DataStore (A18) con un test host su `ThemeSettings`
    (default, persistenza, valore sconosciuto → Sistema).
  - **UI:** barra in basso fluttuante; pulsante SOS a capsula sopra la mappa, con vibrazione; schermate segnaposto con
    illustrazione e simbolo della sezione; Profilo con copertina, avatar, selettore del tema a capsula (auto/sole/luna
    con cursore a molla) e, solo in debug, il selettore del ruolo. Schermata SOS "spinta" con Indietro proprio.
  - **Icone** da `material-icons-core` 1.7.3 (ultima pubblicata); i simboli del tema sono disegnati a mano.
  - **Stringhe** tutte in `composeResources/values/strings.xml`.
  - **Safe area:** `Scaffold` con `contentWindowInsets = WindowInsets.safeDrawing` (il default `systemBars` non
    include la tacca, che in orizzontale sta di lato); la barra in basso usa `safeDrawing` su lati e fondo ed è larga
    al massimo 560 dp. Verificato sull'emulatore in orizzontale con una tacca emulata (02/10); su iPhone resta un
    checkpoint di M0.5.
- Checkpoint:
  - [x] la barra cambia voci simulando i quattro ruoli (flag di debug nel Profilo; verificato sull'emulatore il 02/10).
  - [ ] tema chiaro e scuro con contrasto AA (Accessibility Scanner su Android). Contrasti calcolati a mano, manca il
    passaggio con Accessibility Scanner.

**M0.5 CI**
- GitHub Actions: job `android` (`assembleDebug` e test host) su Ubuntu, job `ios` (`compileKotlinIosSimulatorArm64` e
  `iosSimulatorArm64Test`) su macOS, gitleaks come nel backend. Dependabot per gradle e github-actions.
- Fatto il 02/10: `.github/workflows/ci.yml` con tre job (`android`, `ios`, `secrets`) e `.github/dependabot.yml`
  (in `.github/`, non in `workflows/`). Allineato al backend: `permissions: contents: read`, `checkout@v7`,
  `setup-java@v6`, gitleaks 8.30.1 come binario con SHA-256 verificato. JDK 21 Zulu, quello richiesto da
  `gradle/gradle-daemon-jvm.properties` (con un altro JDK Gradle ne scarica uno a ogni run). Dependabot raggruppa
  Kotlin, KSP e Compose in una sola PR (sono legati alla versione esatta di Kotlin). `gradlew` versionato con il bit
  di esecuzione. In locale: comando del job `android` verde, gitleaks senza segreti sui 4 commit.
- Checkpoint:
  - [ ] i job verdi su una PR di prova. Se `ios` fallisce con "Invalid device", va indicato il simulatore nel blocco
    `kotlin {}` di `shared`.
  - [ ] (con il Mac) l'app sul simulatore di un iPhone con Dynamic Island, in verticale e in orizzontale: niente sotto
    la tacca o l'indicatore Home, barra in basso e pulsante SOS dentro la safe area.

### Milestone 1: client API e rete

**M1.1 Contratto e generazione**
- Copia `../GeoSentinel-Backend/docs/openapi.json` in `api/openapi.json`.
- Modulo `:apiClient` con il plugin Gradle `org.openapi.generator`: generator `kotlin`, `library=multiplatform`,
  `dateLibrary=kotlinx-datetime`, package `it.unical.geosentinel_mobile.api`. Se il plugin non è compatibile con
  Gradle 9.5, si usa la CLI (jar) da uno script e si committa l'output in `:apiClient`.
- Controlla cosa genera per gli enum (`role`, `status`...), per `availableBedsPerNight` (mappa data → posti),
  `formFields` (oggetto libero), i `PagedModel*` e `ProblemDetail`.
- Fatto il 02/10:
  - **Generazione:** plugin `org.openapi.generator` 7.14.0, compatibile con Gradle 9.5 e con la configuration cache.
    Il codice si genera in `apiClient/build/generated/openapi` a ogni build, quando il contratto cambia, e **non si
    committa**. `openApiGenerate` parte da solo prima della compilazione. `inputSpec` è un URI `file:///`: su Windows
    il percorso `C:\...` viene letto come un URI non valido. Il modulo compila solo `src/commonMain/kotlin`; il
    `build.gradle.kts` generato (Ktor 3.1, datetime 0.6) si ignora e valgono le versioni del catalogo.
  - **Contratto:** la copia in `api/` era già uguale a quella del backend nel contenuto (59 operazioni, 47 schemi,
    OpenAPI 3.1); cambiano solo i fine riga.
  - **Cosa genera** (12 classi `*Api`, 47 modelli):
    - date-ora: `kotlin.time.Instant` (con `typeMappings`: `kotlinx.datetime.Instant` è deprecato da datetime 0.7);
      date: `kotlinx.datetime.LocalDate`;
    - `availableBedsPerNight`: `Map<String, Int>`, con la data come stringa (va convertita nel repository, M5);
    - `formFields`: `Map<String, String>`;
    - `PagedModel*`: `content` + `page: PageMetadata`, dove `size` diventa `propertySize`;
    - `ProblemDetail`: `type: String` (lo slug), `errors: List<FieldError>?`, `traceId`;
    - header: `Idempotency-Key` e `If-Match` sono parametri dei metodi;
    - ogni `*Api` ha un costruttore `(baseUrl, httpClient)`: in M1.2 si passa il client condiviso;
    - **enum rigidi** (es. `UserProfileResponse.Role`): un valore nuovo fa fallire la deserializzazione. L'opzione
      `enumUnknownDefaultCase` è ignorata dal template `multiplatform`. Da decidere in M1.2.
  - `shared` dipende da `:apiClient`: la CI compila così il client per Android e `iosSimulatorArm64`.
- Checkpoint:
  - [ ] `:apiClient` compila per Android e per `iosSimulatorArm64` (in CI). Android verde in locale (02/10); iOS si
    vede nel job `ios`.
  - [x] i campi `required` del contratto non sono nullable nelle classi generate (es. `TrailSummaryResponse.name:
    String`, verificato il 02/10).
  - [x] uno script o un task Gradle segnala se `api/openapi.json` è diverso da quello del backend:
    `./gradlew :apiClient:checkApiContract` (confronta il JSON, non i byte; senza il backend accanto non fa nulla).
    Provato allineato e con una differenza simulata (02/10).

**M1.2 `HttpClient` condiviso**
```kotlin
// core/network/HttpClientFactory.kt
fun createHttpClient(config: AppConfig, session: SessionManager, engine: HttpClientEngine): HttpClient
```
- `ContentNegotiation` con `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
- Gli enum generati falliscono su un valore sconosciuto (M1.1), e `enumUnknownDefaultCase` non ha effetto con il
  template `multiplatform`. `coerceInputValues` da solo non basta: i campi `required` non hanno un valore di default
  a cui ripiegare. Le due strade:
  - un template personalizzato (`apiClient/templates/`) che aggiunge a ogni enum un valore `UNKNOWN` e un serializer
    che ci ripiega;
  - `typeMappings` / `schemaMappings` per avere gli enum come `String`, e la mappatura a enum dell'app fatta a mano
    nei repository.

  La prima tiene i tipi, la seconda è più semplice. Si decide qui, con un test che deserializza un valore inventato.
- `Auth` / `bearer`: `loadTokens` dal `SecureTokenStore`; `refreshTokens` chiama l'operazione `refreshTokens` con
  `markAsRefreshTokenRequest()` e salva la coppia nuova; se il refresh risponde 401 svuota la sessione e porta al login.
  Il token si manda anche sulle GET pubbliche (`sendWithoutRequest`): il rate limit conta per utente, lo dice la guida.
- `HttpRequestRetry` con la politica di §4 (`Retry-After` in secondi, al massimo 3 tentativi, solo metodi sicuri o
  richieste con `Idempotency-Key`).
- `HttpTimeout`: connessione 10 s, richiesta 30 s (l'upload ha il suo timeout, M4).
- `Logging` solo in debug, con l'header `Authorization` oscurato.
- Checkpoint (`commonTest` con `MockEngine`):
  - [ ] un 401 seguito da un refresh riuscito ripete la richiesta originale una volta.
  - [ ] due richieste parallele che ricevono 401 fanno **un solo** refresh.
  - [ ] un 429 con `Retry-After: 2` si ripete dopo almeno 2 s (tempo virtuale); un 422 no.

**M1.3 Errori**
```kotlin
// core/network/ApiError.kt
sealed interface ApiError {
    val traceId: String?
    data class Validation(val fieldErrors: Map<String, String>, override val traceId: String?) : ApiError
    data class Unauthorized(override val traceId: String?) : ApiError
    data class Forbidden(override val traceId: String?) : ApiError
    data class NotFound(override val traceId: String?) : ApiError
    // capacity-exceeded, alert-already-taken, concurrent-modification, account-managed, ...
    data class Conflict(val slug: String, override val traceId: String?) : ApiError
    // business-rule, age-requirement-not-met, idempotency-key-reused, media-*
    data class BusinessRule(val slug: String, override val traceId: String?) : ApiError
    data class RateLimited(val retryAfterSeconds: Long?, override val traceId: String?) : ApiError
    data class Server(val status: Int, override val traceId: String?) : ApiError
    data object Network : ApiError { override val traceId: String? = null }
}

suspend fun <T> apiCall(block: suspend () -> T): Result<T>   // ProblemDetail -> ApiError, IOException -> Network
```
- Lo slug è l'ultima parte di `type` (`https://geosentinel.dev/problems/<slug>`). Ogni slug della tabella della guida
  all'API ha un messaggio italiano in `strings.xml`.
- Checkpoint:
  - [ ] un test per ogni slug della guida: il `ProblemDetail` finisce nel ramo giusto di `ApiError`.
  - [ ] i messaggi per campo di un errore di validazione (`errors[]`) arrivano fino al form.

### Milestone 2: sessione e account

**M2.1 `SecureTokenStore`**
```kotlin
// core/session/SecureTokenStore.kt
expect class SecureTokenStore {
    suspend fun read(): StoredTokens?
    suspend fun write(tokens: StoredTokens)
    suspend fun clear()
}
data class StoredTokens(val accessToken: String, val refreshToken: String, val accessExpiresAt: Instant)
```
- Android: chiave AES-GCM nel Keystore (`AndroidKeyStore`), token cifrati in DataStore.
  iOS: Keychain con `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- Checkpoint:
  - [ ] dopo un riavvio dell'app la sessione c'è ancora; dopo `clear()` no.
  - [ ] il file di DataStore su Android non contiene il token in chiaro (`adb shell run-as` e `strings`).
  - [ ] Keychain verificato su simulatore iOS (serve il Mac).

**M2.2 `SessionManager` e login**
```kotlin
class SessionManager(store: SecureTokenStore, authApi: AuthApi, usersApi: UsersApi) {
    val state: StateFlow<SessionState>        // Anonymous | LoggedIn(profile) | Expired
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(form: RegisterForm): Result<Unit>
    suspend fun logout()
}
```
- Dopo login e registrazione si chiama `getMyProfile`: il ruolo per la UI arriva da lì, non dal JWT.
- **Logout nell'ordine del contratto:** `unregisterDevice` (se c'è un token push), poi `logout`, poi `clear()` locale,
  anche se la rete non risponde.
- Registrazione: data di nascita con un date picker; la soglia di età la controlla il server (422
  `age-requirement-not-met`, messaggio neutro). Password da 12 caratteri a 72 byte UTF-8, controllata anche in locale
  contando i byte, non i caratteri.
- Checkpoint (contro il backend locale, utenti del seed):
  - [ ] login di `hiker@geosentinel.dev` → barra da `HIKER`; con `rescuer@` compare "Allerte".
  - [ ] password sbagliata 11 volte → il messaggio del limite (429) con il tempo d'attesa.
  - [ ] dopo il logout la sessione locale è vuota e il token push non è più registrato.

**M2.3 Profilo e cancellazione dell'account**
- `getMyProfile`, `updateMyProfile` (nome visualizzato), `changeMyPassword` (salva i token nuovi che restituisce),
  `deleteMyAccount` con doppia conferma.
- Per il personale `deleteMyAccount` risponde 409 `account-managed`: messaggio "contatta l'amministratore".
- La cancellazione si fa tutta dall'app: è un requisito di App Store e Google Play.
- Checkpoint:
  - [ ] dopo il cambio password l'app resta loggata, un altro dispositivo viene disconnesso al primo 401.
  - [ ] dopo la cancellazione si torna ospiti e il login con le vecchie credenziali fallisce.

### Milestone 3: mappa e catalogo

**M3.1 Mappa e posizione**
```kotlin
expect class LocationProvider {
    suspend fun current(): GeoPoint?                       // null se il permesso è negato o non c'è un fix
    fun updates(intervalMillis: Long): Flow<GeoPoint>
}
data class GeoPoint(val lat: Double, val lon: Double)
```
- Il permesso di posizione si chiede **quando serve** (primo "vicino a me" o primo SOS), con una spiegazione prima del
  dialogo di sistema. Senza permesso la mappa funziona lo stesso, partendo da un'area di default.
- MapLibre con lo stile OpenFreeMap, pallino della posizione, controlli di zoom grandi.
- Checkpoint:
  - [ ] con il permesso negato nessun crash, la mappa si apre sull'area di default.
  - [ ] la posizione dell'emulatore impostata in Sila compare nel punto giusto.

**M3.2 Sentieri e POI**
- `findTrailsInBoundingBox` quando la mappa si ferma (debounce di 400 ms, bbox nel formato del backend),
  `findTrailsNearby` per "vicino a me", `getTrail` per il dettaglio con il percorso disegnato,
  `findPoisByTrail` e `findPoisNearby` con un'icona per tipo.
- Dettaglio del sentiero: nome, difficoltà (T/E/EE/EEA), lunghezza, stato, descrizione, pericoli attivi
  (`findActiveHazardsByTrail`, solo da loggati), foto allegate (`findAttachedMedia` + `getMediaDownloadUrl`).
- Checkpoint:
  - [ ] **test di regressione lon/lat:** il percorso GeoJSON del seed (Sila) si disegna in Calabria e non nel Golfo di
    Aden. Test in `commonTest` sulla conversione da `[lon, lat]` a `Position`.
  - [ ] un sentiero `CLOSED` si riconosce senza leggere il colore (icona o etichetta).
  - [ ] le foto si caricano con l'URL firmato; quando l'URL scade (5 minuti) se ne chiede un altro.

**M3.3 Cache del catalogo e GET condizionali**
- Room: tabelle `trail_summary`, `trail_detail` (con il GeoJSON come testo) e `poi`, con l'`ETag` e la data
  dell'ultimo aggiornamento. La UI legge sempre dalla cache, la rete la aggiorna.
- Le GET del catalogo rimandano l'ETag in `If-None-Match`; un `304` aggiorna solo la data.
- Checkpoint:
  - [ ] in modalità aereo il sentiero già aperto si vede ancora, con "aggiornato alle ...".
  - [ ] la seconda apertura dello stesso sentiero riceve 304 (log del client in debug).

### Milestone 4: segnalazioni e foto

**M4.1 Segnalazione di un pericolo**
- Form: tipo (nomi italiani), gravità, descrizione facoltativa, posizione (attuale o toccando la mappa), sentiero
  facoltativo. `createHazard`, solo `HIKER` e superiori.
- Checkpoint:
  - [ ] la segnalazione compare tra i pericoli del sentiero solo dopo la verifica di un `PARK_MANAGER` (M8), come
    vuole il backend.

**M4.2 Upload delle foto**
```kotlin
expect class ImageEncoder {
    suspend fun toJpeg(source: PickedImage, maxSidePx: Int = 2560, quality: Int = 85): ByteArray
}

class MediaUploader(mediaApi: MediaApi, storageClient: HttpClient) {
    suspend fun upload(jpeg: ByteArray, target: MediaTarget): Result<MediaResponse>   // READY o QUARANTINED
}
```
- Flusso del contratto: `createUploadRequest` (`contentType = image/jpeg`, `sizeBytes` esatto, `entityType` ed
  `entityId`), poi POST multipart verso `uploadUrl` **senza** bearer con tutti i `formFields` e `file` per ultimo, con
  `Content-Length` (niente chunked), infine `confirmUpload`.
- Per MinIO si usa un `HttpClient` separato, senza il plugin `Auth`: il bearer non deve arrivare allo storage.
- Dopo `expiresAt` (10 minuti) si riparte da `createUploadRequest`. `confirmUpload` si può ripetere.
- Checkpoint (backend locale e MinIO del compose, con `MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000`):
  - [ ] una foto dalla galleria dell'emulatore arriva a `READY` ed è visibile nel dettaglio della segnalazione.
  - [ ] il JPEG caricato non ha EXIF GPS (controllo sul file scaricato).
  - [ ] una foto HEIC dal simulatore iOS arriva come JPEG (serve il Mac).
  - [ ] chiude il checkpoint aperto del backend (M7) "foto da emulatore/simulatore → `READY`".

### Milestone 5: rifugi e prenotazioni

**M5.1 Rifugi**
- Rifugi sulla mappa da `findRefugesNearby` (solo da loggati), dettaglio con `getRefuge` e calendario di
  `getRefugeAvailability` (posti liberi notte per notte).
- Checkpoint:
  - [ ] il rifugio piccolo del seed mostra i posti giusti per le prossime notti.

**M5.2 Prenotazione con outbox**
```kotlin
@Entity
data class PendingAction(
    @PrimaryKey val idempotencyKey: String,     // UUID creato al tocco su "Prenota"
    val kind: ActionKind,                        // BOOKING, SOS
    val payloadJson: String,
    val createdAt: Instant,
    val attempts: Int,
)
```
- La chiave si crea e si salva **prima** di chiamare `createBooking` e si cancella con una risposta definitiva.
  201 e 200 (retry) sono entrambi un successo. 409 `capacity-exceeded` e 422 si mostrano e chiudono l'azione.
- "Le mie prenotazioni" con `findMyBookings` paginato (la pagina successiva si carica in fondo alla lista),
  cancellazione con `cancelBooking`.
- Checkpoint:
  - [ ] prenoto, chiudo l'app prima della risposta (rete lenta simulata), la riapro: nessun doppione, la prenotazione c'è.
  - [ ] con il rifugio pieno compare il messaggio di `capacity-exceeded` e non resta nessuna azione in coda.

### Milestone 6: SOS (escursionista)

Il pezzo più delicato dell'app. La regola è una: **l'utente deve sempre sapere se il soccorso ha ricevuto la
richiesta.**

**M6.1 Invio**
```kotlin
class SosController(sosApi: SosApi, outbox: OutboxDao, location: LocationProvider) {
    val state: StateFlow<SosState>   // Idle | Sending(attempt) | Active(alert) | Dispatched(alert) | Failed(reason)
    suspend fun raise(medicalNotes: String?)
    suspend fun cancel()
}
```
- Pressione lunga di 3 s sul FAB, poi posizione corrente (timeout di 10 s; senza fix, l'ultima nota), poi azione `SOS`
  nell'outbox con la sua `Idempotency-Key`, poi `raiseSos`. Si ritenta finché non c'è una risposta, anche dopo un
  riavvio.
- Un 200 con un'allerta già attiva è un successo: si mostra quella.
- La schermata dell'SOS è sempre raggiungibile mentre c'è un'allerta (`getMyActiveSos` all'avvio).
- **"Chiama 112"** è sempre visibile in questa schermata, e passa in primo piano se dopo 30 s l'invio non è riuscito,
  se la sessione è scaduta o se l'utente non è loggato. Apre il dialer (`tel:112`), non chiama da solo.
- Checkpoint:
  - [ ] in modalità aereo l'SOS resta in coda come "in invio"; quando torna la rete arriva **una** allerta.
  - [ ] dieci tocchi veloci producono una sola allerta (outbox più idempotenza del server).
  - [ ] da non loggato la schermata propone 112 e login, senza crash.

**M6.2 Posizione e stato**
- Mentre l'allerta è `ACTIVE` o `DISPATCHED`, `updateSosLocation` ogni 60 s **con l'app in primo piano** e a ogni
  ritorno in primo piano. La posizione in background (permesso "sempre" e servizio in primo piano su Android,
  background mode su iOS) non c'è in questa versione. È una scelta, e va scritta nella schermata ("tieni l'app aperta").
- Lo stato si aggiorna dalle push (M7) e, se le push non arrivano, da `getMyActiveSos` ogni 30 s mentre la schermata
  è aperta.
- Annullamento con conferma (`cancelSos`).
- Checkpoint:
  - [ ] l'operatore vede la posizione aggiornarsi (`locationUpdatedAt` recente).
  - [ ] dopo il dispatch dell'operatore, la schermata dell'escursionista mostra la squadra (`assignedTeam`) entro 30 s
    anche senza push.

### Milestone 7: push e soccorso

**M7.1 Push**
```kotlin
// core/push
interface PushTokenSink { fun onNewToken(token: String) }     // chiamata da Android e da Swift

sealed interface PushEvent {
    val alertId: String
    data class SosRaised(override val alertId: String) : PushEvent
    data class SosDispatched(override val alertId: String) : PushEvent
    data class SosResolved(override val alertId: String) : PushEvent
}
```
- Il token si registra con `registerDevice` (`platform` = `ANDROID` o `IOS`) a ogni avvio da loggati e in `onNewToken`.
- Android 13+: il permesso `POST_NOTIFICATIONS` si chiede la prima volta che serve (l'operatore al login,
  l'escursionista al primo SOS). Canale "Allerte SOS" ad alta importanza.
- iOS: Firebase via SPM nell'`iosApp`, chiave APNs caricata nella console Firebase, token FCM passato a `shared`.
- Il tocco sulla notifica apre il dettaglio dell'allerta (deep link con `alertId`). Un `type` sconosciuto si ignora;
  una notifica doppia non fa danni, apre lo stesso dettaglio.
- `google-services.json` e `GoogleService-Info.plist` restano **fuori dal repo**.
- Checkpoint:
  - [ ] con il backend a `PUSH_ENABLED=true`, un SOS di `hiker@` arriva come push sull'emulatore di `rescuer@`.
  - [ ] la stessa cosa su un iPhone vero (sul simulatore le push servono la configurazione APNs: serve il Mac).
  - [ ] chiude il checkpoint aperto del backend (M7) "push FCM vere".

**M7.2 Schermate del soccorso**
- Lista delle allerte attive (`findActiveSosAlerts`, paginata) e mappa (`findSosAlertsNearby`), dettaglio
  (`getSosAlert`) con posizione, età della posizione e note mediche.
- Invio della squadra: squadre libere vicine (`findAvailableRescueTeamsNearby`), poi `dispatchRescueTeam`.
  Su 409 `alert-already-taken`: "un altro operatore l'ha già presa", e si ricarica il dettaglio.
- Chiusura con `resolveSosAlert`.
- Le note mediche si vedono solo nel dettaglio, mai nella notifica né nella lista.
- Checkpoint:
  - [ ] due operatori fanno il dispatch insieme: uno riesce, l'altro vede il messaggio giusto.
  - [ ] chiude il checkpoint aperto del backend (M7): flusso completo registrazione → login → sentieri vicini →
    segnalazione con foto → SOS → push all'operatore → cancellazione dell'account, su Android e su iOS.

### Milestone 8: personale del parco e amministrazione

**M8.1 Segnalazioni e sentieri**
- Coda delle segnalazioni per sentiero, verifica con `changeHazardStatus` (`VERIFIED`, `REJECTED`, `RESOLVED`).
- Stato del sentiero con `changeTrailStatus`. La regola del backend (`CLOSED → WARNING` vietata, 422) si mostra come
  messaggio: l'app non la duplica.
- Creazione di un POI (`createPoi`) toccando la mappa. Creare e modificare i sentieri (`createTrail`, `updateTrail`)
  disegnando il percorso è rimandato: da un PC è molto più comodo (vedi §7).

**M8.2 Ticket di manutenzione**
- Lista con filtri (`searchTickets`: stato, priorità, assegnatario), dettaglio, creazione (`createTicket` con un solo
  target), modifica, assegnazione ("assegna a me", vedi le lacune in §1), cambio di stato.
- Le modifiche mandano `If-Match` con l'`ETag` ricevuto nel dettaglio. Su 409 `concurrent-modification`: "qualcuno
  l'ha modificato, ricarico", e si mostra la versione nuova.
- Checkpoint:
  - [ ] due dispositivi modificano lo stesso ticket: il secondo riceve il 409 e vede i dati aggiornati.

**M8.3 Rifugi e squadre**
- Capacità dei propri rifugi (`updateRefugeCapacity`), con il 409 se si scende sotto gli ospiti confermati.
- `ADMIN`: creazione delle squadre di soccorso (`createRescueTeam`).

### Milestone 9: qualità e rilascio

**M9.1 Test**
- `commonTest`: client (M1), errori, outbox, `SosController` (con `MockEngine` e tempo virtuale), conversione delle
  coordinate, ViewModel principali con Turbine.
- Test di UI Compose su login, SOS e prenotazione.
- Checkpoint:
  - [ ] i test girano in CI su Android e iOS.

**M9.2 Accessibilità, prestazioni, batteria**
- TalkBack e VoiceOver su tutto il flusso dell'SOS; testo al 200%.
- Mappa senza ricaricamenti a ogni piccolo movimento (debounce), immagini ridimensionate da Coil.
- Aggiornamenti della posizione solo con un SOS attivo.

**M9.3 Privacy e store**
- Testi dei permessi (posizione, foto, notifiche) chiari e in italiano, in `strings.xml` e in `Info.plist`.
- Informativa privacy raggiungibile dal profilo; scheda "Data safety" su Play e "Privacy nutrition label" su App Store:
  posizione, foto, email, nome, note mediche.
- R8/ProGuard in release con le regole per kotlinx.serialization e Ktor; build firmata.
- Checkpoint:
  - [ ] la build release su un telefono vero funziona contro un backend HTTPS (stack prod del backend,
    `docker-compose.prod.yml`).

---

## 6. Ordine e review

```
M0 → M1 → M2 → M3 → M6 → M4 → M5 → M7 → M8 → M9
```
L'SOS (M6) viene subito dopo la mappa: è la funzione per cui l'app esiste, e i suoi problemi conviene scoprirli presto.
La parte iOS di M7 richiede il Mac (vedi §1).

Dopo ogni milestone si lancia una **catena di sola review**. Le catene di implementazione (`tdd-guide`) non si usano,
perché scriverebbero il codice al posto tuo.

| # | Milestone | Chain |
|---|---|---|
| 0 | Fondamenta | `ecc:kotlin-build-resolver,ecc:kotlin-reviewer` |
| 1 | Client e rete | `ecc:kotlin-reviewer,ecc:security-reviewer` |
| 2 | Sessione e account | `ecc:kotlin-reviewer,ecc:security-reviewer` |
| 3 | Mappa e catalogo | `ecc:kotlin-reviewer,ecc:code-reviewer` |
| 4 | Segnalazioni e foto | `ecc:kotlin-reviewer,ecc:security-reviewer` |
| 5 | Rifugi | `ecc:kotlin-reviewer,ecc:code-reviewer` |
| 6 | SOS | `ecc:kotlin-reviewer,ecc:security-reviewer` |
| 7 | Push e soccorso | `ecc:kotlin-reviewer,ecc:security-reviewer` |
| 8 | Personale | `ecc:kotlin-reviewer,ecc:code-reviewer` |
| 9 | Qualità e rilascio | `ecc:kotlin-reviewer,ecc:security-reviewer` |

```bash
/ecc:orchestrate custom "ecc:kotlin-build-resolver,ecc:kotlin-reviewer" "[Plan: docs/MASTER_PLAN.md#step-0] Review only, do not write feature code: KMP Gradle setup (libs.versions.toml with Ktor 3, Koin, Room KMP + KSP, Navigation Compose, MapLibre Compose, stable Material 3), AppConfig per platform, debug-only cleartext (network_security_config, NSAllowsLocalNetworking), theme and role-based bottom bar, CI android+ios jobs; Acceptance: assembleDebug and host tests green; release manifest has no cleartext; local.properties untracked"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-1] Review only: generated OpenAPI multiplatform client module, shared Ktor HttpClient (Auth bearer with single-flight refresh, retry only on 429, 409 resource-busy, 503 with Retry-After, timeouts, debug-only logging with Authorization redacted), ProblemDetail slug to sealed ApiError mapping; Acceptance: parallel 401s trigger one refresh; 422 never retried; no token in logs"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-2] Review only: expect/actual SecureTokenStore (Android Keystore AES-GCM + DataStore, iOS Keychain ThisDeviceOnly), SessionManager login/register/logout order (unregister device, logout, local clear), password byte-length check, profile update, change password storing new tokens, account deletion with 409 account-managed; Acceptance: no plaintext tokens on disk; logout clears local session offline; deletion returns to guest"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:code-reviewer" "[Plan: docs/MASTER_PLAN.md#step-3] Review only: MapLibre Compose map with bbox debounce, trail/POI repositories with Room cache and ETag If-None-Match handling, GeoJSON [lon,lat] to Position conversion, LocationProvider expect/actual with just-in-time permission, signed media URL refresh; Acceptance: lon/lat regression test passes; cached trail visible offline; 304 updates timestamp only"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-4] Review only: hazard report form, ImageEncoder expect/actual (HEIC to JPEG, resize, EXIF stripped), MediaUploader presigned POST flow (separate HttpClient without bearer, formFields then file last, Content-Length, confirm, restart after expiresAt); Acceptance: upload reaches READY; no bearer sent to storage; no GPS EXIF in uploaded file"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:code-reviewer" "[Plan: docs/MASTER_PLAN.md#step-5] Review only: refuges nearby and nightly availability, booking through Room outbox with Idempotency-Key persisted before the request and removed only on a final response, paged my-bookings, cancellation; Acceptance: app killed mid-request creates no duplicate; capacity-exceeded leaves no pending action"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-6] Review only: SosController state machine, 3 s long-press trigger, outbox retry across restarts with one Idempotency-Key, location fix timeout and fallback, foreground-only location updates, status polling, cancel, always-visible 112 dialer fallback, no medical notes or position in logs; Acceptance: offline SOS delivers exactly one alert when back online; logged-out user is offered 112; no PII logged"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-7] Review only: FCM integration (Android FirebaseMessagingService, iOS Firebase via SPM forwarding token to shared), device registration on start and onNewToken, notification permission flow, deep link by alertId tolerant of duplicates and unknown types, rescue operator list/map/detail, dispatch with alert-already-taken handling, resolve; Acceptance: push opens the right alert; concurrent dispatch shows the conflict message; Firebase config files not committed"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:code-reviewer" "[Plan: docs/MASTER_PLAN.md#step-8] Review only: park manager screens (hazard verification, trail status, POI creation), maintenance tickets with If-Match from ETag and concurrent-modification reload, assign-to-me, refuge capacity 409 handling, admin team creation; Acceptance: stale If-Match shows reload instead of overwriting; role-gated UI still handles 403"
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:security-reviewer" "[Plan: docs/MASTER_PLAN.md#step-9] Review only: commonTest coverage of network, outbox and SOS logic, Compose UI tests, accessibility (TalkBack/VoiceOver, 200% font), R8 rules for serialization and Ktor, permission rationale strings, privacy disclosures; Acceptance: tests green on Android and iOS in CI; release build works against HTTPS backend; no debug logging in release"
```

---

## 7. Fuori da questa versione

Scelte esplicite, non dimenticanze:
- Posizione in background durante l'SOS (servizio in primo piano su Android, background mode su iOS).
- Mappe offline scaricabili: offline restano solo i dati già visti.
- Creazione e modifica dei sentieri disegnando il percorso.
- Assegnare un ticket a un altro gestore (manca la rotta nel backend).
- Lingua inglese.
- Layout per tablet e desktop.

## 8. Come lavoriamo
1. Prendi il task successivo nell'ordine di §6.
2. Scrivi il codice. Se un contratto di questo piano non regge, dimmelo prima di cambiarlo: si aggiorna il piano.
3. Chiedimi "review <task>": leggo i file e ti segnalo i problemi con file e riga, senza riscrivere il codice.
4. Spunta i checkpoint e, a fine milestone, lancia la catena di review di §6.
