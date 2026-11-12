# GeoSentinel Mobile: piano di redesign UI/UX ("Carta e isole")

Piano operativo per chi implementa (Opus). Scritto il 12/11/2026 sul codice di `main` (`37fe4ce`).
Si legge insieme a `CLAUDE.md` e a `docs/MASTER_PLAN.md` §2: dove questo piano contraddice §2, vince questo piano e
§2 si aggiorna a fine lavoro (fase 5).

**Regola di lavoro.** `CLAUDE.md` dice che il codice lo scrive l'utente. Questo redesign è un'eccezione chiesta
dall'utente: si implementa una fase alla volta, solo dopo il suo via, e ogni fase finisce con build e test verdi e una
prova sull'emulatore. Niente commit: a fine fase si propone solo il messaggio.

---

## 0. In una pagina

**Tesi.** Una carta escursionistica in tasca: il contenuto (mappa, foto, date, numeri) occupa lo schermo; i comandi
galleggiano sopra in poche "isole" silenziose. Stile Apple nei comportamenti (titoli grandi che si comprimono, fogli
dal basso, liste raggruppate, una sola azione piena per schermata), montagna nei materiali (pietra, abete, lichene,
curve di livello, Barlow dei segnavia).

**Elemento firma (uno solo): le curve di livello** (`core/ui/ContourLines.kt`). Compaiono in tre posti e basta: dietro
ai titoli grandi (appena accennate), negli stati vuoti, nel "biglietto" della prenotazione. Il paesaggio `ForestHeader`
resta solo nei momenti di marca (accesso, registrazione, Profilo da ospite).

**Cosa si toglie.**
- La testata `ForestHeader` da 148/160 dp in cima a ogni pagina e a ogni form: ripete la marca 20 volte e spinge il
  contenuto a metà schermo.
- Il bordo da 1 dp su ogni card: le superfici si separano con il passo di tono, il bordo resta a campi e isole.
- La schermata "Prenota" separata, il `DatePickerDialog`, la pillola "Prenotazioni", un "Annulla" su ogni riga.
- Le cinque copie private di `Message` / `RetryMessage` / `CenteredProgress`.

**Cosa non si tocca.**
- **L'icona dell'app**: niente modifiche a `tools/app-icon`, ai `mipmap-*`, a `Assets.xcassets`, a
  `CFBundleDisplayName`.
- Palette, font, regola del rosso, bersagli 72/56/48, safe area, strato `data/`, contratto API, outbox, regole di
  sessione. La logica dell'SOS (`SosScreen`, `SosController`) prende solo i token nuovi.
- Nessuna dipendenza nuova.

**Il problema dei rifugi, misurato sul codice.** Prenotare 2 notti per 2 persone da sabato oggi vuol dire: dettaglio →
scorrere fino a "Prenota" → schermata nuova con testata, titolo, testo e tre card → "Cambia" → dialogo del calendario →
OK → "+" notti → "+" persone → "Invia" → "Fatto". 9 tocchi, 3 contesti (pagina, form, dialogo), e il form **non conosce
i posti liberi**: la striscia delle 14 notti è nella pagina sotto e non si può toccare, quindi si scopre "posti finiti"
solo dal 409 del server. Dopo il redesign: tocco sul giorno nella striscia → tocco sul giorno di partenza → "+" persone
→ "Conferma" → "Fatto". 5 tocchi, un contesto, le notti senza posto non sono selezionabili.

---

## 1. Sistema visivo

### 1.1 Colore (palette invariata, ruoli precisati)

I valori di `core/ui/theme/Color.kt` restano. Cambia come si usano (regola 60/30/10):

| Quota | Ruolo | Token | Dove |
|---|---|---|---|
| 60% | Fondo | `background` (pietra `EDF0E9` / bosco di notte `0D1411`) | ogni schermata |
| 30% | Superfici e testo | `surfaceContainerLowest` (card), `onSurface`, `onSurfaceVariant` | card, righe, testi |
| 10% | Accento | `primary` (abete / lichene), `primaryContainer` (muschio) | **una** azione piena per schermata, selezione, stato "libero" |

- Testo: tre livelli e non di più. `onSurface` (titoli e valori), `onSurfaceVariant` (secondario), `primary` (link e
  valori positivi). Niente alpha sul testo: i due grigi sono già verificati (6:1 e 7:1, commenti in `Color.kt`).
- **Card senza bordo** sul fondo (`GroupCard`, card dei rifugi, biglietto). Bordo `outlineVariant` da 1 dp solo su:
  campi di testo, celle del calendario non selezionate, isole sopra la mappa (lì resta `mapControlBorder()`).
  Compromesso dichiarato: al sole il bordo aiutava a vedere i gruppi; il passo pietra → card è lo stesso che usa iOS
  nelle liste raggruppate e la leggibilità dipende dal testo, non dal contorno. Se sull'iPhone al sole i gruppi
  spariscono, si rimette il bordo in un punto solo (`GroupCard`).
- **Disponibilità dei posti** (mai rosso, sempre con testo):
  - liberi: numero in `primary`;
  - pochi (≤ 3 o ≤ 20% dei posti): numero in ambra `TrailWarning` + la parola "ultimi";
  - pieno: "Pieno" in `onSurfaceVariant`, cella spenta, giorno barrato.
