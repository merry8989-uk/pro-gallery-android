<#
  adb_export_android_data.ps1

  Copies Android/data (or one app's folder inside it) off a NON-ROOTED Android
  phone to this PC, using the official Android Debug Bridge.

  From Android 11 the OS refuses Android/data and Android/obb to every app, even
  with "All files access" and the folder picker. The `adb` shell runs with
  broader rights, so a cable + this script copies the files out. This is the
  documented developer interface, not a bypass.

  Usage:
    .\adb_export_android_data.ps1
    .\adb_export_android_data.ps1 -Package com.whatsapp
    .\adb_export_android_data.ps1 -Package com.whatsapp -Destination C:\export
#>

param(
    [string]$Package = "",
    [string]$Destination = ".\android-data-export"
)

$ErrorActionPreference = "Stop"
$remote = "/sdcard/Android/data"

function Fail($message) {
    Write-Host ""
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Fail @"
adb not found on PATH.
  Install Android Platform Tools: https://developer.android.com/tools/releases/platform-tools
  Then reopen PowerShell and run this script again.
"@
}

Write-Host "Checking for a connected phone..."
$devices = (adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }

if (-not $devices) {
    adb devices
    Fail @"
No authorised device found.
  On the phone: Settings -> About phone -> tap Build number 7 times,
  then Settings -> Developer options -> enable USB debugging,
  plug in the cable and accept the 'Allow USB debugging?' prompt.
"@
}

$target = $remote
if ($Package -ne "") { $target = "$remote/$Package" }

Write-Host "Phone found. Checking access to $target ..."
adb shell "ls '$target'" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "The shell could not list $target on this device."
    Write-Host "Some OEM builds restrict the shell user too. Options:"
    Write-Host "  1. Try again with the phone unlocked and the screen on."
    Write-Host "  2. If the app is debuggable, its own folder can be read with run-as."
    Write-Host "  3. Check Android/media instead — WhatsApp and Telegram keep their"
    Write-Host "     media there, and the gallery app CAN read it with 'All files access'."
    exit 2
}

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
Write-Host "Copying $target -> $Destination"

adb pull $target $Destination
if ($LASTEXITCODE -ne 0) {
    Write-Host "The copy stopped part way. Partial files are in $Destination." -ForegroundColor Yellow
    Write-Host "Re-run the script to resume what is missing."
    exit 3
}

Write-Host ""
Write-Host "Done. Copied into: $Destination"
Write-Host ""
Write-Host "To browse these in ProGallery:"
Write-Host "  1. Push the folder back to the phone:"
Write-Host "       adb push `"$Destination`" /sdcard/Download/AndroidExport"
Write-Host "  2. In ProGallery: Deep Scan -> add that folder -> Scan."
Write-Host ""
Write-Host "Nothing was modified on the phone; the export is a read-only copy."
