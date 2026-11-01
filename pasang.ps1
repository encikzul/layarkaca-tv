# LayarKaca: pemasang mudah untuk Windows 10 dan 11, dalam Bahasa Melayu.
#
#   Buka PowerShell dan tampal:
#   irm https://raw.githubusercontent.com/encikzul/layarkaca-tv/main/pasang.ps1 | iex
#
# atau, dari folder projek: powershell -ExecutionPolicy Bypass -File pasang.ps1
# Sama seperti pasang.sh: tanya IP TV, sambung, sandaran, buang iklan untuk model yang diuji, muat turun dan
# semak APK, pasang, benarkan panel waktu solat, buka pemilih lokasi di TV. Menu yang sama boleh membatalkan
# semuanya. Ujian: tetapkan $env:APK ke fail .apk untuk memasang APK itu, bukan dari Releases.
# (Fail ini ASCII sahaja supaya Windows PowerShell 5.1 membacanya dengan betul.)

# Continue, not Stop: Windows PowerShell 5.1 turns a native program's stderr into a terminating error under
# Stop. Failures are checked by exit code; the helper scripts still stop on their own errors.
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
try { Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force -ErrorAction Stop } catch { }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

$Repo = if ($env:LAYARKACA_REPO) { $env:LAYARKACA_REPO } else { 'encikzul/layarkaca-tv' }
$Conf = Join-Path $HOME '.layarkaca.json'
$Backups = Join-Path $HOME 'layarkaca-sandaran'
$Pkg = 'com.gathin.tvhome'

function Say([string]$t) { Write-Host $t }
function Step([string]$t) { Write-Host ''; Write-Host $t -ForegroundColor White }
function Ok([string]$t) { Write-Host "[OK] $t" -ForegroundColor Green }
function Warn([string]$t) { Write-Host "[!] $t" -ForegroundColor Yellow }
function Fail([string]$t) { Write-Host ''; Write-Host "[X] $t" -ForegroundColor Red; throw 'berhenti' }
function YesNo([string]$q) { $a = Read-Host "$q (y/t)"; return ($a -eq 'y' -or $a -eq 'Y' -or $a -eq 'ya') }

# ------------------------------------------------------------------------------------------------ setup

function Find-Project {
    if ($PSScriptRoot -and (Test-Path (Join-Path $PSScriptRoot 'launcher\build-and-install.ps1'))) {
        return $PSScriptRoot
    }
    $dir = Join-Path $HOME 'layarkaca-tv'
    Step 'Memuat turun LayarKaca...'
    $zip = Join-Path $env:TEMP 'layarkaca.zip'
    $tmp = Join-Path $env:TEMP 'layarkaca-baru'
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "https://codeload.github.com/$Repo/zip/refs/heads/main" -OutFile $zip -ErrorAction Stop
    } catch { Fail 'Tidak dapat memuat turun projek. Semak sambungan internet dan cuba lagi.' }
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force -ErrorAction Stop
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    $venv = Join-Path $dir 'tools\venv'
    if (Test-Path $venv) { Move-Item $venv (Join-Path $inner.FullName 'tools\venv') }
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    Move-Item $inner.FullName $dir
    Remove-Item $tmp, $zip -Recurse -Force -ErrorAction SilentlyContinue
    Ok "Projek di $dir"
    return $dir
}

function Find-Python {
    $candidates = @()
    foreach ($v in @('313', '312', '311', '310')) {
        $candidates += Join-Path $env:LOCALAPPDATA "Programs\Python\Python$v\python.exe"
        $candidates += "C:\Program Files\Python$v\python.exe"
    }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher) {
        $exe = & py -3 -c 'import sys; print(sys.executable)' 2>$null
        if ($LASTEXITCODE -eq 0 -and $exe) { return "$exe".Trim() }
    }
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python -and $python.Source -notlike '*WindowsApps*') { return $python.Source }   # not the Store stub
    return $null
}

