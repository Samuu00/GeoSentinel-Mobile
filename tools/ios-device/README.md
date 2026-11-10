# App su un iPhone vero dal PC Windows (xtool)

Kotlin/Native non può creare il framework iOS di `shared` su Windows: l'app la costruisce il workflow
`iOS device build` su un runner macOS, senza firma. [xtool](https://github.com/xtool-org/xtool) (in WSL) la firma
con il tuo Apple ID e la installa sull'iPhone collegato via USB.

Con un Apple ID gratuito: l'app scade dopo 7 giorni (si reinstalla), al massimo 3 app installate così, **niente
push** (il profilo gratuito non ha `aps-environment`). Il bundle id lo riscrive xtool con il tuo team.

## Come arriva al telefono

Il telefono resta collegato a **Windows** e passa dal servizio dell'app "Dispositivi Apple" (`127.0.0.1:27015`).
WSL lo raggiunge sull'IP del suo gateway (es. `172.17.16.1`) grazie a un `netsh portproxy`, e xtool lo usa con
`USBMUXD_SOCKET_ADDRESS`. Niente usbipd: con usbipd l'iPhone si vede, ma il caricamento dell'app si blocca
(`usbmuxd`: "message was too large", provato il 10/10). È la strada indicata dalla guida di xtool per WSL.

## Una volta sola (fatto il 10/10 su questo PC)

- In WSL (Ubuntu, utente root): `xtool`, `libimobiledevice-utils`, `libfuse2t64` (per l'AppImage) e `zip`
  (xtool lo usa per rifare l'ipa firmato). Su Windows: `gh`, l'app **Dispositivi Apple** (Microsoft Store).
- `gh auth login` (per scaricare l'artifact) e `wsl -d Ubuntu -- xtool auth login` (modalità *Password*: xtool usa
  API private di Apple, va bene anche un Apple ID secondario).
- iPhone collegato e sbloccato: "Autorizza questo computer" (vale per Windows). Deve comparire in Dispositivi Apple.
- Firewall, da PowerShell come amministratore (l'inoltro `portproxy` lo crea lo script quando manca):
  `New-NetFirewallRule -DisplayName "WSL usbmuxd 27015" -Direction Inbound -InterfaceAlias "vEthernet (WSL (Hyper-V firewall))" -Protocol TCP -LocalPort 27015 -Action Allow`
- Backend raggiungibile dall'iPhone (stessa rete Wi-Fi), rete del PC impostata come **Privata**:
  `New-NetFirewallRule -DisplayName "GeoSentinel backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private`
- Sull'iPhone, dopo il primo install: Impostazioni → Generali → VPN e gestione dispositivi → autorizza il tuo
  Apple ID; Impostazioni → Privacy e sicurezza → Modalità sviluppatore (poi riavvio).

## Ogni volta

1. GitHub → Actions → **iOS device build** → *Run workflow*, con l'URL del backend visto dall'iPhone:
   `http://<IP del PC>:8080` (l'IP con `ipconfig`, es. `http://192.168.1.188:8080`). Circa 15 minuti.
   Da terminale: `gh workflow run ios-device.yml -f api_base_url=http://192.168.1.188:8080`.
2. Backend acceso sul PC (`docker compose up -d`), con le foto firmate sull'IP del PC:
   `MINIO_PUBLIC_ENDPOINT=http://<IP del PC>:9000 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.
   Senza, gli URL delle foto puntano a `localhost`, che sull'iPhone è il telefono. Per l'emulatore Android invece
   serve `http://10.0.2.2:9000`: in debug Android permette HTTP in chiaro solo verso `10.0.2.2`
   (`network_security_config`), quindi con l'IP del PC le foto sull'emulatore non si aprono. Uno dei due alla volta.
3. iPhone collegato e sbloccato, poi: `.\tools\ios-device\install.ps1` (circa 2 minuti).

Se xtool dice `noDevice` o non trova il telefono: sblocca l'iPhone, controlla che compaia in Dispositivi Apple
(se chiede di nuovo l'autorizzazione, accetta) e rilancia lo script.

Se xtool dice `Operation now in progress`, WSL non raggiunge la porta 27015: la regola del firewall resta legata
alla scheda `vEthernet (WSL …)` per GUID, che Windows può ricreare dopo un riavvio. Da PowerShell come
amministratore: `Set-NetFirewallRule -DisplayName "WSL usbmuxd 27015" -InterfaceAlias "vEthernet (WSL (Hyper-V firewall))"`
(provato il 12/10).

Se lo script resta su `Waiting for device to be connected...` con il telefono sbloccato e visibile in Dispositivi
Apple, controlla `netstat -an | Select-String 27015`: se manca `172.17.x.1:27015 LISTENING`, l'inoltro è configurato
ma il servizio IP Helper non lo applica (la scheda di WSL è nata dopo di lui). Da PowerShell come amministratore:
`Restart-Service iphlpsvc -Force` (provato il 19/10).
