# App su un iPhone vero dal PC Windows (xtool)

Kotlin/Native non può creare il framework iOS di `shared` su Windows: l'app la costruisce il workflow
`iOS device build` su un runner macOS, senza firma. [xtool](https://github.com/xtool-org/xtool) (in WSL) la firma
con il tuo Apple ID e la installa sull'iPhone collegato via USB.

Con un Apple ID gratuito: l'app scade dopo 7 giorni (si reinstalla), al massimo 3 app installate così, **niente
push** (il profilo gratuito non ha `aps-environment`). Il bundle id lo riscrive xtool con il tuo team.

## Una volta sola

Già installati sul PC (10/10): `xtool`, `usbmuxd` e `libimobiledevice-utils` in WSL (Ubuntu, utente root),
`usbipd-win` e `gh` su Windows.

1. **GitHub CLI**: `gh auth login` (serve per scaricare l'artifact).
2. **Apple ID per xtool**: `wsl -d Ubuntu -- xtool auth login`, modalità *Password*. Le credenziali vanno solo ad
   Apple; xtool usa API private di Apple, quindi va bene anche un Apple ID secondario.
3. **iPhone a WSL**: collega l'iPhone, sbloccalo e accetta "Autorizza questo computer". Poi, in PowerShell **come
   amministratore**: `usbipd list` (riga con `05ac:`, "Apple Mobile Device"), poi `usbipd bind --busid <BUSID>`.
4. **Backend raggiungibile dall'iPhone** (stessa rete Wi-Fi del PC), in PowerShell come amministratore:
   `New-NetFirewallRule -DisplayName "GeoSentinel backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private`
5. **Sull'iPhone**: Impostazioni → Privacy e sicurezza → Modalità sviluppatore (attiva dopo il primo install, poi
   riavvio). Al primo avvio: Impostazioni → Generali → VPN e gestione dispositivi → fidati del tuo Apple ID.

## Ogni volta

1. GitHub → Actions → **iOS device build** → *Run workflow*, con l'URL del backend visto dall'iPhone:
   `http://<IP del PC>:8080` (l'IP con `ipconfig`, es. `http://192.168.1.188:8080`). Circa 15–20 minuti.
2. Backend acceso sul PC (`docker compose up -d` e `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`).
3. iPhone collegato e sbloccato, poi: `.\tools\ios-device\install.ps1`.

Se `xtool install` dà `AFCClient.Error.muxError`: stacca e riattacca l'iPhone (`usbipd detach --busid <BUSID>`,
poi di nuovo lo script). Al primo collegamento in WSL l'iPhone chiede di nuovo "Autorizza questo computer".