- Da verificare prima di usarla: `TrailWarning` `B45309` è scuro per il tema scuro (stimato ~3,6:1 su `CardDark`, sotto
  4,5:1). Se non esiste già una variante scura, aggiungere `TrailWarningDark = 0xFFE9A862` (stimato ~8:1 su `151E19`,
  da ricalcolare) ed esporre l'ambra con una funzione `@Composable` che sceglie in base al tema, come già si fa per gli
  stati del sentiero.
- Rosso: invariato (SOS, `CRITICAL`, `CLOSED`). "Annulla prenotazione" resta prugna (`error`).

### 1.2 Tipografia (due famiglie, scala Apple)

Barlow Semi Condensed = voce dei cartelli: **solo** titoli di pagina e numeri grandi. Atkinson Hyperlegible Next = tutto
il resto. In `Theme.kt` si allinea la scala a quella di iOS e si tolgono i gradini doppi:

| Ruolo M3 | Uso | Famiglia | sp / interlinea | Peso |
|---|---|---|---|---|
| `headlineLarge` | Titolo grande di scheda | Barlow | 34 / 38 | SemiBold |
| `headlineMedium` | Titolo di pagina di dettaglio (nome del rifugio, del sentiero) | Barlow | 28 / 32 | SemiBold |
| `headlineSmall` | Numeri in evidenza (giorno nel biglietto, posti, km) | Barlow | 22 / 26 | SemiBold |
| `titleLarge` | Titolo di foglio | Atkinson | 20 / 26 | SemiBold |
| `titleMedium` | Titolo di sezione, titolo di riga, testo dei pulsanti | Atkinson | 17 / 22 | SemiBold |
| `bodyLarge` | Testo | Atkinson | 17 / 25 | Regular |
| `bodyMedium` | Testo secondario, sottotitoli di riga | Atkinson | 15 / 22 | Regular |
| `bodySmall` / `labelMedium` | Note, crediti, etichette di chip | Atkinson | 13 / 18 | Regular / Medium |
| `labelSmall` | Barra in basso, giorni della settimana | Atkinson | 12 / 16 | Medium |