function Setup-Python([string]$dir) {
    Set-Location $dir
    $venvPy = Join-Path $dir 'tools\venv\Scripts\python.exe'
    if (Test-Path $venvPy) {
        & $venvPy -c 'import adb_shell' 2>$null
        if ($LASTEXITCODE -eq 0) { Ok 'Alat sedia'; return $venvPy }
    }
    $py = Find-Python
    if (-not $py) {
        Step 'Memasang Python (sekali sahaja, 2 hingga 5 minit)...'
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            Fail 'Python tiada dan winget tidak ada. Pasang Python 3 dari python.org (tandakan "Add python.exe to PATH"), kemudian cuba lagi.'
        }
        winget install -e --id Python.Python.3.12 --scope user --silent --accept-package-agreements --accept-source-agreements | Out-Null
        $py = Find-Python
        if (-not $py) { Fail 'Python gagal dipasang. Pasang Python 3 dari python.org, kemudian cuba lagi.' }
    }
    Step 'Menyediakan alat (sekali sahaja, 1 hingga 2 minit)...'
    $venv = Join-Path $dir 'tools\venv'
    if (Test-Path $venv) { Remove-Item $venv -Recurse -Force }
    & $py -m venv $venv
    if ($LASTEXITCODE -ne 0) { Fail 'Gagal menyediakan Python.' }
    & $venvPy -m pip install -q -r (Join-Path $dir 'tools\requirements.txt')
    if ($LASTEXITCODE -ne 0) { Fail 'Gagal memasang alat Python. Semak sambungan internet dan cuba lagi.' }
    Ok 'Alat sedia'
    return $venvPy
}

