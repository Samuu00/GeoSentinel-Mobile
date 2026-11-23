# Installa sull'iPhone collegato via USB l'ultima app prodotta dal workflow "iOS device build" (vedi README.md).
# Lo script scarica il pacchetto .ipa con la CLI di GitHub, si assicura che il servizio Apple di Windows sia attivo,
# inoltra la sua porta verso WSL e infine firma e installa l'app con xtool, che gira dentro WSL.
# Uso, da PowerShell nella cartella del progetto: .\tools\ios-device\install.ps1
# Con -Ipa <percorso> salta il download e installa un .ipa già scaricato.
param([string]$Ipa)

$ErrorActionPreference = 'Stop'
# Distribuzione WSL in cui è installato xtool e porta del servizio usbmuxd di Apple su Windows.
$Distro = 'Ubuntu'
$MuxPort = 27015

# Passo 1: l'app è l'artefatto dell'ultima run riuscita del workflow, scaricato una sola volta in una cartella
# temporanea.
if (-not $Ipa) {
    $runId = gh run list --workflow ios-device.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId'
    if (-not $runId) { throw "Nessuna run riuscita di 'iOS device build': lanciala da GitHub Actions (README.md)." }
    $dir = Join-Path $env:TEMP "geosentinel-ipa-$runId"
    if (-not (Test-Path "$dir\GeoSentinel.ipa")) { gh run download $runId --name GeoSentinel-ipa --dir $dir }
    $Ipa = "$dir\GeoSentinel.ipa"
}
Write-Host "App: $Ipa"

# Passo 2: il telefono si raggiunge tramite il servizio Apple di Windows (app "Dispositivi Apple", porta 27015 su
# localhost) e non tramite usbipd, con cui il caricamento si blocca (usbmuxd: "message was too large"). Se il
# servizio non è in ascolto si avvia l'app e si aspetta fino a un minuto.
if (-not (Get-NetTCPConnection -LocalPort $MuxPort -State Listen -ErrorAction SilentlyContinue)) {
    Start-Process 'shell:AppsFolder\AppleInc.AppleDevices_nzyj5cx40ttqa!App'
    for ($i = 0; $i -lt 30 -and -not (Get-NetTCPConnection -LocalPort $MuxPort -State Listen -ErrorAction SilentlyContinue); $i++) {
        Start-Sleep -Seconds 2
    }
}

# Passo 3: WSL raggiunge Windows sull'IP del suo gateway, che può cambiare a ogni avvio. L'inoltro della porta
# verso localhost si ricrea, con i permessi di amministratore, solo se manca per l'IP attuale.
$hostIp = ((wsl -d $Distro -- ip route list default) -split '\s+')[2]
if (-not ((netsh interface portproxy show v4tov4) -match "$([regex]::Escape($hostIp))\s+$MuxPort")) {
    Write-Host "Inoltro $hostIp`:$MuxPort -> 127.0.0.1 (serve l'autorizzazione da amministratore)"
    Start-Process netsh -Verb RunAs -Wait -ArgumentList 'interface', 'portproxy', 'set', 'v4tov4', "listenport=$MuxPort",
        "listenaddress=$hostIp", "connectport=$MuxPort", 'connectaddress=127.0.0.1'
}

# Passo 4: firma con l'Apple ID salvato da `xtool auth` e installazione. usbmuxd di WSL viene fermato perché xtool
# usi quello di Windows tramite l'inoltro.
$wslIpa = (wsl -d $Distro -- wslpath -a ($Ipa -replace '\\', '/')).Trim()
wsl -d $Distro -u root -- bash -c "pkill -x usbmuxd; export USBMUXD_SOCKET_ADDRESS=$hostIp`:$MuxPort; xtool install '$wslIpa'"
