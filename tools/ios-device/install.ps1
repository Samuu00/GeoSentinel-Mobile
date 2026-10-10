# Installa sull'iPhone collegato via USB l'ultima app costruita dal workflow "iOS device build" (vedi README.md).
# Uso, da PowerShell nella cartella del progetto:  .\tools\ios-device\install.ps1
# -Ipa <percorso> salta il download e installa un .ipa già scaricato.
param([string]$Ipa)

$ErrorActionPreference = 'Stop'
$Distro = 'Ubuntu'

# 1. L'app: l'artifact dell'ultima run riuscita del workflow
if (-not $Ipa) {
    $runId = gh run list --workflow ios-device.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId'
    if (-not $runId) { throw "Nessuna run riuscita di 'iOS device build': lanciala da GitHub Actions (README.md)." }
    $dir = Join-Path $env:TEMP "geosentinel-ipa-$runId"
    if (-not (Test-Path "$dir\GeoSentinel.ipa")) { gh run download $runId --name GeoSentinel-ipa --dir $dir }
    $Ipa = "$dir\GeoSentinel.ipa"
}
Write-Host "App: $Ipa"

# 2. L'iPhone dentro WSL: usbipd lo stacca da Windows e lo passa alla distro (il bind, una volta sola, è nel README)
$apple = usbipd list | Select-String -Pattern '^\s*(\S+)\s+05ac:' | Select-Object -First 1
if (-not $apple) { throw "iPhone non trovato: collegalo via USB, sbloccalo e accetta 'Autorizza questo computer'." }
$busId = $apple.Matches[0].Groups[1].Value
if ($null -eq (usbipd list | Select-String -Pattern "^\s*$busId\s.*Attached")) {
    usbipd attach --wsl $Distro --busid $busId
    Start-Sleep -Seconds 2
}

# 3. Firma con l'Apple ID salvato da `xtool auth` e installazione. usbmuxd parte qui perché WSL non ha udev
$wslIpa = (wsl -d $Distro -- wslpath -a ($Ipa -replace '\\', '/')).Trim()
wsl -d $Distro -u root -- bash -lc "pgrep -x usbmuxd >/dev/null || usbmuxd -U root; sleep 1; xtool install '$wslIpa'"
