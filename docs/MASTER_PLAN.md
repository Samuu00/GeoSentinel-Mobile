# GeoSentinel Mobile: piano di lavoro dell'app

> Claude fa da mentore, architetto e reviewer: **il codice lo scrivi tu.** Qui ci sono struttura, contratti (firme),
> vincoli e checkpoint. Un task si chiude quando passano tutti i suoi checkpoint; poi chiedi la review (vedi §6).
>
> **Stato (04/10/2026):** il backend è completo (da M0 a M7) più il periodo di grazia del refresh token (ADR-0019,
> migration V10, 02/10): `./mvnw clean verify` verde, 42 unit + 108 IT.
> App: **M0.1–M0.5 scritte** (convenzioni e dipendenze l'01/10; configurazione per ambiente, tema, navigazione,
> Koin e CI il 02/10). L'app ha la shell completa: tema "foresta" chiaro/scuro selezionabile, barra in basso per
> ruolo, pulsante SOS, schermate segnaposto, safe area anche in orizzontale. Checkpoint ancora aperti di M0:
> Accessibility Scanner (M0.4), job verdi su una PR di prova e verifica sul simulatore iPhone (M0.5). La parte iOS
> si compila per la prima volta nel job `ios` della CI.
> **M1 fatta e revisionata** (02/10): client generato in `:apiClient`, enum tolleranti ai valori nuovi, `HttpClient`
> condiviso con refresh, retry e log, `ApiError` dallo slug con i messaggi in `strings.xml`. Catena di review di M1
> (§6) eseguita e correzioni applicate (`AuthScope`, redirect, log, eccezioni senza dati personali).
> **M2.1 e M2.2 fatte** (02/10): token cifrati (Keystore + DataStore, Keychain), `SessionManager`, schermate di
> accesso e registrazione, Profilo legato alla sessione; verificate sull'emulatore contro il backend locale.
> 70 test host + 1 test sull'emulatore.
> Aperti: test iOS in CI (dopo la correzione dei nomi), Keychain sul simulatore, token push al logout (M7).
> **M2.3 fatta** (03/10): nome, cambio password e cancellazione dell'account in `SessionManager` (niente
> `ProfileRepository`), schermate del Profilo; checkpoint verificati contro il backend locale. 86 test host.
> Restyling "carta topografica" dell'interfaccia (03/10, §2).
> **M3.1 fatta** (03/10): mappa MapLibre/OpenFreeMap sulla Sila, posizione dal modulo `location` di MapLibre (niente
> `expect class LocationProvider`), permesso solo quando serve. 92 test host.
> **Review e security review di M2 e M3.1** (03/10), due giri, correzioni applicate e riverificate:
> `SessionManager` scrive e svuota lo store sotto il mutex del refresh (niente sessione che rivive dopo un logout o
> chiusa da un 401 vecchio, anche durante un cambio password), refresh entro 20 s, cambio password e cancellazione
> non annullabili, errori dello store senza crash, Keychain con update e pulizia dopo una reinstallazione, database
> escluso dal backup, satellite bloccato sul livello EOX CC BY; sulla mappa niente lampo dello stile sbagliato e
> "La mia posizione" con un tempo massimo. 104 test host; mappa riverificata sull'emulatore. Corretto anche un test
> instabile del DataStore che faceva fallire il job `android` della CI (M2.1). Dettagli in M2.3 e
> M3.1, punti rimandati in M9.3. Aperti: job `ios` (codice del Keychain controllato solo a occhio) e simulatore.
> **M3.2 fatta** (04/10): sentieri della bbox disegnati sulla mappa per stato (chiusi tratteggiati), POI con un simbolo
> per tipo, "Sentieri vicini", dettaglio del sentiero con difficoltà, POI e, da loggati, pericoli e foto (URL
> firmato rinnovato quando scade). Nel backend il riepilogo dei sentieri ha ora il percorso semplificato e la bbox usa
> `ST_Intersects` (ADR-0020). 131 test host. Dettagli in M3.2.
> **M3.3 scritta** (04/10): cache Room del catalogo (la UI legge da lì), ETag per rotta con `If-None-Match` su dettaglio
> e POI del sentiero, "aggiornato alle" e dati salvati senza rete; checkpoint verificati sull'emulatore.
> **Review di M3 fatta** (04/10, §6): correzioni applicate, 153 test host. Dettagli in fondo a M3.
> Il job `ios` della CI falliva su `NSNumber.numberWithBool` in `AppDatabase.ios.kt` (04/10): corretto con
> `NSNumber(bool = true)`, da riverificare in CI.
> **M4.1 fatta** (04/10, anticipata rispetto all'ordine di §6): segnalazione di un pericolo dalla mappa e dal
> dettaglio del sentiero, posizione scelta su una mappa con il segnaposto al centro. 164 test host, verificata
> sull'emulatore contro il backend locale. Dettagli in M4.1.
> Prossimo: M4.3 (le mie segnalazioni, con la rotta nuova nel backend), poi M6 (SOS). Restano i checkpoint aperti di
> M3.2 (URL firmato con M4.2, simulatore).

---

## 0. Cosa deve fare l'app

GeoSentinel serve a chi va in montagna. Il backend è in `../GeoSentinel-Backend`, il contratto in
`../GeoSentinel-Backend/docs/openapi.json` e i comportamenti che lo schema non dice in
`../GeoSentinel-Backend/docs/api-guide.md`. Leggi la guida prima di M1: retry, refresh e upload hanno regole precise.

L'app ha quattro tipi di utente, e il ruolo arriva da `GET /users/me` (campo `role`):

| Ruolo | Cosa fa nell'app |
|---|---|
| Ospite (non loggato) | Mappa con sentieri e POI (sono le uniche GET pubbliche), dettaglio del sentiero senza pericoli né foto (anche `/media/**` richiede il login), registrazione e login |
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
| A11 | **Posizione dal modulo `location` di MapLibre Compose** (03/10, al posto dell'expect/actual): `LocationManager` di sistema su Android (niente Google Play), `CLLocationManager` su iOS, con stato e richiesta del permesso. Sopra, in `core/location/Location.kt`: `GeoPoint`, conversioni da/verso `Position` e `current()`/`positions()` | È già una dipendenza della mappa (non una libreria in più) e copre permessi, ciclo di vita e piattaforme. Il provider si crea nella UI (`rememberLocationState`/`rememberDefaultLocationProvider`): su Android il permesso si chiede solo con l'Activity. |
| A12 | **Push con gli SDK Firebase nativi**: `FirebaseMessagingService` su Android, Firebase via Swift Package Manager su iOS. Lo `shared` riceve solo il token e il payload | Il payload contiene solo `type` e `alertId` (ADR-0016 del backend): l'app rilegge i dettagli dall'API. |
| A13 | **Foto dai picker di sistema** (Photo Picker su Android, `PHPickerViewController` su iOS), **ricodificate in JPEG** nel codice di piattaforma | Il backend accetta solo JPEG, PNG e WebP fino a 10 MB: l'HEIC dell'iPhone va convertito qui. La ricodifica toglie anche gli EXIF, compresa la posizione GPS. |
| A14 | **Errori come `sealed interface ApiError`** costruito dallo slug del `ProblemDetail` | Il client decide solo sullo slug (`type`), mai su `title` o `detail`. I messaggi per l'utente sono in italiano, nelle risorse. |
| A15 | **Stringhe in `composeResources`**, italiano come lingua base | L'app è italiana; l'inglese si aggiunge dopo senza toccare il codice. |
| A16 | **Configurazione per ambiente passata dalla piattaforma**: `BuildConfig` su Android, `Info.plist` (da `Config.xcconfig`) su iOS, letti in un `AppConfig` | Niente plugin Gradle in più per due valori (URL dell'API e ambiente). |
| A17 | **Stile visivo "foresta", minimale, uguale su Android e iOS** (02/10): palette nebbia/bosco di notte con accento verde pino/salvia, card morbide, illustrazione di montagne e pini disegnata con i colori del tema, barra in basso fluttuante. **Rivisto il 03/10 in "carta topografica"**: curve di livello al posto dell'illustrazione, palette pietra/abete/lichene, superfici separate da tono e filo di bordo invece che da ombre, due font OFL (Barlow Semi Condensed per i titoli, Atkinson Hyperlegible Next per il testo) | Scelto dopo tre giri di prova sull'emulatore (Material 3 Expressive e stile iOS scartati). La prima versione risultava grossolana (pini a triangoli, font di sistema, card invisibili sullo sfondo): le carte dei sentieri danno un'identità di montagna più precisa e minimale, sempre senza immagini da caricare. |
| A18 | **Tema scelto dall'utente** (Sistema/Chiaro/Scuro) salvato in DataStore (`ThemeSettings`), con le barre di sistema allineate (`SystemBarsAppearance`, expect/actual) | Al sole serve il chiaro, di notte e con la batteria scarica lo scuro: la scelta non può dipendere solo dal sistema. |
| A19 | **Errori in prugna** (ruolo `error` del tema), non in rosso | Il rosso resta solo a SOS, `CRITICAL` e sentieri `CLOSED` (§2). La prugna si distingue dal rosso anche con la protanopia. |

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
- **"Le mie segnalazioni" e "pericoli vicino a me": si fanno** (04/10 e 05/10). Oggi i pericoli si leggono solo per
  sentiero (`/hazards/trail/{trailId}`); le rotte `GET /hazards/mine` (M4.3) e `GET /hazards/nearby` (M4.4) si
  aggiungono al backend.
- **I rifugi richiedono il login** (`/refuges/**` non è pubblico): l'ospite non li vede sulla mappa.
- **Anche foto e pericoli richiedono il login** (`/media/**`, `/hazards/**`): nel dettaglio del sentiero l'ospite vede
  un invito ad accedere al posto di pericoli e foto (M3.2).
- **Sentieri della mappa senza geometria: risolto nel backend il 04/10** (ADR-0020). `TrailSummaryResponse` non aveva il
  percorso, quindi per disegnare i sentieri della bbox serviva un `GET /trails/{id}` per ognuno (fino a 200, contro un
  limite di 60 richieste al minuto). Ora ha un `path` semplificato (circa 5 m); il percorso completo resta nel
  dettaglio. Nello stesso ADR la bbox passa da `ST_Contains` a `ST_Intersects`: prima, avvicinandosi con lo zoom, un
  sentiero che usciva dallo schermo spariva.
- **Risposta del refresh persa: risolto nel backend il 02/10** (ADR-0019 del backend, migration V10). Se la
  connessione cade dopo che il server ha ruotato il refresh token, l'app può riprovare con lo stesso token entro
  **60 secondi**, purché il successore non sia mai stato usato: il server emette una coppia nuova e invalida quella
  persa. Fuori dalla finestra, o se il successore è già stato usato o revocato (logout, cambio password), resta la
  revoca della famiglia. Un furto dentro la finestra viene scoperto al primo refresh dell'app legittima.
  Lato app (M2.2) non serve codice in più, basta non rompere questo: su un errore di rete `refreshTokens` restituisce
  null **e non tocca i token salvati**; il plugin `Auth` tiene il refresh token vecchio e al 401 successivo ci riprova
  da solo, di solito dentro la finestra. Un 401 del refresh resta "sessione scaduta, accedi di nuovo".
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
- **Colori** (03/10): pietra `EDF0E9` e bosco di notte `0D1411` come sfondi, card un passo sopra (`FAFBF7` / `151E19`)
  con un filo di bordo `outlineVariant`, accento verde abete `1F4535` (chiaro) e lichene `A9D3B6` (scuro). Il **rosso (`D32F2F`) è riservato all'SOS, ai pericoli `CRITICAL` e ai sentieri `CLOSED`**, così il pulsante di
  emergenza non si confonde con nient'altro; gli **errori** (form, rete) usano la **prugna** del ruolo `error` (A19).
  Stato dei sentieri: `OPEN` verde, `WARNING` ambra (`B45309`), `CLOSED` rosso, sempre con un'icona o un'etichetta
  oltre al colore (per chi non distingue i colori).
- **Forme e testo:** card a 20 dp, barra a 28 dp. Titoli (`headline*`) in Barlow Semi Condensed SemiBold, il semi-condensato
  dei cartelli; tutto il resto in Atkinson Hyperlegible Next (corpo 15–17 sp), leggibile al sole. Tutti i ruoli di
  `Typography` sono definiti, così nessun testo ricade su Roboto/SF. Ombre quasi assenti (barra 3 dp, SOS 4 dp).
- **Testata `ForestHeader`** (03/10, secondo giro): paesaggio a strati di carta che arriva sotto la barra di stato.
  Cielo color muschio con le curve di livello appena accennate (`contourPaths`: rilievo inventato tagliato con marching
  squares, una direttrice ogni cinque), due creste più chiare con la distanza, una fascia fitta di abeti (il colore più
  scuro) e il "terreno" nel colore dello sfondo, su cui poggia il contenuto. In chiaro gli strati scuriscono verso il verde
  abete, in scuro verso il nero. In cima a stati vuoti, Profilo e form; mai sulla schermata SOS. Per questo il `NavHost`
  non ha margine in alto: ogni schermata usa `WindowInsets.safeDrawing.only(Top)` per il suo contenuto.
- **Pulsanti** (03/10): una sola azione evidente per schermata, a tutta larghezza a capsula (`PrimaryPillButton`), e
  l'alternativa come link sotto ("Non hai un account? Registrati", `FooterLink`): niente due pulsanti grandi in competizione. Nei form l'azione principale e il link alternativo stanno in
  fondo, fissi, sopra la tastiera (zona del pollice). Indietro è un tondo da 48 dp in alto a sinistra.
- **Movimento:** cambio di scheda con una dissolvenza breve; le schermate "spinte" (es. SOS) entrano da destra e la
  schermata sotto scivola via in parallasse. Ogni schermata ha uno **sfondo pieno**, altrimenti durante la
  transizione si vedono entrambe.
- **Form** (M2.2, `core/ui/FormControls.kt`): campi come piccole card (`surfaceContainerLowest`, angoli da 20 dp)
  con un bordo sottile `outlineVariant` che diventa `primary` (2 dp) col focus ed `error` con un errore; etichetta
  sempre visibile, messaggio sotto il campo; errore generale in un riquadro `errorContainer` annunciato dagli screen
  reader; invio con `PrimaryPillButton` (capsula). Nel Profilo da ospite due capsule compatte affiancate: "Accedi"
  piena e "Registrati" solo bordo (`outline`, così il bordo si vede). I form nuovi (M2.3, M4) riusano questi
  controlli.
- **Contrasto alto** (almeno WCAG AA, AAA sul testo della mappa e sull'SOS): sotto il sole i grigi chiari spariscono.
- **Bersagli grandi** (regola precisata il 02/10): **72 dp per le azioni d'emergenza** (pulsante SOS, "Chiama 112"),
  **56 dp per il pulsante d'invio dei form**, **48 dp minimo per tutto il resto** (es. le capsule Accedi/Registrati
  del Profilo). Prima la regola diceva "72 dp per le azioni principali", troppo vago: nei form dava pulsanti
  sproporzionati. Il pulsante SOS si tiene premuto 3 secondi con un
  anello che si riempie, così non parte un SOS dalla tasca.
- **Etichette di montagna:** difficoltà con le quattro parole del backend: Facile, Media, Difficile, Per esperti
  (04/10: niente più scala CAI T/E/EE/EEA, la sigla la assegna chi gestisce il sentiero e non si ricava da quattro
  valori); pericoli e POI con nomi italiani (frana, albero caduto, bivacco, sorgente...).
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
    │   │   ├── network/          # HttpClientFactory (+ AuthScope), RetryPolicy, ApiCall, ApiError, HttpEngine (expect)
    │   │   ├── session/          # UserRole, SessionManager, SecureTokenStore, TokenCodec, SessionModels
    │   │   ├── db/               # AppDatabase (Room), OutboxDao
    │   │   ├── location/         # Location.kt: GeoPoint, lon/lat, current() e positions() sul provider di MapLibre
    │   │   ├── push/             # PushTokenSink, PushEvent
    │   │   ├── ui/
    │   │   │   ├── theme/        # Color, Theme (GeoSentinelTheme), ThemeSettings, SystemBars (expect)
    │   │   │   ├── navigation/   # Route, BottomNavItems
    │   │   │   └── ...           # ContourLines, FormControls, UiMessage, schermate segnaposto
    │   │   └── di/               # KoinModules (initKoin)
    │   ├── auth/                 # data/PasswordRules; ui/ Login e Register (Screen + ViewModel)
    │   ├── trail/  hazard/  media/  refuge/  sos/  rescue/  staff/  profile/
    │   │   ├── data/             # <Feature>Repository
    │   │   └── ui/               # <Schermata>Screen + <Schermata>ViewModel
    │   └── composeResources/     # stringhe (values/strings.xml), icone
    ├── androidMain/              # actual: Keystore + DataStore, OkHttp, ImageEncoder, driver Room
    ├── iosMain/                  # actual: Keychain, Darwin, CLLocationManager, UIImage in JPEG, driver Room
    ├── commonTest/               # test con MockEngine (backend finto in SessionTestSupport), Room in memoria
    ├── androidHostTest/          # test sulla JVM che usano API Android (DataStore, cifratura)
    └── androidDeviceTest/        # test sull'emulatore (Keystore vero); albero separato, nomi senza spazi
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
  - [x] `:apiClient` compila per Android e per `iosSimulatorArm64` (in CI): nel job `ios` del 02/10 compilano
    `:apiClient` e tutto `:shared` (Keychain compreso). Lo stesso job falliva solo sui test: Kotlin/Native non accetta
    la virgola nei nomi tra backtick (10 nomi corretti).
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
- Fatto il 02/10:
  - **Firma reale:** `HttpClientFactory.createHttpClient(config, sessionRepository, engine)`. Al posto di
    `SessionManager` (M2.2) il client dipende da un'interfaccia minima, `SessionRepository` (`core/session/
    SessionModels.kt`): `getTokens()`, `refreshTokens(refreshToken): AuthTokens?`, `clearSession()`. In M2
    `SessionManager` la implementa.
  - **Enum:** template personalizzato (la prima strada). Con `enumUnknownDefaultCase=true` (in
    `additionalProperties`: in `configOptions` è ignorato) ogni enum ha un ultimo caso `unknown_default_open_api`;
    `apiClient/templates/data_class.mustache` è il template di 7.14.0 con il serializer che ci ripiega anche in
    `multiplatform` (blocchi marcati "GeoSentinel"). Gli enum sono tutti incorporati nei modelli, quindi
    `typeMappings` a `String` non era applicabile. Quando si aggiorna il generatore il template va ricopiato dal jar
    e ripatchato. Nei `when` dell'app il caso sconosciuto va gestito (es. ruolo sconosciuto → vista da `HIKER`).
  - **Refresh:** `SessionRepository.refreshTokens` svuota la sessione **solo** su un 401: un errore di rete
    restituisce null e l'utente resta loggato (in montagna la rete va e viene, e senza sessione non c'è SOS). Il
    plugin fa un refresh alla volta dentro il client; fuori dal client serve il `Mutex` del repository (M2.2).
  - **Dove vale il plugin `Auth`** (dalla review di sicurezza di M1): su un 401 di una richiesta partita senza token,
    Ktor la **rimanda con il token**. Per questo il plugin `AuthScope`, installato prima di `Auth`, mette
    `AuthCircuitBreaker` su ogni richiesta che non va al backend (schema, host **e porta** di `apiBaseUrl`) e su
    `/auth/login`, `/auth/register`, `/auth/refresh`. Lì niente bearer, niente richiesta ripetuta, niente refresh:
    un 401 del login (password sbagliata) è definitivo e non consuma il refresh token. Così anche login e refresh
    possono usare il client condiviso: un solo client autenticato per tutta l'app. `/auth/logout` il bearer lo vuole
    e lo riceve.
  - **Redirect:** `followRedirects = false` (l'API non ne fa).
  - **Retry:** lo slug sta nel body, che `retryIf` non può leggere; tra i 409 il backend manda `Retry-After` solo con
    `resource-busy`, quindi si ritentano 429, 409 e 503 **solo se c'è `Retry-After`**, e solo fino a 10 s
    (`MAX_RETRY_AFTER_SECONDS`): oltre, il 429 arriva alla UI con il tempo d'attesa (login bloccato, M2.2). Errori di
    rete: solo GET, PUT e DELETE o richieste con `Idempotency-Key`; mai una cancellazione.
  - **Log:** Kermit, solo in debug, `Authorization` oscurato; non si loggano le richieste con `lat`, `lon` o `bbox`
    nell'URL, né `/devices` (il token push sta nel path) né `/auth/`.
  - `Content-Type` lo mette il client generato sulle richieste con body; `coerceInputValues` non si usa.
- Checkpoint (`commonTest` con `MockEngine`, `core/network/HttpClientTest.kt`):
  - [x] un 401 seguito da un refresh riuscito ripete la richiesta originale una volta.
  - [x] due richieste parallele che ricevono 401 fanno **un solo** refresh.
  - [x] un 429 con `Retry-After: 2` si ripete dopo almeno 2 s (tempo virtuale); un 422 no.
  - [x] in più: 409 `resource-busy` ritentato e 409 senza `Retry-After` no; `Retry-After` oltre 10 s non
    ritentato; errore di rete ritentato solo su GET o con `Idempotency-Key`; niente token verso altri host; refresh
    senza token nuovi → resta il 401 e il client non svuota la sessione; ruolo sconosciuto →
    `unknown_default_open_api`; dopo la review: niente token su un'altra porta, un 401 da un altro host o dal login
    non fa rimandare la richiesta né partire il refresh, i redirect non si seguono (14 test). Verificato anche
    rompendo apposta il codice: i test falliscono (02/10).

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
- Fatto il 02/10 (firma cambiata):
  - `suspend fun <T : Any> apiCall(block: suspend () -> HttpResponse<T>): Result<T>`, con l'`HttpResponse` del
    client generato. Il client generato non lancia eccezioni sui 4xx/5xx: l'errore si legge da `success = false`.
    Il fallimento è un `ApiException(error: ApiError)`. Solo `IOException` (timeout compresi, anche durante la
    lettura del body) diventa `Network`; la cancellazione si propaga; un 2xx che non si deserializza lancia
    `ContractViolationException`, senza la causa: il messaggio di kotlinx.serialization contiene un pezzo del JSON
    ricevuto (email, posizione). Anche il messaggio di `ApiException` ha solo il tipo e il `traceId`.
  - Il body si decodifica nel `ProblemDetail` **generato** (`errors[]` ha `field` e `message`), dal testo:
    `ContentNegotiation` non gestisce `application/problem+json`. Senza body valido (es. proxy) decide lo status.
  - Lo status sceglie il ramo (ogni slug della guida ha uno status fisso), lo slug lo precisa; `title` e `detail` mai.
  - Messaggi: `suspend fun ApiError.userMessage(): String` (dal ViewModel) e `messageResource()`. Slug e status non
    si mostrano all'utente. Nei test host si verifica `messageResource()`: `getString` sulla JVM non ha le risorse
    Android.
- Checkpoint (`commonTest`, `core/network/ApiErrorTest.kt`, 12 test, 02/10):
  - [x] un test per ogni slug della guida: il `ProblemDetail` finisce nel ramo giusto di `ApiError`, e ogni slug ha
    il suo messaggio (uno slug sconosciuto ha quello generico del ramo).
  - [x] i messaggi per campo di un errore di validazione (`errors[]`) arrivano fino al form: fino a `apiCall` con
    `Validation.fieldErrors` (testato con `MockEngine`), poi nei form di accesso e registrazione con messaggi italiani
    nostri (M2.2, `AuthViewModelsTest`).
  - [x] in più: 2xx → successo; `Retry-After` portato in `RateLimited`; pagina HTML di un proxy → `Server`;
    `IOException` → `Network`; la cancellazione si propaga; un 2xx fuori contratto non diventa `ApiException`.
    Verificato rompendo apposta il codice: i test falliscono.

### Milestone 2: sessione e account

**M2.1 `SecureTokenStore`**
```kotlin
// core/session/SecureTokenStore.kt (contratto attuale; quello iniziale era un expect class, vedi "Fatto il 02/10")
interface SecureTokenStore {
    suspend fun read(): AuthTokens?
    suspend fun write(tokens: AuthTokens)
    suspend fun clear()
}
internal expect fun createSecureTokenStore(filesDir: String): SecureTokenStore
```
- Android: chiave AES-GCM nel Keystore (`AndroidKeyStore`), token cifrati in DataStore. Il file dei token va
  **escluso dal backup** (`android:dataExtractionRules` e `fullBackupContent`; oggi `allowBackup="true"`): la chiave
  del Keystore non si salva, e dopo un ripristino i token cifrati non si leggerebbero più (review di M1).
  iOS: Keychain con `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- Fatto il 02/10 (firma cambiata):
  - `interface SecureTokenStore { read(): AuthTokens?; write(AuthTokens); clear() }` invece di `expect class`: in
    M2.2 `SessionManager` si testa in commonTest con un fake. Le implementazioni arrivano da
    `internal expect fun createSecureTokenStore(filesDir)`, registrata in Koin. Un solo tipo per i token,
    `AuthTokens` (in `SessionModels.kt`), con `accessExpiresAt` non nullo come in `TokenResponse`; il suo
    `toString()` non stampa i token. `StoredToken`/`StoredTokens` non esistono più.
  - `initKoin(config, filesDir)`: la piattaforma passa la cartella dei dati, i nomi dei file li decide `shared`.
  - Android (`AndroidSecureTokenStore`): AES-256-GCM con la chiave `geosentinel_session` nell'Android Keystore, IV in
    testa al testo cifrato, tutto in un DataStore a parte (`geosentinel.session.preferences_pb`), su
    `Dispatchers.IO`. Il file è escluso dal backup e dal trasferimento (`data_extraction_rules.xml`, Android 12+;
    `backup_rules.xml`, fino ad Android 11). Se i dati non si decifrano più (chiave diversa dopo un ripristino,
    file rovinato) `read()` li cancella e restituisce null: niente crash, si rifà il login.
  - iOS (`KeychainTokenStore`): una voce `kSecClassGenericPassword` con
    `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (leggibile dopo il primo sblocco anche per le push, mai nei
    backup); scrittura = delete + add. Compilata solo nel job `ios` della CI.
  - Test: `TokenCodecTest` (commonTest), `AndroidSecureTokenStoreTest` (JVM, chiave software, storage Okio perché
    su Windows lo storage di default non sostituisce il file alla seconda scrittura) e
    `AndroidSecureTokenStoreDeviceTest` sull'emulatore con il Keystore vero
    (`./gradlew :shared:connectedAndroidDeviceTest`; albero separato da commonTest, nomi senza spazi per D8).
  - 03/10: il job `android` della CI falliva su `AndroidSecureTokenStoreTest` ("multiple DataStores active",
    `IllegalStateException` da `OkioStorage`). Il "riavvio" del test chiudeva lo scope del DataStore con `cancel()`
    senza aspettarlo, e il DataStore successivo trovava il file ancora aperto: una corsa che su Windows passava e su
    Linux no. Ora `cancelAndJoin()`. Regola: nei test, prima di riaprire lo stesso file DataStore si aspetta la
    chiusura dello scope precedente.
- Checkpoint:
  - [x] dopo un riavvio dell'app la sessione c'è ancora; dopo `clear()` no (host test, 02/10).
  - [x] il file di DataStore su Android non contiene il token in chiaro: verificato sul file vero
    nell'emulatore `Medium_Phone` con il Keystore (device test) e sulla JVM (02/10). Verificato anche rompendo
    apposta la cifratura: i test falliscono.
  - [ ] Keychain verificato su simulatore iOS (serve il Mac); la compilazione si vede nel job `ios`.

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
- **Cache dei token del client** (dalla review di M1): il plugin `Auth` tiene in memoria i token di `loadTokens`.
  Dopo login, logout, cambio password e `clearSession()` va chiamato
  `client.authProvider<BearerAuthProvider>()?.clearToken()`. Altrimenti dopo un nuovo login partono i token del
  primo utente, e al primo 401 il plugin manda un refresh token già sostituito: il server lo vede riusato e revoca
  la sessione. Test: "dopo il login, la richiesta successiva manda il bearer nuovo".
- **Un refresh alla volta in tutta l'app:** `SessionManager` implementa `SessionRepository` con un `Mutex` attorno a
  `refreshTokens`; dentro il lock rilegge i token salvati e, se sono già cambiati, li restituisce senza chiamare il
  server. Un solo `SessionManager` (singleton Koin) e un solo `HttpClient` autenticato (outbox, Coil e
  `createUploadRequest` compresi; lo storage MinIO ha il suo client senza `Auth`, M4.2). Test: due `refreshTokens`
  in parallelo fuori dal client → una sola chiamata al server.
- **Login, registrazione e refresh** passano dal client condiviso (il plugin `Auth` lì è spento, M1.2). Il refresh
  non ha retry automatico di `HttpRequestRetry` (POST senza `Idempotency-Key`: va mantenuto così); il nuovo tentativo
  dopo una risposta persa lo fa il plugin `Auth` al 401 successivo, coperto dal periodo di grazia del backend (§1).
- **Enum sconosciuti:** un DTO ricevuto con `unknown_default_open_api` non si rimanda al server così com'è (il server
  risponderebbe 400); nei `when` il caso sconosciuto si gestisce (es. ruolo sconosciuto → vista da `HIKER`).
- **Logout nell'ordine del contratto:** `unregisterDevice` (se c'è un token push), poi `logout`, poi `clear()` locale,
  anche se la rete non risponde.
- Registrazione: data di nascita con un date picker; la soglia di età la controlla il server (422
  `age-requirement-not-met`, messaggio neutro). Password da 12 caratteri a 72 byte UTF-8, controllata anche in locale
  contando i byte, non i caratteri.
- Fatto il 02/10 (firma cambiata):
  - `SessionManager(store, httpClient: () -> HttpClient, apiBaseUrl, pushToken = { null })`: crea da sé
    `AuthenticationApi`, `UserProfileApi`, `DevicesApi`; il client arriva come funzione perché il client usa
    `SessionManager` per i token (in Koin niente ciclo). Stati: `Loading | Anonymous | LoggedIn(profile?) | Expired`;
    `profile` null = sessione salvata ma profilo non letto (avvio offline: vista da `HIKER`, l'SOS resta possibile).
    `SessionState.role` dà il ruolo per la barra; un ruolo sconosciuto diventa `HIKER`. `restore()` all'avvio.
    `clearSession()` tolta da `SessionRepository`: la sessione la chiude solo `SessionManager`.
  - Refresh: `Mutex` + rilettura dello store; solo un 401 chiude la sessione (`Expired`); rete, 429 e 5xx lasciano i
    token. Login e registrazione svuotano la cache del plugin `Auth` (`clearToken`). Logout: `unregisterDevice` (in
    M7, quando c'è il token push), `logout`, pulizia locale in `NonCancellable`.
  - UI: `LoginScreen`/`RegisterScreen` (`auth/ui`, ViewModel con `UiState` e `UiMessage` come risorsa), rotte
    `Login`/`Register` spinte senza barra in basso, Profilo legato alla sessione (Accedi/Crea un account/Esci,
    "sessione scaduta"). Il selettore del ruolo di debug di M0.4 è stato tolto. Password: minimo 12 in unità UTF-16
    (come `@Size` di Java), massimo 72 byte UTF-8 (`auth/data/PasswordRules`). Gli errori per campo del server
    arrivano al form con messaggi italiani nostri; 401 sul login = "Email o password non corretti".
  - Stile: dopo due giri di prova, form con `FormControls` (§2) e Profilo da ospite con le capsule affiancate.
    "Crea un account" del Profilo si chiama ora "Registrati"; i form si collegano tra loro ("Non hai un account?
    Registrati" / "Hai già un account? Accedi"); la data di nascita si sceglie toccando tutto il campo.
  - Test: `SessionManagerTest` (17), `AuthViewModelsTest` (10), `PasswordRulesTest` (3), con il backend finto di
    `SessionTestSupport`. Verificati anche rompendo apposta `clearToken` e il controllo dei token già rinnovati.
- Checkpoint (contro il backend locale, utenti del seed, emulatore `Medium_Phone`, 02/10):
  - [x] login di `hiker@geosentinel.dev` → barra da `HIKER` (Mappa, Rifugi, Profilo); con `rescuer@` compare
    "Allerte".
  - [x] password sbagliata 11 volte → "Troppe richieste. Riprova tra 67 secondi." (429 con `Retry-After`).
  - [x] dopo il logout la sessione locale è vuota (file della sessione a 0 byte) e si torna ospiti. Da loggati il file
    è di 468 byte cifrati, senza stringhe leggibili né JWT.
  - [ ] il token push non è più registrato dopo il logout: si verifica in M7, quando le push esistono.

**M2.3 Profilo e cancellazione dell'account**
- `getMyProfile`, `updateMyProfile` (nome visualizzato), `changeMyPassword` (salva i token nuovi che restituisce),
  `deleteMyAccount` con doppia conferma.
- Per il personale `deleteMyAccount` risponde 409 `account-managed`: messaggio "contatta l'amministratore".
- La cancellazione si fa tutta dall'app: è un requisito di App Store e Google Play.
- Fatto il 03/10 (contratto cambiato):
  - **Niente `ProfileRepository`.** L'interfaccia scritta per M2.3 restituiva il DTO generato e non poteva toccare
    la sessione, ma tutte e tre le operazioni la cambiano: il cambio password ruota i token (vanno salvati e va
    svuotata la cache del plugin `Auth`), la cancellazione chiude la sessione, il nome nuovo va nel profilo di
    `SessionState`. Stanno quindi in `SessionManager`: `updateDisplayName(name)`, `changePassword(current, new)`,
    `deleteAccount()`, tutte `Result<Unit>`. `profile/` ha solo `ui/` (niente strato vuoto, A2).
  - UI: `ProfileScreen` (sostituisce il segnaposto) con le righe Nome, Cambia password, Esci e, in una card a parte e
    in prugna, Elimina account con doppia conferma (cosa succede: prenotazioni future e SOS attivo annullati, foto
    cancellate; poi "Elimina definitivamente"). Form `EditNameScreen` e `ChangePasswordScreen` (rotte `EditName`,
    `ChangePassword`, senza barra in basso). `FormScaffold`, `FooterLink` e `PasswordField` spostati in
    `core/ui/FormControls.kt`. ViewModel in `profile/ui/ProfileViewModels.kt`.
  - Errori: password attuale errata (422 `business-rule`) sul campo; personale (409 `account-managed`) con il
    messaggio "tramite l'amministratore" e la sessione intatta.
  - Test: 6 in `SessionManagerTest` (backend finto esteso con PATCH/DELETE `/users/me` e `/users/me/password`) e 8 in
    `ProfileViewModelsTest`; 86 test host in tutto. Verificato anche rompendo apposta `clearToken` dopo il cambio
    password: il test fallisce.
- Dopo la review del 03/10 (regole della sessione, valgono anche per le milestone dopo):
  - lo store si scrive e si svuota solo con `refreshMutex` preso; `refreshTokens` lo tiene per tutta la richiesta,
    così login, logout, cambio password e cancellazione non si accavallano mai con un refresh. Il mutex non avvolge
    mai una richiesta autenticata (il plugin `Auth` vi richiama `refreshTokens`: deadlock);
  - refresh entro `RefreshTimeout` (20 s): oltre, conta come errore di rete (i token restano, ADR-0019 copre una
    risposta persa);
  - durante un cambio password (`passwordChanges` > 0) il 401 di un refresh partito con il token appena revocato
    non chiude la sessione: i token nuovi arrivano con la risposta del cambio;
  - `changePassword` e `deleteAccount` in `NonCancellable`; logout con il refresh token riletto subito prima
    dell'invio (e una seconda revoca se il plugin lo rinnova a metà);
  - errori dello store: mai un crash. Login e registrazione restituiscono un errore generico, `restore()` parte da
    ospite, `clear()` si riprova una volta (se fallisce ancora, la sessione si chiude in memoria e si riapre al
    riavvio: annotato in M9.3);
  - iOS: Keychain con `SecItemUpdate` e add solo se manca; alla prima lettura dopo un'installazione la sessione
    rimasta nel Keychain si cancella (flag in `NSUserDefaults`). Chi era già loggato con una build precedente esce
    una volta: nessuna versione distribuita;
  - backup Android: esclusi i token e il database (da M4/M6 posizioni SOS, note mediche, outbox);
  - test: 9 in più in `SessionManagerTest` (concorrenza con un refresh in volo, annullamento, store che fallisce,
    `restore()` in parallelo, cambio password con refresh rifiutato); 6 falliscono sul `SessionManager` di prima.
- Checkpoint (backend locale, emulatore `Medium_Phone`, 03/10):
  - [x] dopo il cambio password l'app resta loggata (anche dopo un riavvio), un altro dispositivo riceve 401 e la
    vecchia password non vale più. Password attuale sbagliata: errore sul campo.
  - [x] dopo la cancellazione (account di prova registrato dall'app) si torna ospiti, il file della sessione è vuoto e
    il login con le vecchie credenziali risponde 401. `rescuer@`: 409 `account-managed`.
  - [x] in più: con il refresh revocato dal server il Profilo mostra "La sessione è scaduta", non "Ospite".

### Milestone 3: mappa e catalogo

**M3.1 Mappa e posizione**
```kotlin
// core/location/Location.kt, sopra org.maplibre.compose.location.LocationProvider (A11)
data class GeoPoint(val lat: Double, val lon: Double)
fun GeoPoint.toPosition(): Position                         // Position(longitude, latitude)
suspend fun LocationProvider.current(timeout: Duration = 10.seconds): GeoPoint?   // null: niente permesso o fix
fun LocationProvider.positions(interval: Duration): Flow<GeoPoint>
```
- Il permesso di posizione si chiede **quando serve** (primo "vicino a me" o primo SOS), con una spiegazione prima del
  dialogo di sistema. Senza permesso la mappa funziona lo stesso, partendo da un'area di default.
- MapLibre con lo stile OpenFreeMap, pallino della posizione, controlli di zoom grandi.
- Fatto il 03/10 (contratto cambiato):
  - **Niente `expect class LocationProvider`.** Quella scritta per M3.1 non aveva `actual` (la build non compilava) e
    un `expect class` senza costruttore non può ricevere il `Context`/l'Activity che Android vuole per il permesso.
    Il modulo `location` di MapLibre Compose fa già tutto (A11); in `Location.kt` restano `GeoPoint`, le conversioni
    lon/lat e `current()`/`positions()` per sentieri vicini (M3.2) e SOS (M6).
  - `trail/ui/MapScreen.kt` sostituisce il segnaposto: stile OpenFreeMap `liberty` (chiaro) o `fiord` (scuro: `dark` era troppo nero), partenza
    sulla Sila (39,33 / 16,55, zoom 9,5), pallino `LocationIndicatorLayer`, controlli da 56 dp a destra (zoom e "La mia
    posizione", simboli disegnati: `material-icons-core` non ha il "meno" né il mirino). Overlay di default di MapLibre
    (scala, bussola, attribuzione OSM) senza il logo MapLibre; la "i" dell'attribuzione in basso a sinistra, nei colori del tema. La mappa arriva sotto la barra di stato e lascia spazio all'SOS in basso.
  - Permesso solo al tocco su "La mia posizione": spiegazione, poi dialogo di sistema; se negato per sempre, rimando
    alle impostazioni. Manifest: `ACCESS_COARSE/FINE_LOCATION` (solo primo piano); iOS: `NSLocationWhenInUseUsageDescription`.
  - **Motore di MapLibre su Android:** da aggiungere a mano (`maplibre-native-ffi-runtime-opengl`, stessa versione di
    `maplibre-native-ffi`): senza, la mappa va in crash all'apertura (`libmaplibre-native-c.so` mancante). Su iOS il
    runtime Metal arriva da solo con maplibre-compose (niente SPM in Xcode, da confermare nel job `ios`).
  - Test: `LocationTest` (6): regressione lon/lat sulla Sila, primo fix, niente permesso, timeout, piattaforma senza
    provider, `positions`. 92 test host.
- Checkpoint (emulatore `Medium_Phone`, 03/10):
  - [x] con il permesso negato nessun crash, la mappa si apre sull'area di default (Sila).
  - [x] la posizione dell'emulatore impostata in Sila (Camigliatello, 39,3311 / 16,4497) compare nel punto giusto e
    "La mia posizione" ci centra la mappa.
  - [ ] (con il Mac) mappa e permesso sul simulatore iPhone.
- Tipi di mappa (03/10, richiesta dopo M3.1): selettore "Tipo di mappa" (pulsante livelli, foglio dal basso, scelta
  salvata in DataStore da `trail/data/MapSettings`). Stili in `composeResources/files/map`, generati da
  `tools/map-styles/build_map_styles.py`, tutti gratuiti e senza chiave:
  - **Stradale**: `liberty` di OpenFreeMap ricolorato con una palette tipo Google Maps, chiaro e scuro;
  - **Satellite**: Sentinel-2 cloudless 2016 di EOX (CC BY 4.0, 10 m, nitido fino allo zoom 15) con strade,
    sentieri ed etichette sopra;
  - **Topografica tolta** (03/10): OpenTopoMap serve tile PNG, che il motore di maplibre-compose 0.19 su Android
    scarica ma non disegna in modo affidabile (né direttamente, né convertite in JPEG con un `MapResourceProvider`:
    spariva dopo un cambio di tema o un riavvio). Da riprovare con una versione nuova del motore.
  - Barra di stato: sopra la mappa le icone seguono la mappa (chiare su satellite e stradale scura), non il tema
    (`LocalStatusBarOverDark`). Solo Android: su iOS lo stile della barra dipende da tutta la finestra, da rifare
    quando si prova sul simulatore.
  - `MapStylesTest` (androidHostTest) blocca il satellite sul livello EOX 2016 (CC BY 4.0; dal 2018 è CC BY-NC-SA)
    e controlla che nessuno stile usi chiavi.
  - Dopo la review del 03/10: il tipo salvato si legge prima di caricare uno stile (niente stradale e poi satellite),
    lo stile si legge su `Dispatchers.Default` e un file illeggibile lascia la mappa vuota invece di un crash;
    "La mia posizione" smette di aspettare il fix dopo 15 s o quando l'utente sposta la mappa, e l'animazione gira
    nello scope della schermata. `current()` usa `firstOrNull`. Verificato sull'emulatore (centratura su
    Camigliatello, riavvio con il satellite salvato).
  - Nota per M3.2/M6: su Android il primo fix è l'ultima posizione nota, che può essere vecchia (sull'emulatore era
    ancora a Roma). Per "vicino a me" e per l'SOS conviene guardare `measuredAt` e aspettare un fix recente.

**M3.2 Sentieri e POI**
- `findTrailsInBoundingBox` quando la mappa si ferma (debounce di 400 ms, bbox nel formato del backend),
  `findTrailsNearby` per "vicino a me", `getTrail` per il dettaglio con il percorso disegnato,
  `findPoisByTrail` e `findPoisNearby` con un'icona per tipo.
- Dettaglio del sentiero: nome, difficoltà, lunghezza, stato, descrizione, pericoli attivi
  (`findActiveHazardsByTrail`, solo da loggati), foto allegate (`findAttachedMedia` + `getMediaDownloadUrl`).
- Fatto il 04/10 (contratto cambiato nel backend, ADR-0020):
  - **Backend:** `TrailSummaryResponse.path` (GeoJSON semplificato, obbligatorio) e bbox con `ST_Intersects`; senza,
    la mappa non poteva disegnare i sentieri (vedi le lacune in §1). Contratto ricopiato in `api/openapi.json`.
  - **Dati:** `core/location/GeoBounds.kt` (bbox `minLon,minLat,maxLon,maxLat` tenuta dentro i limiti del mondo,
    `GeoJsonLineString.toPositions()` da `[lon, lat]`, distanza in metri, `toString` senza coordinate);
    `TrailRepository` (bbox, vicini, dettaglio, POI per sentiero e vicini, con i limiti del server: 200 nella bbox,
    50 vicini, raggio fino a 50 km), `HazardRepository`, `MediaRepository` (solo le foto `READY`). Tutti sul client
    condiviso, registrati in Koin. Niente strato `domain`.
  - **Mappa** (`TrailMapViewModel`, `TrailMapLayers.kt`): a camera ferma, dopo 400 ms senza movimenti, si chiede la
    vista allargata di metà per lato; finché la vista resta nell'area già caricata non parte nulla (rate limit). Con
    200 risultati l'area non conta come caricata e compare "Troppi sentieri in quest'area". Sotto lo zoom 8 niente
    richieste ("Avvicinati"), POI solo dallo zoom 12 (raggio che copre l'area). Un errore lascia i sentieri già visti
    con "Sentieri non aggiornati. Tocca per riprovare". Linee con un bordo chiaro (scuro su satellite e stradale
    scura), una sorgente per stato perché in MapLibre il tratteggio non può dipendere da un dato: aperti verdi, con
    avvisi ambra, chiusi rossi **e tratteggiati**. Tocco su una linea: dettaglio. POI come tondi da card con il simbolo
    SDF del tipo (casa, goccia disegnata a mano, stella, triangolo, chiave, "i"); tocco: card con nome, tipo e "Apri il
    sentiero".
  - **Sentieri vicini:** capsula in alto; il permesso si chiede come per "La mia posizione" (spiegazione, poi
    dialogo). Vale solo un fix di meno di 2 minuti (`lastLocationMeasurementMark`), atteso per 15 s: su Android il
    primo fix può essere vecchio di giorni (nota di M3.1). Foglio con i sentieri entro 10 km (ordine del server).
  - **Dettaglio** (`TrailDetailScreen`, rotta `TrailDetail(id)`, spinta senza barra in basso): anteprima ferma del
    percorso completo inquadrato, stato con simbolo ed etichetta, difficoltà (Facile, Media, Difficile, Per esperti), km con la virgola, descrizione, POI.
    Pericoli (il rosso solo per `CRITICAL`) e foto solo da loggati, e spariscono all'uscita; da ospite un invito ad
    accedere (dopo il login si torna al sentiero). Foto con Coil: l'URL firmato si chiede quando la foto compare, la
    cache usa l'id della foto (l'URL cambia a ogni richiesta), su un errore di download (URL scaduto) se ne chiede un
    altro, al massimo tre per foto.
  - **Nota sul client generato:** openapi-generator 7.14 dà a `findTrailsInBoundingBox(bbox)` il default
    `"createdAt,ASC"`, preso da un'altra operazione (il contratto è corretto). `TrailRepository` passa sempre `bbox`;
    da ricontrollare quando si aggiorna il generatore.
  - Test: `GeoBoundsTest` (8), `TrailLabelsTest` (3), `TrailMapViewModelTest` (10), `TrailDetailViewModelTest` (6),
    con le rotte del catalogo in `FakeCatalog` sul backend finto (`FakeBackend.extraRoutes`). 131 test host.
    Verificato anche invertendo apposta lon e lat in `toPositions`: tre test falliscono.
- Checkpoint:
  - [x] **test di regressione lon/lat:** il percorso GeoJSON del seed (Sila) si disegna in Calabria e non nel Golfo di
    Aden (`GeoBoundsTest`, e lo stesso percorso letto dall'API finta in `TrailMapViewModelTest`).
  - [x] un sentiero `CLOSED` si riconosce senza leggere il colore: linea tratteggiata sulla mappa, simbolo ed etichetta
    "Chiuso" nel dettaglio e nella lista dei vicini (visto sull'emulatore contro il backend locale il 04/10).
  - [ ] le foto si caricano con l'URL firmato; quando l'URL scade (5 minuti) se ne chiede un altro. Il rinnovo è
    coperto dai test; la prova vera vuole una foto `READY` sul sentiero, che arriva con l'upload (M4.2).
  - [ ] (con il Mac) mappa, sentieri e dettaglio sul simulatore iPhone.

**M3.3 Cache del catalogo e GET condizionali**
- Room: tabelle `trail_summary`, `trail_detail` (con il GeoJSON come testo) e `poi`, con l'`ETag` e la data
  dell'ultimo aggiornamento. La UI legge sempre dalla cache, la rete la aggiorna.
- Le GET del catalogo rimandano l'ETag in `If-None-Match`; un `304` aggiorna solo la data.
- Fatto il 04/10 (entità, DAO e database scritti dall'utente; il resto e le correzioni da Claude):
  - **ETag per rotta, non per riga.** Il backend calcola l'ETag sull'intero body (`ShallowEtagHeaderFilter`), quindi
    vale per una risposta: sta in una tabella a parte, `catalog_etag(key, etag)` con chiave `trail/<id>` e
    `pois/<trailId>`. Le colonne `etag` su `trail_summary` e `poi` erano sbagliate e sono state tolte; `updatedAt`
    resta su ogni riga (ultima conferma del server, è la data di "aggiornato alle").
  - **Quali GET sono condizionali:** il dettaglio (`/trails/{id}`) e i POI del sentiero (`/pois/trail/{id}`). La bbox
    no: l'area cambia a ogni movimento e la stessa URL non torna quasi mai. Il client generato non ha l'header,
    perché lo schema non lo dice: `core/network/ConditionalGet.kt` fa la GET sul client condiviso (bearer, retry, log)
    con gli stessi errori di `apiCall`. L'ETag non si manda se manca la copia in cache (un 304 lascerebbe il dettaglio
    vuoto).
  - **Database:** `AppDatabase` con `@ConstructedBy(AppDatabaseConstructor)` (senza, il target iOS non compila),
    `createAppDatabase` con `BundledSQLiteDriver` e query su `Dispatchers.IO`, nessuna migrazione distruttiva (da
    M5/M6 qui c'è l'outbox). Android in `databases/geosentinel.db` (già escluso dal backup), iOS in Application
    Support con `NSURLIsExcludedFromBackupKey` (compilato solo nel job `ios`). Koin riceve il builder dalla
    piattaforma: `initKoin(context, config)` su Android (Room vuole il `Context`), `startKoinWith` da
    `MainViewController`. Flag `-Xexpect-actual-classes` in `shared` per l'`expect object` di Room.
  - **DAO:** classe astratta (i metodi `@Transaction` con un corpo funzionano così su tutti i target): sentieri il cui
    riquadro tocca la vista, `replaceSummariesInBox` (con una risposta completa, meno di 200, toglie quelli che il
    server non ha più; con 200 aggiunge e basta), `replacePoisOfTrail`, POI nel riquadro, ETag.
  - **Repository e UI:** `TrailRepository` con `observe*` (dalla cache) e `refresh*` (dalla rete alla cache). Mappa:
    i sentieri e i POI salvati dell'area si vedono subito, anche senza rete. Dettaglio: prima la copia salvata, poi
    l'aggiornamento; sotto la lunghezza "Aggiornato alle 14:05" (o "il 03/10 alle 14:05"); senza rete il motivo,
    "Mostro i dati salvati. Tocca per aggiornare." e i dati salvati; senza copia, l'errore di prima. "Sentieri vicini"
    resta solo rete.
  - Test: `TrailRepositoryTest` (10: 304 che cambia solo la data, ETag nuovo quando il sentiero cambia, niente ETag
    senza copia, modalità aereo, lon/lat del percorso salvato, POI rivalidati e tolti, bbox completa e parziale,
    riquadro, riga illeggibile), 2 in più in `TrailDetailViewModelTest` (offline con e senza copia), 1 in
    `TrailLabelsTest` ("aggiornato alle"), con `FakeCatalogDao` (sulla JVM Room vuole un Context) e ETag/304 nel
    backend finto. 144 test host. Verificato anche togliendo apposta `If-None-Match`: i due test del 304 falliscono.
    `CatalogDaoDeviceTest` (3) con la SQL vera sull'emulatore.
- Checkpoint (emulatore `Medium_Phone`, backend locale, 04/10):
  - [x] in modalità aereo, con l'app chiusa e riaperta, la mappa mostra i sentieri salvati con "Sentieri non
    aggiornati. Tocca per riprovare" e il sentiero già aperto si vede con POI, "Nessuna connessione… Mostro i dati
    salvati" e "Aggiornato alle 17:24".
  - [x] la seconda apertura dello stesso sentiero riceve 304 (log `HTTP` in debug), sia `/trails/{id}` sia
    `/pois/trail/{id}`; la prima 200.
  - [x] `CatalogDaoDeviceTest` verde sull'emulatore (`./gradlew :shared:connectedAndroidDeviceTest`), insieme al test
    del Keystore.
- Visto sull'emulatore: l'attribuzione nell'anteprima del dettaglio restava aperta (quella di MapLibre si chiude solo
  al primo gesto). Ora `AttributionButton` (`MapControls.kt`) parte aperta e si chiude al primo gesto o dopo 5 s, come
  chiedono le linee guida di OSM, sulla mappa e nell'anteprima (i 5 s partono quando lo stile è caricato); verificato
  sull'emulatore il 04/10. L'emulatore ha poco
  spazio (520 MB liberi su 5,8 GB): per aggiornare l'app è servito disinstallarla (persa la sessione di prova).

**Review di M3** (04/10, `ecc:kotlin-reviewer` + `ecc:code-reviewer`, §6): nessun CRITICAL. Corretto:
- **cache che perdeva sentieri validi** (HIGH): con una risposta completa della bbox si cancellano solo i sentieri il cui
  riquadro sta tutto dentro l'area; un sentiero a L che tocca l'area solo con il riquadro poteva non tornare dal server
  (`ST_Intersects` sul percorso vero) e sparire dalla cache;
- **"Carico i sentieri…" per sempre** (HIGH/MEDIUM): `loading` si azzera in un `finally` anche quando un movimento
  annulla la richiesta; l'errore di un'altra area sparisce se la vista è già coperta;
- **foto in caricamento infinito** se l'URL firmato non arriva: "Foto non disponibile", tocco per riprovare (anche
  dopo i tre tentativi);
- **POI tolti dal server** che restavano sulla mappa: con una risposta completa (meno di 50, cerchio che contiene il
  riquadro) si tolgono quelli del riquadro (`replacePoisInBox`); **404 sul dettaglio**: si toglie la copia salvata e
  si mostra l'errore;
- **conversioni GeoJSON sul main thread**: `flowOn`/`withContext` su un dispatcher passato al repository (Default
  nell'app, quello del test nei test); **errori del database o fuori contratto**: fallimenti e non crash (`guarded`
  nel repository, `safely` nel ViewModel), scope della sessione con `SupervisorJob`; un solo aggiornamento del
  dettaglio alla volta;
- **da lontano** (sotto lo zoom 8) la cache non si legge più tutta; **bbox senza notazione scientifica** vicino allo 0;
  id codificato nell'URL; `MapScreen.kt` sotto le 800 righe (controlli in `MapControls.kt`);
- backend: `ORDER BY t.id` prima del `LIMIT 200` (sottoinsieme stabile), latitudine controllata nel test del percorso.
- Test in più: sentiero a L (anche in `CatalogDaoDeviceTest`), 404, POI tolti, errore del database, annullamento
  durante il caricamento, zoom basso, URL firmato che non arriva, bbox vicino allo 0. 153 test host (due giri da zero),
  `TrailSpatialQueryIT` verde. `CatalogDaoDeviceTest` (con il sentiero a L) verde sull'emulatore.
- Deciso il 04/10: i sentieri `CLOSED` restano rossi (`TrailClosed = EmergencyRed`), sempre tratteggiati e con
  l'etichetta "Chiuso"; CLAUDE.md e §2 aggiornati. Accettato: con una risposta troncata (200) la cache non si ripulisce.

### Milestone 4: segnalazioni e foto

**M4.1 Segnalazione di un pericolo**
- Form: tipo (nomi italiani), gravità, descrizione facoltativa, posizione (attuale o toccando la mappa), sentiero
  facoltativo. `createHazard`, solo `HIKER` e superiori.
- **Fatto (04/10)**, `hazard/`:
  - `HazardRepository.report(type, severity, location, description, trailId)` con gli enum generati di
    `CreateHazardRequest` (niente enum scritti a mano); descrizione senza spazi ai lati, vuota non si manda. Il POST
    non ha `Idempotency-Key`: su un errore di rete un solo tentativo, l'utente riprova (il form resta compilato).
  - `ReportHazardViewModel` + `ReportHazardScreen` (`FormScaffold`): tipo e gravità senza valore di partenza
    (obbligatori), capsule di scelta (`Critica` in rosso, §2), descrizione fino a 1000 caratteri con contatore
    (`FormTextField` ora accetta `singleLine`/`minLines`), errori del server mappati sui campi come in registrazione
    (`location.lat` → posizione, campo sconosciuto → errore generico), poi "Segnalazione inviata" con "Fatto".
  - **Posizione = centro di una mappa con il segnaposto fisso**: si sposta la mappa, o "Usa la mia posizione"
    (permesso chiesto solo al tocco, fix recente come per "Sentieri vicini": `recentFix` ora condivisa). I gesti della
    mappa sono in Compose (`detectTransformGestures` + `Position.dragged`, mappa con `MapInteractions.None`): con i
    gesti nativi, dentro la pagina che scorre, sull'emulatore il trascinamento spostava la pagina e non la mappa.
  - Ingressi (solo da loggati, `UserRole.canReportHazards`): pulsante col triangolo sulla mappa (parte dal punto
    guardato, zoom almeno 13) e "Segnala un pericolo" sotto i pericoli nel dettaglio (parte dall'inizio del sentiero,
    con `trailId`). Rotta `Route.ReportHazard(lat, lon, zoom, trailId?)`, senza barra in basso.
  - Tolte le bozze `hazard/data/HazardModels.kt` e `media/ui/ReportHazard*` (package sbagliato, enum senza
    `SNOW_ICE`, testi nel codice, controllo della posizione mai raggiunto).
  - Test: `ReportHazardViewModelTest` (10: campi obbligatori, corpo con lat/lon e trailId, dalla mappa senza sentiero,
    descrizione lunga, 400 su campo noto e sconosciuto, posizione, rete senza retry, 403, doppio tocco, niente dati
    personali in `toString`) e `Position.dragged` in `LocationTest`. 164 test host.
- Checkpoint (emulatore `Medium_Phone`, backend locale, 04/10):
  - [x] dalla mappa: senza tipo compare "Scegli il tipo di pericolo."; inviata arriva al backend `FALLEN_TREE`/`CRITICAL`,
    `SUBMITTED`, senza sentiero, lat 39,32 lon 16,56 (in Sila, non invertite); il trascinamento sposta la mappa e non
    la pagina.
  - [x] dal dettaglio del "Sentiero delle Vette Silane": arriva con il suo `trailId` e la posizione d'inizio.
  - [ ] la segnalazione compare tra i pericoli del sentiero solo dopo la verifica di un `PARK_MANAGER` (M8), come
    vuole il backend (oggi resta `SUBMITTED` e non si vede, giusto).
  - [ ] (con il Mac) form e gesti della mappa sul simulatore iPhone.

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
- **Scritta (05/10, anticipata rispetto a M4.3/M6; prima bozza dell'utente, corretta e completata da Claude su richiesta)**:
  - Contratti cambiati: `ImageEncoder()` senza argomenti in tutte e due le piattaforme (così Koin lo crea in common);
    `PickedImage` è Uri + `ContentResolver` dell'applicazione su Android, `NSData` su iOS. `MediaTarget(type, entityId)`
    usa l'enum generato `InitUploadRequest.EntityType` (tolto `MediaEntityType` scritto a mano). `MediaUploader` riceve i
    client come funzioni (come i repository) e un `Clock` per i test.
  - `HttpClientFactory.createStorageClient(engine)`: niente `Auth`, niente redirect, niente log, timeout di 120 s.
    Multipart con un `ByteArray`: Ktor conosce la lunghezza e manda `Content-Length`.
  - Storage rifiutato dopo `expiresAt` → una nuova richiesta di upload, una volta. Conferma ritentata (fino a 3 volte)
    solo su errore di rete: ricominciare caricherebbe la foto due volte. Oltre 10 MB non parte (`media-payload-too-large`).
  - Encoder Android: dimensioni con `inJustDecodeBounds` (la bozza lanciava sempre: con quel flag `decodeStream`
    restituisce null), orientamento EXIF con `android.media.ExifInterface` (API 24, niente dipendenza), campionamento a
    potenze di 2 e scala fine, su `Dispatchers.IO`. iOS: `UIImage.imageWithData`, sempre ridisegnata (orientamento nei
    pixel, metadati fuori), `UIImageJPEGRepresentation`.
  - Picker: `media/ui/PhotoPicker.kt`, `rememberPhotoPicker(onPicked)` expect/actual: Photo Picker (`activity-compose`
    ora anche in `shared`) e `PHPickerViewController` ("public.image", anche HEIC). Nessun permesso sulla galleria.
  - Dove: nella schermata "Segnalazione inviata" (serve l'id appena creato), sezione Foto, fino a 5
    (`MAX_HAZARD_PHOTOS`); "Fatto" in attesa mentre un upload è in corso. `ReportHazardViewModel.addPhoto(encode)` /
    `retryPhoto(id)`; `PhotoUpload` tiene il JPEG (miniatura e Riprova).
  - Interfaccia (05/10, `hazard/ui/HazardPhotos.kt`, rivista dopo il primo giro: le righe "Foto 1: caricata" erano
    poco chiare): miniature da 88 dp che vanno a capo (`FlowRow`, niente scorrimento con foto tagliate), badge
    nell'angolo con una piccola molla: anello in invio, **spunta verde** (`primary`) caricata, ✕ prugna non accettata
    (con il motivo nel banner), freccia prugna per riprovare (si tocca la miniatura). Sopra le miniature una sola barra
    come i campi dei form (card a 20 dp, filo `outlineVariant`) divisa a metà: "Scatta" | "Galleria" (solo Galleria senza
    fotocamera); resta ferma mentre le foto si aggiungono sotto. Lo stato è detto a parole a TalkBack/VoiceOver ("Foto 1, caricata").
  - **Fotocamera** (`rememberCameraCapture`, null senza fotocamera): Android `TakePicture` con un `FileProvider` di
    androidApp (`${applicationId}.photos`, solo `cache/camera/`), senza il permesso `CAMERA`; iOS
    `UIImagePickerController` con `NSCameraUsageDescription` in `Info.plist`.
  - Verificata sull'emulatore (dopo l'allargamento del disco dell'AVD a 8 GB): foto dalla fotocamera e dalla galleria
    con la spunta, `READY` sul server, senza EXIF.
  - Test: `MediaUploaderTest` (8: flusso e bearer solo verso l'API, ordine dei campi e `Content-Length`, QUARANTINED,
    scadenza, rifiuto dello storage, rete, conferma ritentata, 10 MB) e 6 test delle foto in `ReportHazardViewModelTest`.
    178 test host.
  - Aperti: compilazione iOS (picker e encoder scritti senza Mac: li controlla il job `ios`) e HEIC sul simulatore.
- Checkpoint (backend locale e MinIO del compose, con `MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000`):
  - [x] (05/10, `Medium_Phone`, avvio a freddo) una foto dalla galleria dell'emulatore arriva a `READY` dalla schermata
    "Segnalazione inviata": "Foto 1: caricata", `GET /media/my` dà `READY`, `HAZARD_REPORT` con l'id della segnalazione
    appena creata. Visibile nel dettaglio della segnalazione quando c'è (M4.3).
  - [x] il JPEG caricato non ha EXIF GPS (controllo sul file scaricato): foto di prova 4000×3000 con GPS in Sila e
    orientamento 6, scaricata dall'URL firmato è 1920×2560, ruotata nel verso giusto, nessun tag EXIF.
  - [ ] una foto HEIC dal simulatore iOS arriva come JPEG (serve il Mac).
  - [ ] chiude il checkpoint aperto del backend (M7) "foto da emulatore/simulatore → `READY`".

**M4.3 Le mie segnalazioni** (aggiunta il 04/10, si fa prima di M6)
- **Prima il backend** (`../GeoSentinel-Backend`): `GET /api/v1/hazards/mine?page&size`, per ogni utente loggato,
  paginata (`PagedModel<HazardResponse>` come le altre liste, es. `findMyBookings`), ordinata per `createdAt` decrescente, solo le segnalazioni con
  `reporter_id` = utente corrente (indice `idx_hazard_reports_reporter` già in V4). `HazardResponse` ha bisogno anche di
  `createdAt` per mostrare la data. Con test di integrazione (le segnalazioni di un altro utente non compaiono) e
  `openapi.json` rigenerato; nell'app `checkApiContract`, copia in `api/` e client rigenerato.
  **Fatto il 05/10 da Claude (su richiesta):** `GET /hazards/mine` (`findMyHazards`), sort solo su `createdAt`
  (whitelist, 400 sugli altri), in ogni stato compreso `REJECTED`; `HazardResponse.createdAt` obbligatorio (anche in
  creazione e nel dettaglio). `MyHazardsIT` (7 test) e `./mvnw verify` verde (158); CHANGELOG aggiornato. Nell'app:
  contratto copiato, client rigenerato (`findMyHazards(page, size, sort)` → `PagedModelHazardResponse`), `createdAt`
  aggiunto ai JSON finti dei test (`ReportHazardViewModelTest`, `FakeCatalog`).
```kotlin
// hazard/data
suspend fun HazardRepository.mine(page: Int): Result<PagedModelHazardResponse>   // nome generato dal backend (PagedModel di Spring)

// hazard/ui
data class MyHazardsUiState(
    val items: List<HazardResponse> = emptyList(),
    val loading: Boolean = false,       // prima pagina o pagina successiva
    val endReached: Boolean = false,
    val error: UiMessage? = null,
)
class MyHazardsViewModel(hazards: HazardRepository) : ViewModel() {
    fun loadMore()   // la pagina successiva quando la lista arriva in fondo
    fun refresh()    // trascinando in basso e al ritorno dal form della segnalazione
}
```
- Schermata "spinta" dal Profilo ("Le mie segnalazioni", solo da loggati), con il suo Indietro: tipo, data, sentiero
  (se c'è) e stato in parole, non solo con il colore: `SUBMITTED` "In verifica", `VERIFIED` "Confermata",
  `REJECTED` "Non confermata", `RESOLVED` "Risolta"; un valore nuovo del server "Stato non disponibile". Lista vuota:
  stato vuoto che rimanda alla mappa per segnalare.
- Il tocco su una voce con un sentiero apre il dettaglio del sentiero. Il dettaglio della segnalazione con le foto
  arriva con M4.2 (l'upload su `HAZARD_REPORT` è consentito solo a chi l'ha fatta).
- Niente cache: solo rete, con l'errore e "Riprova" senza connessione.
- **App fatta (05/10)**: prima bozza dell'utente, rivista e completata da Claude su richiesta (non compilava:
  `mine` senza corpo, `suspend` chiamata da `init`, `UiMessage.Dynamic` inesistente, testi nel codice, enum
  `HazardType`/`HazardSeverity` tolti in M4.1, "←" di testo al posto di `BackButton`).
  - `HazardRepository.mine(page, size = 20)` su `findMyHazards`; `MyHazardsViewModel(hazards)` con `loadMore()`,
    `refresh()` e `retry()`: flag alzati prima del `launch` (niente pagine doppie), un refresh annulla la pagina in
    corso, `distinctBy` sulle pagine a offset, errore della pagina dopo in fondo alla lista con "Riprova" (le righe
    restano). `MyHazardsUiState` senza descrizioni né posizioni in `toString`; `HazardResponse.Status.label()`.
  - `MyHazardsScreen`: testata, titolo, card come quelle del form (tipo + stato in una capsula, gravità e data,
    inizio della descrizione; "Non confermata" in prugna, mai rosso), trascinando in basso si ricarica, stato vuoto con
    "Vai alla mappa" (scheda Mappa), tutta la card apre il sentiero (freccia). Voce nel Profilo sotto "Cambia
    password"; rotta senza barra in basso. Il ritorno "dal form" del piano non serve: da qui non si apre il form.
  - `MyHazardsViewModelTest` (10). 188 test host.
- Checkpoint (emulatore, backend locale, 05/10):
  - [x] la segnalazione verificata da `manager@` (via API, finché non c'è M8) compare in cima come "Confermata",
    le altre "In verifica"; tocco sulla card con sentiero → dettaglio del sentiero.
  - [x] con 25 segnalazioni la seconda pagina si carica in fondo; l'ultima riga coincide con l'ultima del server.
  - [x] `hiker2@` vede solo le sue 2 del seed (una "Non confermata"), nessuna di `hiker@`.

**M4.4 Pericoli vicino a me** (aggiunta il 05/10, dopo M4.2)
- **Prima il backend:** `GET /api/v1/hazards/nearby?lat&lon&radius&limit`, come `/pois/nearby` e `/trails/nearby`
  (`radius` in metri, default 5000, massimo 50000; `limit` default 30, massimo 50), solo da loggati come il resto di
  `/hazards/**`. Restituisce i pericoli **attivi** (stessa regola di `/hazards/trail/{trailId}`: verificati e non
  risolti), anche quelli senza sentiero, dal più vicino; con `ST_DWithin` sul `geography` e un indice GiST su
  `location` (migration nuova). Test di integrazione: ordine per distanza, raggio rispettato, `SUBMITTED` e
  `RESOLVED` esclusi, 401 da ospite. `openapi.json` rigenerato, client dell'app rigenerato.
  **Backend fatto il 05/10** (prima bozza dell'utente, corretta da Claude su richiesta: mancava la `@Query`, quindi il
  contesto non partiva; filtrava per `reporterId`; `Pageable` su una query nativa): `findHazardsNearby` →
  `List<HazardResponse>`, solo `VERIFIED`, di tutti gli utenti, dal più vicino. **Nessuna migration**: l'indice GiST su
  `location::geography` c'è già (`idx_hazard_reports_location_geog`, V4). `NearbyHazardsIT` (5), `openapi.json`
  rigenerato, CHANGELOG. Nell'app: `checkApiContract`, copia in `api/`, client rigenerato.
```kotlin
// hazard/data
suspend fun HazardRepository.nearby(center: GeoPoint, radiusMeters: Double): Result<List<HazardResponse>>
```
- **Sulla mappa** (solo da loggati): un livello di pericoli sopra i sentieri, triangolo nel colore della gravità (rosso
  solo `CRITICAL`, §2), caricato come i POI della vista (cerchio che contiene il riquadro, stesso debounce, sotto lo
  zoom 8 niente richiesta). Il tocco apre una card con tipo, gravità, descrizione e, se c'è, "Apri il sentiero"; il
  livello sparisce al logout. Niente cache in Room: i pericoli cambiano spesso, e uno vecchio mostrato come attivo
  inganna (senza rete il livello resta vuoto, con l'avviso).
- **"Pericoli vicini"** accanto a "Sentieri vicini": dalla posizione attuale (fix recente, `recentFix`), raggio 5 km,
  lista per distanza ("a 350 m", "a 2,1 km") con tipo e gravità; il tocco centra la mappa sul pericolo. Senza
  posizione, lo stesso dialogo dei sentieri vicini.
- Il filtro dei log sulle query con posizione (M9.3) va esteso a questa rotta (`lat`, `lon`, `radius`). Già coperta:
  `lat` e `lon` bastano a escludere la riga dal log.
- **App fatta (05/10)**: prima bozza dell'utente, corretta e completata da Claude su richiesta (non compilava:
  `nearby` senza tipo di ritorno, `findHazardNearby` inesistente, `HazardType` tolto in M4.1, `formatDistance` senza
  tipo; `color()` e le etichette della gravità duplicate in conflitto con quelle di `TrailLabels.kt`; esadecimali e
  testi nel codice; stati dei pericoli in `TrailMapViewModel` mai usati).
  - `HazardRepository.nearby(center, radiusMeters = 5000)` su `findHazardsNearby` (limite 50).
  - `HazardMapViewModel(hazards)` in `hazard/ui`, separato da `TrailMapViewModel`: livello caricato a mappa ferma
    (400 ms, cerchio della vista più il margine come i POI, sotto lo zoom 8 niente), area già caricata riusata per
    5 minuti al massimo; `setEnabled(role != GUEST)` dalla schermata (al logout livello, card e foglio spariscono);
    senza rete livello vuoto e avviso "Pericoli non aggiornati" con riprova; "Pericoli vicini" come "Sentieri vicini"
    (fix recente, `NearbyHazards`), distanze dal punto del fix.
  - `HazardMarkers` (triangolo SDF da 30 dp, rosso solo `CRITICAL`, ambra `HIGH`, il resto `onSurfaceVariant`, bordo
    come i sentieri; `idClickHandler` di `TrailMapLayers` ora `internal`), `HazardCard` (come la card dei POI, mai
    insieme), `NearbyHazardsSheet` (riga: tipo, "a 350 m · Gravità alta"); il tocco chiude il foglio, centra la mappa
    (zoom almeno 15) e apre la card. `distanceLabel` in `HazardUiUtils.kt`. Capsule "Sentieri vicini" e "Pericoli vicini"
    affiancate (la seconda solo da loggati).
  - `HazardMapViewModelTest` (15, compresa `distanceLabel`). 203 test host.
- Checkpoint (emulatore `Medium_Phone`, backend locale, 05/10):
  - [x] i due pericoli `VERIFIED` (quello del seed sul Sentiero delle Vette, `HIGH`, e un `CRITICAL`) compaiono in Sila
    nel punto giusto; le segnalazioni `SUBMITTED` di M4.1 no. "Pericoli vicini" da 39,31/16,54: "a 2,0 km · Gravità
    critica", "a 4,4 km · Gravità alta"; il tocco centra la mappa sul pericolo con la sua card.
  - [ ] da ospite il livello non c'è e la rotta non parte: coperto dai test (nessuna richiesta, stato vuoto al logout) e
    dal 401 del backend; da rivedere a mano sull'emulatore con un logout.
  - [x] un pericolo `CRITICAL` si riconosce senza il colore: "Gravità critica" nella card e nella lista.
  - [ ] (con il Mac) livello e foglio sul simulatore iPhone.

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
- **Contratto da rivedere prima di iniziare:** con A11 il `LocationProvider` si crea nella UI
  (`rememberLocationState`, su Android serve l'Activity per il permesso), quindi Koin non può passarlo al costruttore
  di `SosController`. Strade possibili: la posizione (o il provider) come parametro di `raise()` e di
  `updateLocation()`, oppure un provider creato a livello di Activity e registrato a parte. Si decide qui e si
  aggiorna la firma.
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
- Dalla review di sicurezza del 03/10 (rimandati qui):
  - l'informativa dice che le tile della mappa arrivano da OpenFreeMap ed EOX, che vedono IP e zona guardata;
  - EOX è un servizio gratuito senza garanzie: prima del rilascio un'alternativa (tile proprie o un altro fornitore);
  - `NSAllowsLocalNetworking` è anche nella build Release di iOS: spostarlo in una Info.plist solo per Debug;
  - il filtro dei log sulle query con posizione è una lista di nomi (lat, lon, bbox): un parametro nuovo con un altro
    nome sfuggirebbe. Rivederlo a ogni endpoint geografico nuovo (M3.2, M4, M6);
  - un logout senza rete (o con lo store illeggibile) lascia valido sul server il refresh token (fino a 30 giorni):
    se diventa un requisito, accodare la revoca nell'outbox. Se lo store non si svuota nemmeno al secondo tentativo,
    la sessione si chiude in memoria ma si riapre al riavvio (disco o Keychain guasti);
  - `DELETE /users/me` si ritenta su errore di rete (DELETE è idempotente): se la prima è passata e la risposta si è
    persa, l'utente vede un errore ma l'account è cancellato e la sessione si chiude al 401 successivo.
- Informativa privacy raggiungibile dal profilo; scheda "Data safety" su Play e "Privacy nutrition label" su App Store:
  posizione, foto, email, nome, note mediche.
- R8/ProGuard in release con le regole per kotlinx.serialization e Ktor; build firmata.
- Checkpoint:
  - [ ] la build release su un telefono vero funziona contro un backend HTTPS (stack prod del backend,
    `docker-compose.prod.yml`).

---

## 6. Ordine e review

```
M0 → M1 → M2 → M3 → M4.1 → M4.3 → M6 → M4.2 → M4.4 → M5 → M7 → M8 → M9
```
L'SOS (M6) viene subito dopo la mappa: è la funzione per cui l'app esiste, e i suoi problemi conviene scoprirli presto.
Eccezione decisa il 04/10: la segnalazione (M4.1, già fatta) e "Le mie segnalazioni" (M4.3) vengono prima; le foto
(M4.2) e i pericoli vicini (M4.4) restano dopo M6.
La parte iOS di M7 richiede il Mac (vedi §1).

Dopo ogni milestone si lancia una **catena di sola review**. Le catene di implementazione (`tdd-guide`) non si usano,
perché scriverebbero il codice al posto tuo.

| # | Milestone | Chain |
|---|---|---|
| 0 | Fondamenta | `ecc:kotlin-build-resolver,ecc:kotlin-reviewer` |
| 1 | Client e rete | `ecc:kotlin-reviewer,ecc:security-reviewer` (eseguita il 02/10: nessun CRITICAL/HIGH; correzioni in §M1.2 e §M1.3, note per M2 in §M2.1 e §M2.2, periodo di grazia nel backend) |
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
/ecc:orchestrate custom "ecc:kotlin-reviewer,ecc:code-reviewer" "[Plan: docs/MASTER_PLAN.md#step-3] Review only: MapLibre Compose map with bbox debounce, trail/POI repositories with Room cache and ETag If-None-Match handling, GeoJSON [lon,lat] to Position conversion, MapLibre location module with just-in-time permission, signed media URL refresh; Acceptance: lon/lat regression test passes; cached trail visible offline; 304 updates timestamp only"
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
