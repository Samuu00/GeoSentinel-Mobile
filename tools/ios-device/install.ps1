# Installa sull'iPhone collegato via USB l'ultima app costruita dal workflow "iOS device build" (vedi README.md).
# Uso, da PowerShell nella cartella del progetto:  .\tools\ios-device\install.ps1
# -Ipa <percorso> salta il download e installa un .ipa già scaricato.
param([string]$Ipa)

$ErrorActionPreference = 'Stop'
$Distro = 'Ubuntu'
$MuxPort = 27015

# 1. L'app: l'artifact dell'ultima run riuscita del workflow
if (-not $Ipa) {
    $runId = gh run list --workflow ios-device.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId'
    if (-not $runId) { throw "Nessuna run riuscita di 'iOS device build': lanciala da GitHub Actions (README.md)." }
    $dir = Join-Path $env:TEMP "geosentinel-ipa-$runId"
    if (-not (Test-Path "$dir\GeoSentinel.ipa")) { gh run download $runId --name GeoSentinel-ipa --dir $dir }
    $Ipa = "$dir\GeoSentinel.ipa"
}
Write-Host "App: $Ipa"

# 2. Il telefono passa dal servizio Apple di Windows ("Dispositivi Apple", porta 27015 su localhost), non da usbipd:
#    con usbipd il caricamento si blocca (usbmuxd: "message was too large"). Il servizio parte con l'app.
if (-not (Get-NetTCPConnection -LocalPort $MuxPort -State Listen -ErrorAction SilentlyContinue)) {
    Start-Process 'shell:AppsFolder\AppleInc.AppleDevices_nzyj5cx40ttqa!App'
    for ($i = 0; $i -lt 30 -and -not (Get-NetTCPConnection -LocalPort $MuxPort -State Listen -ErrorAction SilentlyContinue); $i++) {
        Start-Sleep -Seconds 2
    }
}

# 3. WSL raggiunge Windows sull'IP del suo gateway, che può cambiare a ogni avvio: l'inoltro verso localhost si
#    rifà (da amministratore) solo se manca per l'IP attuale
$hostIp = ((wsl -d $Distro -- ip route list default) -split '\s+')[2]
if (-not ((netsh interface portproxy show v4tov4) -match "$([regex]::Escape($hostIp))\s+$MuxPort")) {
    Write-Host "Inoltro $hostIp`:$MuxPort -> 127.0.0.1 (serve l'autorizzazione da amministratore)"
    Start-Process netsh -Verb RunAs -Wait -ArgumentList 'interface', 'portproxy', 'set', 'v4tov4', "listenport=$MuxPort",
        "listenaddress=$hostIp", "connectport=$MuxPort", 'connectaddress=127.0.0.1'
}

# 4. Firma con l'Apple ID salvato da `xtool auth` e installazione
$wslIpa = (wsl -d $Distro -- wslpath -a ($Ipa -replace '\\', '/')).Trim()
wsl -d $Distro -u root -- bash -c "pkill -x usbmuxd; export USBMUXD_SOCKET_ADDRESS=$hostIp`:$MuxPort; xtool install '$wslIpa'"