function Load-Conf {
    if (Test-Path $Conf) { try { return Get-Content $Conf -Raw | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ TvHost = ''; List = ''; SetupOff = '' }
}
function Save-Conf { $script:C | ConvertTo-Json | Out-File -Encoding ascii $Conf }

function T { & $script:Py -I (Join-Path $script:Dir 'tools\tv.py') @args }

function Connect-Tv {
    Step 'Langkah 1: sambung ke TV'
    Say 'Di TV: Settings > System > About, tekan OK pada "Android TV OS build" 7 kali,'
    Say 'kemudian Settings > System > Developer options > hidupkan USB debugging.'
    Say 'Alamat IP TV ada di Settings > Network & Internet > rangkaian Wi-Fi anda.'
    while ($true) {
        $prompt = if ($script:C.TvHost) { "Alamat IP TV [$($script:C.TvHost)]" } else { 'Alamat IP TV' }
        $ip = Read-Host $prompt
        if (-not $ip) { $ip = $script:C.TvHost }
        if ($ip -match '^\d{1,3}(\.\d{1,3}){3}$') { break }
        Warn 'Tulis empat nombor seperti 192.168.1.50'
    }
    $env:TV_HOST = $ip
    $script:C.TvHost = $ip
    $tries = 0
    while ($true) {
        Say "Menyambung ke $ip... Jika TV bertanya 'Allow USB debugging?', tandakan Always allow dan tekan OK."
        $out = @(T 'getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.sdk; getprop ro.product.manufacturer' 2>$null)
        if ($LASTEXITCODE -eq 0 -and $out.Count -ge 4) { break }
        $tries++
        if ($tries -ge 3) {
            Say ''
            Warn 'Masih tidak dapat menyambung. Semak:'
            Say '  - TV dan komputer ini pada Wi-Fi yang sama, dan IP betul'
            Say '  - USB debugging hidup'
            Say '  - Windows Firewall atau antivirus tidak menyekat Python'
            Say '  - Sesetengah Google TV perlu "Wireless debugging" dan kod pairing: lihat Masalah lazim dalam panduan'
            Fail 'Tiada apa-apa diubah pada TV.'
        }
        Read-Host 'Tidak berjaya. Tekan Enter untuk cuba lagi' | Out-Null
    }
    $script:Model = "$($out[0])".Trim(); $script:Device = "$($out[1])".Trim()
    $script:Sdk = "$($out[2])".Trim(); $script:Maker = "$($out[3])".Trim()
    Ok "Disambung: $script:Maker $script:Model ($script:Device), Android API $script:Sdk"
    Save-Conf
}

function Stock-Launcher {
    if ((T 'pm list packages com.google.android.apps.tv.launcherx') -match 'launcherx') { return 'com.google.android.apps.tv.launcherx' }
    return 'com.google.android.tvlauncher'
}

# ------------------------------------------------------------------------------------------------ actions

function Install-GlassTv {
    if ([int]$script:Sdk -lt 26) { Fail "TV ini Android API $script:Sdk. Skrin utama Glass TV perlukan Android 8 (API 26) ke atas. Tiada apa-apa diubah." }
    if ($script:Maker -eq 'Amazon') { Fail 'Ini Fire TV: Fire OS sentiasa mengambil semula butang Home. Tiada apa-apa diubah.' }

    Step 'Langkah 2: sandaran'
    $bdir = Join-Path $Backups ("$script:Device-" + (Get-Date -Format 'yyyyMMdd-HHmm'))
    New-Item -ItemType Directory -Force -Path $bdir | Out-Null
    T 'pm list packages -f' | Out-File -Encoding ascii (Join-Path $bdir 'packages.txt')
    T 'pm list packages -d' | Out-File -Encoding ascii (Join-Path $bdir 'disabled.txt')
    T 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Out-File -Encoding ascii (Join-Path $bdir 'home.txt')
    if ((Get-Item (Join-Path $bdir 'packages.txt')).Length -eq 0) { Fail 'Sandaran kosong; sambungan terputus. Tiada apa-apa diubah.' }
    Ok "Sandaran di $bdir"

    Step 'Langkah 3: buang iklan'
    $list = $null
    $pattern = '[ (,]' + [regex]::Escape($script:Device) + '([ ,)]|$)'
    foreach ($f in Get-ChildItem (Join-Path $script:Dir 'examples') -Filter debloat.txt -Recurse) {
        if ((Get-Content $f.FullName -TotalCount 3) -match $pattern) { $list = $f.FullName; break }
    }
    if ($list) {
        $n = @(Get-Content $list | Where-Object { $_ -notmatch '^\s*(#|$)' }).Count
        Say "Ada senarai yang sudah diuji untuk TV ini ($n aplikasi iklan, penjejak dan demo)."
        Say 'Aplikasi hanya dimatikan, tidak dibuang. HDMI, alat kawalan jauh, Wi-Fi, carian suara dan cast dikekalkan.'
        if (YesNo 'Matikan aplikasi dalam senarai ini?') {
            & (Join-Path $script:Dir 'tools\disable.ps1') -TvHost $env:TV_HOST $list | Out-Null
            $script:C.List = $list; Save-Conf
            Ok 'Iklan dan penjejak dimatikan'
        } else { Say 'Dilangkau.' }
    } else {
        Say "Tiada senarai yang diuji untuk $script:Device; buang iklan dilangkau (Glass TV tetap tiada iklan)."
    }
    if (-not $script:C.List -and ((T 'pm list packages com.google.android.tungsten.setupwraith') -match 'setupwraith')) {
        T 'pm disable-user --user 0 com.google.android.tungsten.setupwraith' | Out-Null
        $script:C.SetupOff = '1'; Save-Conf
        Ok 'Wizard persediaan Google TV dimatikan (supaya butang Home kekal pada Glass TV)'
    }

    Step 'Langkah 4: muat turun LayarKaca'
    $apk = $env:APK
    if ($apk) {
        $apk = (Resolve-Path $apk).Path
        Ok "Menggunakan APK tempatan: $apk"
    } else {
        try { $rel = Invoke-RestMethod -UseBasicParsing "https://api.github.com/repos/$Repo/releases/latest" -ErrorAction Stop }
        catch { Fail 'Tidak dapat mencari keluaran terkini. Semak internet dan cuba lagi.' }
        $a = $rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
        $s = $rel.assets | Where-Object { $_.name -like '*.apk.sha256' } | Select-Object -First 1
        if (-not $a -or -not $s) { Fail 'Keluaran terkini tiada APK. Cuba lagi nanti.' }
        $apk = Join-Path $script:Dir $a.name
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $a.browser_download_url -OutFile $apk -ErrorAction Stop
            Invoke-WebRequest -UseBasicParsing -Uri $s.browser_download_url -OutFile "$apk.sha256" -ErrorAction Stop
        } catch { Fail 'Muat turun gagal. Cuba lagi.' }
        $want = ((Get-Content "$apk.sha256" -Raw).Trim() -split '\s+')[0].ToLower()
        $got = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLower()
        if ($want -ne $got) { Remove-Item $apk -Force; Fail 'Fail APK tidak sepadan dengan SHA-256 rasmi. Tidak dipasang.' }
        Ok 'APK disemak (SHA-256 sepadan)'
    }

    Step 'Langkah 5: pasang'
    Warn 'JANGAN SENTUH alat kawalan jauh sehingga selesai (kira-kira 30 saat).'
    try {
        & (Join-Path $script:Dir 'launcher\build-and-install.ps1') -TvHost $env:TV_HOST -Apk $apk -InputKeyService | Out-Host
    } catch {
        Say "$_"
        if ("$_" -match 'UPDATE_INCOMPATIBLE') { Say "Glass TV versi lain sudah ada di TV ini. Pilih 'Batalkan semua' dalam menu, kemudian pasang semula." }
        Fail 'Pemasangan tidak berjaya. Skrin utama asal TV sudah dipulihkan.'
    }
    Set-Location $script:Dir
    Ok 'Glass TV dipasang dan menjadi skrin utama'

    if (-not ((T "[ -f /sdcard/Android/data/$Pkg/files/config.json ] && echo ADA") -match 'ADA')) {
        $theme = Join-Path $env:TEMP 'layarkaca-tema'
        New-Item -ItemType Directory -Force -Path $theme | Out-Null
        $cfg = Get-Content (Join-Path $script:Dir 'themes\malaysia\config.json') -Raw | ConvertFrom-Json
        foreach ($k in @('prayer_zone', 'weather_location', 'weather_place', 'haze_station')) { $cfg.PSObject.Properties.Remove($k) }
        $cfg | ConvertTo-Json | Out-File -Encoding ascii (Join-Path $theme 'config.json')
        & (Join-Path $script:Dir 'tools\theme.ps1') -TvHost $env:TV_HOST $theme | Out-Null
        Set-Location $script:Dir
        Ok 'Tetapan asal Malaysia dipasang'
    }

    T "appops set $Pkg SYSTEM_ALERT_WINDOW allow" | Out-Null
    Ok 'Panel waktu solat dibenarkan'

    Step 'Langkah 6: pilih lokasi di TV'
    T "am start -n $Pkg/.PlaceActivity" | Out-Null
    Say 'Lihat TV: pilih negeri, kawasan dan stesen udara dengan alat kawalan jauh, tekan OK.'
    Say "Untuk menukar lokasi nanti: tekan Bawah di skrin utama dan buka jubin 'Lokasi'."
    Write-Host ''
    Write-Host 'Siap! Tekan Home pada alat kawalan jauh.' -ForegroundColor Green
}