- `displaySmall` (40) esce dall'uso; `headlineMedium` scende da 30 a 28 e `headlineSmall` da 26 a 22. Dopo il cambio
  si cercano tutti gli usi dei tre ruoli e si controlla che nessuno si sia rimpicciolito per errore (il testo "SOS" del
  pulsante usa `headlineSmall`: lì si fissa la dimensione a mano, 26 sp, perché deve restare com'è).
- Pesi: Regular e SemiBold. Medium solo nelle etichette da 12–13 sp. Mai Bold fuori dall'SOS.
- **I valori contano più delle etichette**: "6" grande e "posti liberi" piccolo, non il contrario.
- `titleSmall` e `labelLarge` (15 SemiBold) restano definiti ma si usano solo per chip e link.

### 1.3 Forme

Nessun raggio nuovo: quelli di `AppShapes` bastano, con regole d'uso fisse.

| Token | Raggio | Dove |
|---|---|---|
| `extraSmall` | 8 | celle del calendario, badge |
| `small` | 12 | miniature, foto nelle righe, banner d'errore |
| `medium` | 20 | card e gruppi, campi di testo |
| `large` | 28 | angoli alti dei fogli, pannello che sale sopra la foto del rifugio, biglietto |
| `CircleShape` | capsula | pulsanti, chip, barra in basso, controlli della mappa, Indietro |

- **Raggi concentrici**: un elemento dentro una card ha raggio = raggio della card − margine interno (foto da 12 in una
  card da 20 con 8 di margine). Mai un angolo vivo dentro un angolo morbido.
- Solo due famiglie di forme sullo schermo: rettangoli morbidi per il contenuto, capsule per ciò che si tocca.

### 1.4 Spazio

Griglia a 4: valori ammessi **4, 8, 12, 16, 20, 24, 32, 48**. Nel codice oggi ci sono 6, 10, 14, 18: si portano al
valore vicino quando si tocca il file.

- Margine di pagina 20. Margine interno di card 16. Riga: minimo 56 di altezza, 12 sopra e sotto.
- Tra elementi dello stesso gruppo 8; tra titolo di sezione e card 8; tra una sezione e la successiva 32 (il doppio di
  16: ciò che è lontano non è parente).
- Larghezza massima del contenuto: `PageMaxWidth` (600), nei fogli 480.

### 1.5 Profondità: due livelli

1. **Contenuto** (fondo, card, foto): piatto, nessuna ombra, nessuna trasparenza.
2. **Isole** (barra in basso, controlli della mappa, barra fissa dell'azione, Indietro sopra una foto, fogli): tono
   `surfaceContainerHigh`, ombra morbida 6 dp **tinta** (`Modifier.shadow(..., ambientColor = primary, spotColor =
   primary)` con alpha bassa) invece del grigio.

- **Niente sfocatura / vetro.** `liquid-glass.md › Where the material belongs`: "Don't use Liquid Glass in the content
  layer"; e l'unico posto dove avrebbe senso, sopra la mappa, non è fattibile: la mappa è una vista nativa e Compose
  non può sfocare quello che ci sta dietro (giudizio tecnico, non una linea guida). Le isole restano opache: è anche
  la risposta corretta a "Riduci trasparenza".
- **Bordo di scorrimento**: dove il contenuto incontra una barra fissa in basso non c'è più una linea
  (`HorizontalDivider` di `FormScaffold`) né un taglio netto: una sfumatura di 24 dp da trasparente a `background`
  sopra la barra (`liquid-glass.md › Review checklist`, punto 6: il contenuto sfuma dove incontra una barra).

### 1.6 Movimento e risposta tattile

- Spinta/ritorno: invariati (320/240 ms con parallasse). Cambio scheda: dissolvenza 150 ms.
- Fogli: animazione di default di `ModalBottomSheet`.
- Selezione delle date: la fascia dell'intervallo si allarga in 200 ms; tick tattile a ogni giorno toccato.
- **Un solo momento orchestrato**: la conferma della prenotazione (spunta che si disegna in 400 ms dentro un tondo
  muschio + vibrazione di conferma). È il picco del flusso, non ce ne sono altri.
- Caricamenti: sagome ("skeleton") a blocchi tonali con un respiro di opacità 0,5 ↔ 1 in 900 ms, al posto dello
  spinner al centro. Lo spinner resta dentro i pulsanti e per "altre pagine".
- "Riduci movimento": un `expect fun isReduceMotionEnabled(): Boolean` in `core/ui` (Android: scala delle animazioni a
  0; iOS: `UIAccessibility.isReduceMotionEnabled`). Se attivo: niente respiro, niente disegno della spunta (appare),
  niente parallasse.
- Tattile: usare i tipi specifici di `HapticFeedbackType` se la versione di Compose li ha (conferma, tick di
  selezione); altrimenti `TextHandleMove` per il tick e `LongPress` per la conferma.

### 1.7 Icone

`material-icons-core` ha poco. Si resta lì più i glifi disegnati a mano (`Glyph` in `trail/ui/MapControls.kt`, tratto
2 dp, estremità tonde). `Glyph` e `GlyphIcon` si spostano in `core/ui/Glyphs.kt` e diventano `internal`. Glifi nuovi,
solo quelli usati: `Bed`, `Altitude` (cima con quota), `Calendar`, `Phone` se serve coerenza con gli altri. Icone
sempre da 20 o 24 dp, peso visivo uguale al testo accanto.

---

## 2. Scheletro dell'app

### 2.1 Componenti nuovi in `core/ui` (fase 0)

| Componente | File | Cosa fa | Sostituisce |
|---|---|---|---|
| `LargeTitlePage` | `PageLayout.kt` | Titolo grande Barlow 34 allineato a sinistra che, scorrendo, si comprime in una barra con il titolo da 17 al centro. Slot: `navigationIcon` (Indietro), `actions`, `below` (segmenti). Curve di livello tenui dietro il titolo espanso. Costruito su `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` di Material 3 con colori e stili del tema; insets `safeDrawing` | `PageHeader` + `ForestHeader` in schede e liste |
| `SegmentedControl` | `PageLayout.kt` | Binario `surfaceContainerHighest` a capsula, pollice `surfaceContainerLowest`, 2–3 voci, altezza 44, `Role.Tab` | `ManagementTabs` (`staff/ui/TicketScreens.kt:157`) e il selettore del tema del Profilo: si estrae quello che c'è, non se ne scrive un terzo |
| `StickyActionBar` | `PageLayout.kt` | Barra fissa in basso: sfumatura 24 dp + fondo pieno, slot libero, `safeDrawing` in basso | il `Box` in fondo a `RefugeDetailScreen`, il fondo di `FormScaffold` |
| `EmptyState` / `ErrorState` | `States.kt` | Curve di livello in un tondo muschio da 96, titolo, una riga, una azione. `ErrorState` annuncia con `liveRegion` | i 5 `Message` privati e `RetryMessage` |
| `Skeleton` | `States.kt` | `Modifier.skeleton()` + 2 sagome pronte (riga con miniatura, pagina di dettaglio) | `CenteredProgress` al primo caricamento |
| `MetaLine` | `PageLayout.kt` | "1 450 m · 24 posti · 3,2 km": valori separati da un punto mediano, una riga, va a capo con il testo grande | le file di `InfoChip` |

Modifiche ai componenti che restano:
- `GroupCard` e `Modifier.groupedItem`: via il bordo (§1.1). `GroupRow`: icona nel tondo muschio da 32 (non 40),
  margini sulla griglia.
- `BackButton`: tondo da 44 visibile dentro un bersaglio da 48; sopra foto e mappa è un'isola (tono + ombra), nelle
  barre è senza fondo.
- `PrimaryPillButton`: invariato (56). Nuovo `TonalPillButton` (48, `primaryContainer`) per le azioni secondarie
  ("Chiama", "Indicazioni"), al posto dei `FilledTonalButton` sparsi.
- `FormScaffold`: via `ForestHeader`; barra compatta con Indietro e titolo, testo introduttivo sotto, azione in
  `StickyActionBar`. `ForestHeader` resta (alto 200) solo in `LoginScreen`, `RegisterScreen`, `PasswordResetScreen` e
  nel Profilo da ospite.

### 2.2 Navigazione

Schede invariate (Mappa, Rifugi, Allerte, Gestione, Profilo, in base al ruolo). Cambiano le rotte dei rifugi:

```
Rifugi (scheda)
├─ segmento "Esplora"        lista dei rifugi
└─ segmento "Prenotazioni"   le mie prenotazioni        (era Route.MyBookings, spinta)
     └─ foglio della prenotazione (azioni)

RefugeDetail (spinta, dalla lista, dalla mappa, da una prenotazione)
└─ foglio "Prenota" (calendario + persone + conferma)   (era Route.BookRefuge, spinta)
```

- `Route.MyBookings` e `Route.BookRefuge` si eliminano, con i loro `composable<>` in `App.kt` e le voci in
  `FormRoutes` / `NoBottomBarRoutes`.
- Il segmento scelto si salva con `rememberSaveable` nella scheda; `Route.Refuges` prende un parametro facoltativo
  `bookings: Boolean = false` solo se serve arrivarci dal Profilo (riga "Le mie prenotazioni" in "Attività": si
  aggiunge, oggi dal Profilo non ci si arriva).
- Barra in basso: resta l'isola a capsula. Altezza 64, voce attiva con la pillola muschio com'è.
- Motivo dei fogli: `sheets.md`: "A sheet helps people perform a scoped task that's closely related to their current
  context." Prenotare è esattamente questo; una schermata spinta con testata e titolo proprio lo fa sembrare un altro
  posto.

---

## 3. Rifugi (il cuore del lavoro)

### 3.1 Scheda Rifugi, segmento "Esplora"

File: `refuge/ui/RefugeListScreen.kt`, `RefugeListViewModel.kt`.

```
┌──────────────────────────────────────┐
│ Rifugi                               │  titolo grande, curve tenui dietro
│ ┌──────────────┬───────────────────┐ │
│ │  Esplora     │  Prenotazioni  •  │ │  SegmentedControl (• = in invio/rifiutate)
│ └──────────────┴───────────────────┘ │
│ Vicino a te                          │  bodyMedium grigio (o "Nell'area del parco")
│ ┌──────────────────────────────────┐ │
│ │ ┌──────┐ Rifugio Monte Curcio    │ │  titleMedium
│ │ │ foto │ 1 760 m · 24 posti      │ │  bodyMedium grigio
│ │ └──────┘ 3,2 km                  │ │  bodyMedium, primary
│ │ ──────────────────────────────── │ │  filo rientrato fino al testo
│ │ ┌──────┐ Rifugio Botte Donato    │ │
│ │ │ ≋≋≋  │ 1 928 m · 12 posti      │ │  senza foto: tessera muschio con curve
│ │ └──────┘ 5,8 km                  │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

- Riga: miniatura 72×72 raggio 12 a sinistra, tre righe di testo, tutta la riga apre il dettaglio, nessuna freccia
  (la foto dice già "apri"). Altezza minima 96. Un `GroupCard` solo, come oggi.
- Miniatura: la prima foto del rifugio. Il contratto non la dà nella lista, quindi `RefugeListViewModel` tiene una
  mappa `covers: Map<String, MediaResponse?>` e chiede `media.photosOfRefuge(id)` **solo per le righe che entrano in
  vista** (`LaunchedEffect(refuge.id)` nella riga → `viewModel.loadCover(id)`, una richiesta per rifugio, mai due
  volte). URL firmato con `rememberSignedUrl` e `photoRequest`, come nel dettaglio. Senza foto o in errore: tessera
  `primaryContainer` con le curve di livello. La lista non aspetta mai le foto.
- Caricamento: 5 sagome di riga. Vuoto ed errore: `EmptyState` / `ErrorState`.
- **Richiesta al backend (facoltativa, non blocca):** `coverMediaId` e `freeBedsTonight` in `GET /refuges/nearby`.
  Con quelli la terza riga diventa "3,2 km · 6 posti stanotte" e sparisce la richiesta per riga. Finché non ci sono,
  niente posti nella lista (chiederli rifugio per rifugio sarebbero 20 richieste per aprire una scheda). Se si
  decide di farla: prima `MASTER_PLAN` §1 "Lacune del contratto", poi il backend, poi `checkApiContract`.

### 3.2 Pagina del rifugio

File: `refuge/ui/RefugeDetailScreen.kt` (riscrittura della parte visiva, il ViewModel cambia poco).

```
┌──────────────────────────────────────┐
│ (‹)                                  │  Indietro: isola tonda sopra la foto
│                                      │
│            F O T O                   │  320 dp, da bordo a bordo, sotto la barra di stato
│                                      │
│  © autore                    • • ○ • │  crediti a sinistra, puntini a destra
│╭────────────────────────────────────╮│  pannello che sale di 24 sopra la foto,
││ Rifugio Monte Curcio               ││  angoli alti 28, fondo `background`
││ 1 760 m · 24 posti · giu – set     ││  MetaLine
││                                    ││
││ ( ☎ Chiama )   ( ➤ Indicazioni )   ││  TonalPillButton 48, metà e metà
││                                    ││
││ Disponibilità                      ││  titleMedium
││ ┌───┐┌───┐┌───┐┌───┐┌───┐┌───┐┌──  ││  striscia: tocco = apre "Prenota" con
││ │ogg││sab││dom││lun││mar││mer││gi  ││  quell'arrivo già scelto
││ │ 14││ 15││ 16││ 17││ 18││ 19││ 2  ││
││ │ 6 ││ 2 ││ — ││ 8 ││ 8 ││ 8 ││ 8  ││  — = pieno; 2 in ambra "ultimi"
││ └───┘└───┘└───┘└───┘└───┘└───┘└──  ││
││                                    ││
││ Il rifugio                         ││
││ Testo, 4 righe…        Leggi tutto ││
││                                    ││
││ Servizi                            ││  griglia a 2 colonne dentro un GroupCard
││ ✓ Acqua potabile   ✓ Ristorante    ││
││ ✓ Doccia           ✓ Cani ammessi  ││
││                                    ││
││ Sentieri da qui                    ││  GroupCard + TrailRow (come oggi)
││ Gestione (solo staff)              ││  GroupCard, in fondo
│╰────────────────────────────────────╯│
│▒▒▒▒▒▒▒▒▒▒ sfumatura ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒│
│ Stanotte 6 posti liberi  ( Prenota ) │  StickyActionBar
└──────────────────────────────────────┘
```

Decisioni, nell'ordine della pagina:
1. **Testata.** Foto a tutta larghezza (`HorizontalPager`, com'è), niente più sfumatura che scioglie la foto nel
   fondo: la foto finisce netta sotto un pannello con gli angoli alti da 28 che le sale sopra di 24 dp. Puntini di
   pagina fino a 5 foto, oltre "2/7" nella capsula. Senza foto resta la mappa ferma (`RefugeHero`), stesso pannello.
2. **Titolo.** Nome in `headlineMedium`, sotto una `MetaLine` (quota · posti · stagione). Via le tre capsule e la riga
   della stagione con l'icona. "Stanotte N liberi" non sta più qui: è nella barra in basso, dove serve.
3. **Azioni del luogo.** "Chiama" e "Indicazioni" con `TonalPillButton`. Senza telefono "Indicazioni" resta a metà
   larghezza, allineato a sinistra (non si allarga: cambierebbe posto da un rifugio all'altro).
4. **Disponibilità subito**, prima della descrizione: è il motivo per cui si apre la pagina. La striscia (`NightStrip`)
   resta orizzontale per l'occhiata, ma ogni giorno è un **pulsante** (`Role.Button`, descrizione "sabato 15 novembre,
   2 posti liberi, prenota da questa data"); i giorni pieni sono spenti e non toccabili. Cella 64×88, raggio 12, senza
   bordo, fondo card; oggi con fondo muschio.
5. **Descrizione** tagliata a 4 righe con "Leggi tutto" (si apre sul posto, niente altra schermata).
6. **Servizi** in un `GroupCard`, griglia a due colonne (una colonna oltre il 130% di scala del testo), spunta
   `primary` + testo. Via le capsule con il bordo.
7. **Gestione** (`manage`) scende in fondo come gruppo con il suo titolo: oggi sta tra i contatti e la descrizione, in
   mezzo al percorso dell'escursionista.
8. **Barra fissa.** A sinistra una riga di stato (`titleMedium` il valore, `bodySmall` l'etichetta): "6 posti liberi /
   stanotte", oppure "Pieno stanotte / prossima notte libera lun 17"; a destra `PrimaryPillButton` "Prenota" largo
   quanto il testo (minimo 132), alto 56. Con il calendario in errore: solo il pulsante, a tutta larghezza.

### 3.3 Foglio "Prenota"

File nuovi: `refuge/ui/BookingSheet.kt`, `refuge/ui/StayCalendar.kt`. `BookRefugeScreen.kt` si elimina.
`BookRefugeViewModel` resta (rinominabile in `BookingSheetViewModel`), con le aggiunte sotto.

```
┌──────────────────────────────────────┐
│               ▬▬                     │  maniglia
│ Annulla       Prenota                │  titleLarge al centro
│ Rifugio Monte Curcio                 │  bodyMedium grigio
│                                      │
│  ‹   14 – 27 novembre   ›            │  due settimane per pagina
│  L    M    M    G    V    S    D     │  labelSmall grigio
│                      ┌──┐╭────────╮  │
│  ·    ·    ·    ·    │14││ 15  16 │  │  arrivo e partenza = tondi pieni primary,
│                      └──┘╰────────╯  │  in mezzo fascia primaryContainer
│  17   18   19   20   21   22   23    │
│   8    8    8    2    —    6    6    │  posti liberi sotto il numero (labelSmall)
│                                      │
│ ┌──────────────────────────────────┐ │
│ │ Persone                 (−) 2 (+)│ │  GroupCard, stepper 44
│ └──────────────────────────────────┘ │
│                                      │
│ sab 15 → lun 17 · 2 notti · 2 persone│  riepilogo vivo, liveRegion
│ ┌──────────────────────────────────┐ │
│ │            Conferma              │ │  PrimaryPillButton 56
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**Comportamento.**
- `ModalBottomSheet` a tutta altezza (`skipPartiallyExpanded = true`), maniglia visibile, trascinabile in giù per
  chiudere, "Annulla" in alto a sinistra (`sheets.md › Mobile`: "the Cancel button belongs on the leading edge of the
  top toolbar"; "Support swiping to dismiss a sheet"). Un solo foglio alla volta: niente dialoghi sopra.
- **Calendario a due settimane** (`StayCalendar`): griglia 7 colonne allineata ai giorni della settimana, celle da
  almeno 44×56, pagina = 14 notti = una chiamata a `refuges.availability(id, from)`; le frecce caricano le 14
  successive o precedenti (mai prima di oggi). Le pagine già lette restano in memoria nel ViewModel.
- **Selezione a intervallo**: primo tocco = arrivo (e partenza il giorno dopo, 1 notte); secondo tocco su un giorno
  successivo = partenza; tocco su un giorno precedente all'arrivo o terzo tocco = nuovo arrivo. Massimo
  `MAX_BOOKING_NIGHTS` (14): oltre, le celle sono spente.
- **Posti vincolanti**: una notte con `freeBeds < guests` non è selezionabile come notte del soggiorno (la cella di
  partenza sì: quella notte non si dorme lì). Se aumentando le persone l'intervallo scelto non regge più, lo stepper si
  ferma a quel numero e sotto compare "Solo N posti il dom 16". Il 409 `capacity-exceeded` del server resta gestito
  come oggi (banner sopra "Conferma"): qualcuno può prenotare nel frattempo.
- **Persone**: 1 … `min(capacity, 20)`. Oggi `maxGuests` è `capacity` ma `CreateBookingRequest.guests` ha massimo 20
  nel contratto: è un difetto da correggere qui.
- Apertura da un giorno della striscia: arrivo già scelto. Da "Prenota": prima notte libera.
- **Conferma nello stesso foglio**: il contenuto si dissolve nella conferma (spunta animata, "Prenotazione confermata"
  oppure "Prenotazione in invio" se è finita in coda, il biglietto di §3.4 in piccolo, "Fatto"). "Fatto" chiude il
  foglio; sotto, pagina e striscia si rileggono (`viewModel.refresh()`). Link secondario "Vedi le prenotazioni".
- Durante l'invio il foglio non si chiude trascinando (`confirmValueChange`), "Annulla" è spento.
- Il ViewModel del foglio vive con la voce `RefugeDetail` della pila (`koinViewModel` nella schermata, non nel
  contenuto del foglio): chiudere il foglio non annulla un invio in corso. L'outbox garantisce comunque il reinvio.

**ViewModel (aggiunte, il resto resta).**
```kotlin
data class BookRefugeUiState(
    val arrival: LocalDate,
    val nights: Int = 1,
    val guests: Int = 1,
    val pageStart: LocalDate,                                   // primo giorno della pagina mostrata
    val availability: Map<LocalDate, Int> = emptyMap(),         // posti liberi per notte, pagine già lette
    val loadingPage: Boolean = false,
    val pageError: UiMessage? = null,
    val submitting: Boolean = false,
    val done: BookingDone? = null,
    val error: UiMessage? = null,
)
fun onDayTapped(date: LocalDate)        // regole dell'intervallo qui, non nella UI
fun onPage(forward: Boolean)
fun open(arrival: LocalDate?)           // dal giorno toccato o dalla prima notte libera
```
`toString` continua a non stampare date. `onNightsChange` e `onArrivalChange` si tolgono se non servono più.
Dipendenza nuova del ViewModel: `RefugeRepository` (per `availability`).

**Testo grande (200%).** Le celle mostrano solo il numero del giorno; i posti del giorno scelto vanno nella riga del
riepilogo. La griglia non scorre mai in orizzontale.

**Accessibilità.** Ogni cella: `Role.Button`, `selected`, descrizione "sabato 15 novembre, 2 posti liberi" / "pieno";
arrivo e partenza annunciati ("arrivo", "partenza"). Le celle spente hanno `disabled()` e restano leggibili.

### 3.4 Segmento "Prenotazioni"

File: `refuge/ui/MyBookingsScreen.kt` → contenuto del segmento (`MyBookingsContent`), `MyBookingsViewModel.kt`.

```
│ ┌──────────────────────────────────┐ │
│ │ ⚠ Non siamo riusciti a prenotare │ │  rifiutate: banner prugna con "Ho capito"
│ │   Rifugio X · posti finiti       │ │
│ └──────────────────────────────────┘ │
│ Prossimo soggiorno                   │
│ ╭──────────────────────────────────╮ │  "biglietto": card raggio 28, curve di
│ │ ┌────┐  Rifugio Monte Curcio     │ │  livello tenui sul fondo
│ │ │ 15 │  sab 15 → lun 17          │ │  giorno Barlow 22, mese labelSmall
│ │ │NOV │  2 notti · 2 persone      │ │
│ │ └────┘  Tra 3 giorni             │ │  primary
│ │ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ │ │  strappo tratteggiato
│ │ ( ☎ Chiama )  ( ➤ Indicazioni )  │ │
│ ╰──────────────────────────────────╯ │
│ In arrivo                            │
│ ┌──────────────────────────────────┐ │
│ │ 22  Rifugio Botte Donato       › │ │  blocco data + nome + "1 notte · 4 persone"
│ │ NOV 1 notte · 4 persone          │ │
│ │ ──────────────────────────────── │ │
│ │ 03  Rifugio Y        In invio ◌  │ │  in coda: stesso gruppo, stato a destra
│ │ DIC 2 notti · 2 persone          │ │
│ └──────────────────────────────────┘ │
│ Passate e annullate                  │  gruppo più spento (testo onSurfaceVariant)
│ ┌──────────────────────────────────┐ │
│ │ 02  Rifugio Z        Annullata   │ │
│ └──────────────────────────────────┘ │
```

- **Ordine per chi parte**: prima il soggiorno più vicino (biglietto), poi "In arrivo" dalla data più vicina, poi
  "Passate e annullate". Oggi è un elenco solo, dal più lontano nel futuro. Il raggruppamento si fa in memoria sulle
  pagine caricate. Limite noto: il server ordina dalla più lontana e pagina a offset, quindi con più di una pagina di
  prenotazioni future la più vicina può stare in una pagina non ancora letta; si caricano le pagine finché l'ultima
  riga letta non è nel passato (di norma una o due). Se pesa, si chiede al backend un parametro d'ordine.
- **Lo stato si scrive solo quando non è quello atteso.** "Confermata" è il caso normale: niente capsula. Si mostrano
  "In invio" (con spinner piccolo), "Annullata", "Scaduta".
- **Blocco data** a sinistra (48 di larghezza): giorno in `headlineSmall`, mese in `labelSmall` maiuscolo. Sostituisce
  la stringa "sab 10/10 → lun 12/10 · 2 notti · 2 persone" su una riga sola, che oggi va a capo male.
- **Una riga = un tocco = un foglio** (`BookingActionsSheet`, altezza media): riepilogo, "Apri il rifugio",
  "Indicazioni", "Chiama", e in fondo "Annulla prenotazione" in prugna (solo se `canCancel`), che apre la conferma
  `AlertDialog` di oggi. Prima di aprire il dialogo il foglio si chiude (`sheets.md › Best practices`: "Display only
  one sheet at a time"). Via il pulsante "Annulla" su ogni riga.
- Per rifugio servono nome, telefono e posizione: `MyBookingsUiState.refugeNames: Map<String, String>` diventa
  `refuges: Map<String, RefugeResponse>` (la chiamata `refuges.get(id)` c'è già, oggi se ne butta via il resto).
  `toString` resta senza nomi.
- Trascinare in giù per aggiornare: resta. Paginazione: resta (`groupedItem`).
- Vuoto: `EmptyState` "Nessuna prenotazione" + "Esplora i rifugi" (passa all'altro segmento).
- Pallino sul segmento "Prenotazioni" quando ci sono righe in invio o rifiutate (con descrizione per gli screen
  reader: "Prenotazioni, 1 da controllare").

---

## 4. Resto dell'app

Stesso sistema, senza cambiare cosa fanno le schermate. Prima di toccare un file lo si legge tutto.

| Schermata | Cosa cambia |
|---|---|
| **Mappa** (`trail/ui/MapScreen.kt`, `MapControls.kt`) | Resta a tutto schermo. Controlli: ombra tinta, margini sulla griglia (16 dai bordi, 12 tra loro). Card di sentiero, POI e pericolo con la stessa anatomia: raggio 28, titolo `titleMedium`, `MetaLine`, una azione. "Vicino a te": foglio con `SegmentedControl` al posto delle sue schede. SOS: invariato |
| **Sentiero** (`trail/ui/TrailDetailScreen.kt`) | Stessa anatomia del rifugio: anteprima del percorso in testata, pannello che sale con angoli 28, nome `headlineMedium`, stato come badge. Riga di **tessere dei numeri** (lunghezza, dislivello, difficoltà): valore `headlineSmall`, etichetta `labelSmall` sotto. Sezioni in `GroupCard`. "Segnala un pericolo" in `StickyActionBar` |
| **Segnalazione, POI, ticket, squadra, posti letto** (form) | `FormScaffold` nuovo (§2.1): barra compatta, campi, azione fissa con sfumatura. Nient'altro |
| **Accesso, registrazione, reset** (`auth/ui/AuthScreens.kt`) | Unico posto con `ForestHeader` alto (200): è il momento di marca. Campi e pulsanti invariati |
| **Profilo** (`profile/ui/ProfileScreens.kt`) | `LargeTitlePage`; in testa avatar 72 + nome + email in una card. Gruppi com'è. Nuova riga "Le mie prenotazioni" in Attività. Selettore del tema con `SegmentedControl` |
| **Le mie segnalazioni, coda del gestore** | `LargeTitlePage` + stati condivisi. Righe invariate |
| **Allerte, dettaglio allerta** (`rescue/ui`) | `LargeTitlePage`, righe in gruppo, stati condivisi. Azioni dell'operatore in `StickyActionBar` |
| **Gestione, ticket** (`staff/ui`) | `LargeTitlePage` con `SegmentedControl` nello slot `below` |
| **SOS** (`sos/ui/SosScreen.kt`) | Solo i token (tipografia, spaziatura). Layout, 72 dp, pressione di 3 s e test al 200% non si toccano |
| **MFA** | `FormScaffold` nuovo; `SecureScreen` e `sensitiveClipEntry` restano |

Nota sul tema nel Profilo: le linee guida Apple preferiscono seguire il sistema senza un interruttore nell'app. Qui
resta (decisione A18): all'aperto forzare il chiaro al sole è una funzione, non un vezzo.

---

## 5. Fasi

Ogni fase: leggere i file elencati, implementare, poi
`./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`, prova sull'emulatore in chiaro e in scuro e con il
testo al 200%. iOS si compila solo in CI: niente API solo-Android in `commonMain`, nomi dei test senza
`, . ; : / < > [ ]`. Stringhe nuove in `composeResources/values/strings.xml`; quelle non più usate si tolgono (poi
`./gradlew :shared:clean` se compare "Unresolved reference _collect...").

### Fase 0 — Fondamenta (nessuna schermata cambia aspetto in modo sostanziale)
1. `Theme.kt`: scala tipografica di §1.2; controllare ogni uso di `displaySmall`, `headlineMedium`, `headlineSmall`.
2. `Color.kt`: ambra per il tema scuro, se manca (§1.1), con il contrasto ricalcolato nel commento.
3. `core/ui/Glyphs.kt`: spostare `Glyph` / `GlyphIcon`; aggiungere solo i glifi che la fase 1 usa.
4. `core/ui/PageLayout.kt`: `LargeTitlePage`, `SegmentedControl` (estratto da `ManagementTabs` e dal selettore del
   tema, che passano a usarlo), `StickyActionBar`, `MetaLine`; `GroupCard` / `groupedItem` senza bordo.
5. `core/ui/States.kt`: `EmptyState`, `ErrorState`, `Skeleton`; `isReduceMotionEnabled()` expect/actual.
6. `FormControls.kt`: `TonalPillButton`; `FormScaffold` nuovo.
7. Test: quelli di UI esistenti (`LoginScreenTest`, `SosScreenTest`, `BookRefugeScreenTest`) devono restare verdi.

**Fatto quando:** build e 348 test verdi; i form hanno la barra compatta; nessun `Message` privato è ancora stato
toccato (si sostituiscono schermata per schermata nelle fasi dopo).

### Fase 1 — Pagina del rifugio (§3.2)
`RefugeDetailScreen.kt`. Il pulsante "Prenota" e i giorni della striscia aprono ancora `Route.BookRefuge` (il foglio
arriva nella fase 2): `onBook` prende in più `arrival: LocalDate?`.
**Fatto quando:** pagina come nel disegno con e senza foto, con e senza telefono, calendario in errore, da gestore.

### Fase 2 — Foglio "Prenota" (§3.3)
`StayCalendar.kt`, `BookingSheet.kt`, ViewModel, Koin (`core/di`), via `BookRefugeScreen.kt`, `Route.BookRefuge` e il
suo `composable<>`.
Test: `BookRefugeViewModelTest` (regole dell'intervallo, notti piene non selezionabili, massimo 14 notti e 20 persone,
pagina successiva, doppio invio); `BookRefugeScreenTest` → `BookingSheetTest` (scegli due date, conferma, vedi
"Prenotazione confermata"; in coda vedi "in invio"; 409 mostra il banner e lascia la selezione).
**Fatto quando:** sull'emulatore la prenotazione di 2 notti per 2 persone si fa in 5 tocchi; in modalità aereo finisce
"in invio" e parte da sola al ritorno della rete (come M5.2).

### Fase 3 — Scheda Rifugi (§3.1, §3.4)
`RefugeListScreen.kt` (segmenti, righe con miniatura), `RefugeListViewModel.kt` (`covers`), `MyBookingsScreen.kt` →
contenuto del segmento, `MyBookingsViewModel.kt` (`refuges`, gruppi), `BookingActionsSheet`, via `Route.MyBookings`.
Riga "Le mie prenotazioni" nel Profilo.
Test: gruppi (prossimo, in arrivo, passate) con `today` iniettato; annullamento dal foglio; copertine chieste una
volta sola.
**Fatto quando:** le quattro situazioni si vedono giuste: nessuna prenotazione, una in invio, una rifiutata, miste.

### Fase 4 — Resto dell'app (§4)
Una schermata per volta nell'ordine della tabella; a ogni schermata il suo `Message` privato sparisce. Alla fine
`PageHeader`, `HeaderPill`, `HeaderLink` e `PageHeaderHeight` si eliminano se non hanno più usi.

### Fase 5 — Rifinitura e documenti
1. Momento della conferma (spunta, tattile), respiro delle sagome, "Riduci movimento".
2. Passata di accessibilità: TalkBack su calendario e fogli, 200% su ogni schermata toccata, contrasti nuovi
   calcolati (ambra, testo sul muschio) e scritti nei commenti di `Color.kt`.
3. Chiaro/scuro su ogni schermata; orizzontale sulle pagine con `PageMaxWidth`.
4. Build per l'iPhone (`gh workflow run ios-device.yml ...`, `install.ps1`): fogli, gesto indietro, safe area.
5. `MASTER_PLAN.md` §2 e `CLAUDE.md` (righe "Stile", "Interfaccia", "Struttura", stato) aggiornati a quello che c'è.

---

## 6. Vincoli che valgono in ogni fase (da `CLAUDE.md`)

- Colori solo da `MaterialTheme.colorScheme` (eccezioni già note). Rosso solo SOS, `CRITICAL`, `CLOSED`.
- Bersagli: 72 emergenza, 56 invio dei form e "Conferma", 48 il resto (le celle del calendario: 44 di larghezza
  visibile dentro una cella alta almeno 56, quindi area ≥ 48 in altezza; se a 320 dp di larghezza non ci stanno 7
  colonne da 44, si riduce il margine del foglio a 12, non la cella).
- `WindowInsets.safeDrawing`, mai `systemBars` da solo. Ogni schermata con fondo pieno. Indietro proprio sulle
  schermate spinte.
- `contentDescription` solo dove l'icona parla da sola. Niente dati personali nei log (date e nomi dei rifugi
  compresi: i `toString` degli stati restano come sono).
- Enum del server: `unknown_default_open_api` gestito in ogni `when`.
- Commenti in italiano, nomi in inglese. Nessuna dipendenza nuova. L'icona dell'app non si tocca.
- Se una decisione di questo piano non regge sul codice, ci si ferma e lo si dice prima di cambiarla.

## 7. Fuori dal piano (deciso)

- Ricerca e filtri nella lista dei rifugi: sono poche decine e ordinati per distanza. Si aggiungono quando il backend
  dà i posti di stanotte nella lista (il filtro che serve davvero è "con posti").
- Sfocature e trasparenze: §1.5.
- Azioni a scorrimento sulle righe: il foglio delle azioni copre lo stesso bisogno con meno codice.
- Glifi per ogni servizio del rifugio (doccia, cucina…): spunta e testo bastano; se ne riparla a redesign chiuso.
- Layout a due colonne per tablet.