function Undo-GlassTv {
    Step 'Batalkan semua'
    Say 'Ini menghidupkan semula aplikasi yang dimatikan, mengembalikan skrin utama asal dan membuang Glass TV.'
    if (-not (YesNo 'Teruskan?')) { Say 'Tiada apa-apa diubah.'; return }
    $stock = Stock-Launcher
    if ($script:C.List -and (Test-Path $script:C.List)) {
        & (Join-Path $script:Dir 'tools\restore.ps1') -TvHost $env:TV_HOST $script:C.List $stock | Out-Null
        Ok 'Aplikasi yang dimatikan dihidupkan semula'
    }
    T "pm enable $stock" | Out-Null
    if ($script:C.SetupOff) { T 'pm enable com.google.android.tungsten.setupwraith' | Out-Null }
    T "pm uninstall $Pkg" | Out-Null
    Ok 'Glass TV dibuang'
    T 'input keyevent KEYCODE_HOME' | Out-Null
    $script:C.List = ''; $script:C.SetupOff = ''; Save-Conf
    Ok 'TV kembali seperti asal. Anda boleh matikan USB debugging di Developer options.'
}

# ------------------------------------------------------------------------------------------------ main

try {
    Write-Host 'LayarKaca: skrin utama tanpa iklan, dengan waktu solat, cuaca dan IPU.' -ForegroundColor White
    $script:C = Load-Conf
    $script:Dir = Find-Project
    $script:Py = Setup-Python $script:Dir
    Connect-Tv
    while ($true) {
        Step 'Apa yang anda mahu buat?'
        Say '  1) Pasang atau kemas kini LayarKaca'
        Say '  2) Tukar lokasi'
        Say '  3) Uji panel waktu solat'
        Say '  4) Batalkan semua (kembalikan TV seperti asal)'
        Say '  5) Keluar'
        switch (Read-Host 'Pilih 1-5') {
            '1' { Install-GlassTv }
            '2' { T "am start -n $Pkg/.PlaceActivity" | Out-Null; Ok 'Pemilih lokasi dibuka di TV' }
            '3' { T "touch /sdcard/Android/data/$Pkg/files/azan-test; input keyevent KEYCODE_HOME" | Out-Null; Ok 'Lihat TV: panel waktu solat dan bunyi bip 7 saat' }
            '4' { Undo-GlassTv }
            '5' { return }
            ''  { return }
            default { Warn 'Pilih nombor 1 hingga 5.' }
        }
    }
} catch {
    if ("$_" -ne 'berhenti') { Write-Host "[X] Ralat: $_" -ForegroundColor Red }
}
