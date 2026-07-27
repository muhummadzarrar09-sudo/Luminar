<#
    recto-build.ps1 - build Recto, install it on your phone, and watch the logs.

    This is the successor to the old luminar-run.ps1, with the rough edges from
    that one fixed: it installs over USB instead of opening Explorer, it can tail
    logcat, it pins the JDK properly, and it explains failures instead of just
    printing "Build failed."

    Usage:
        .\scripts\recto-build.ps1                 build + install + open logcat
        .\scripts\recto-build.ps1 -NoInstall      build only, then reveal the APK
        .\scripts\recto-build.ps1 -Clean          wipe build dirs first
        .\scripts\recto-build.ps1 -Release        build an unsigned release APK
        .\scripts\recto-build.ps1 -NoLogcat       skip the log tail
        .\scripts\recto-build.ps1 -Offline        build without hitting the network

    First run downloads Gradle and every dependency. Expect 5-15 minutes and a
    few hundred MB. Every run after that is seconds.

    NOTE: this file is deliberately pure ASCII. Windows PowerShell 5.1 reads
    BOM-less files as cp1252, where the bytes of a UTF-8 em-dash decode to a
    curly quote and silently break parsing. Do not add smart quotes, em-dashes,
    arrows or accented characters to this file.
#>

param(
    [switch]$Clean,
    [switch]$NoInstall,
    [switch]$NoLogcat,
    [switch]$Release,
    [switch]$Offline
)

$ErrorActionPreference = "Stop"

$AppId   = "dev.recto.reader"
$Root    = Split-Path $PSScriptRoot -Parent
$Variant = if ($Release) { "release" } else { "debug" }
$Task    = if ($Release) { "assembleRelease" } else { "assembleDebug" }

function Step { param($m) Write-Host "`n[ >> ] $m" -ForegroundColor Cyan }
function Ok   { param($m) Write-Host "[ OK ] $m" -ForegroundColor Green }
function Warn { param($m) Write-Host "[ ~~ ] $m" -ForegroundColor Yellow }
function Info { param($m) Write-Host "       $m" -ForegroundColor DarkGray }
function Die  {
    param($m, $hint)
    Write-Host "`n[ !! ] $m" -ForegroundColor Red
    if ($hint) { Write-Host "       $hint" -ForegroundColor Yellow }
    exit 1
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host ""
Write-Host "  ____           _        " -ForegroundColor DarkCyan
Write-Host " |  _ \ ___  ___| |_ ___  " -ForegroundColor DarkCyan
Write-Host " | |_) / _ \/ __| __/ _ \ " -ForegroundColor DarkCyan
Write-Host " |  _ <  __/ (__| || (_) |" -ForegroundColor DarkCyan
Write-Host " |_| \_\___|\___|\__\___/ " -ForegroundColor DarkCyan
Write-Host "   the page you're on     " -ForegroundColor DarkGray
Write-Host ""

Set-Location $Root

# ---------------------------------------------------------- 0. sanity checks
if (-not (Test-Path "settings.gradle.kts")) {
    Die "No settings.gradle.kts here - the project has not been scaffolded yet." `
        "Phase 0 has not landed. Run scripts\recto-doctor.ps1 first and send me the report."
}

if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    Die "No Java found." "Install Eclipse Temurin JDK 17: https://adoptium.net/temurin/releases/?version=17"
}

# ------------------------------------------------- 1. local.properties / SDK
Step "Checking Android SDK..."

if (-not (Test-Path "local.properties")) {
    $sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
             "$env:LOCALAPPDATA\Android\Sdk", "C:\Android\Sdk") |
           Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

    if ($sdk) {
        $escaped = $sdk -replace '\\', '\\\\' -replace ':', '\:'
        "sdk.dir=$escaped" | Set-Content "local.properties" -Encoding ASCII
        Ok "Wrote local.properties -> $sdk"
    } else {
        Die "Android SDK not found." `
            "Install Android Studio, or the command-line tools, then set ANDROID_HOME."
    }
} else {
    Ok "local.properties present."
}

# ------------------------------------------------------- 2. Gradle wrapper
Step "Checking Gradle wrapper..."

$jarPath = "gradle\wrapper\gradle-wrapper.jar"

# A valid JAR is a ZIP: it must start with the bytes 'PK'. A truncated or
# HTML-error-page download would otherwise fail later with a baffling
# "invalid entry CRC" or "could not find main class" error.
$jarLooksValid = $false
if (Test-Path $jarPath) {
    try {
        $fs = [System.IO.File]::OpenRead((Resolve-Path $jarPath))
        $sig = New-Object byte[] 2
        $null = $fs.Read($sig, 0, 2)
        $fs.Close()
        if ($sig[0] -eq 0x50 -and $sig[1] -eq 0x4B -and (Get-Item $jarPath).Length -gt 10000) {
            $jarLooksValid = $true
        }
    } catch {
        $jarLooksValid = $false
    }
    if (-not $jarLooksValid) {
        Warn "Existing wrapper JAR looks corrupt. Re-downloading."
        Remove-Item $jarPath -Force -ErrorAction SilentlyContinue
    }
}

if (-not $jarLooksValid) {
    Warn "Wrapper JAR missing - fetching it (one time only)."
    New-Item -ItemType Directory -Force -Path "gradle\wrapper" | Out-Null

    $gradleVer = "9.5.0"
    if (Test-Path "gradle\wrapper\gradle-wrapper.properties") {
        $m = Select-String -Path "gradle\wrapper\gradle-wrapper.properties" `
                           -Pattern "gradle-([\d.]+)-bin" -ErrorAction SilentlyContinue
        if ($m) { $gradleVer = $m.Matches[0].Groups[1].Value }
    }
    Info "Targeting Gradle $gradleVer"

    # TLS 1.2 - Windows PowerShell 5.1 does not always negotiate it by default,
    # and both of these hosts require it.
    try {
        [Net.ServicePointManager]::SecurityProtocol =
            [Net.SecurityProtocolType]::Tls12 -bor [Net.ServicePointManager]::SecurityProtocol
    } catch { }

    $got = $false
    $jarUrl = "https://raw.githubusercontent.com/gradle/gradle/v$gradleVer/gradle/wrapper/gradle-wrapper.jar"
    try {
        Info "Trying GitHub..."
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath -UseBasicParsing -TimeoutSec 60
        if ((Get-Item $jarPath).Length -gt 10000) {
            $got = $true
            Ok "Downloaded gradle-wrapper.jar."
        }
    } catch {
        Info "GitHub route failed: $($_.Exception.Message)"
    }

    if (-not $got) {
        Warn "Falling back to the full Gradle distribution (~130 MB)."
        $zip = "$env:TEMP\gradle-$gradleVer-bin.zip"
        $ex  = "$env:TEMP\gradle-$gradleVer-extract"
        try {
            Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVer-bin.zip" `
                              -OutFile $zip -UseBasicParsing -TimeoutSec 600
            Expand-Archive -Path $zip -DestinationPath $ex -Force
            $found = Get-ChildItem $ex -Recurse -Filter "gradle-wrapper.jar" -ErrorAction SilentlyContinue |
                     Select-Object -First 1
            if (-not $found) { Die "gradle-wrapper.jar not found inside the distribution." }
            Copy-Item $found.FullName $jarPath -Force
            Ok "Extracted gradle-wrapper.jar."
        } catch {
            Die "Could not obtain gradle-wrapper.jar: $($_.Exception.Message)" `
                "Check your internet connection, then re-run."
        } finally {
            Remove-Item $zip -Force -ErrorAction SilentlyContinue
            Remove-Item $ex -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
} else {
    Ok "Wrapper JAR present."
}

if (-not (Test-Path "gradlew.bat")) { Die "gradlew.bat is missing." "It should be committed with the project. Re-pull the repo." }

# ------------------------------------------------------------------ 3. clean
if ($Clean) {
    Step "Cleaning..."
    & .\gradlew.bat clean --console=plain
    Get-ChildItem -Path . -Include "build" -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch "\\.git\\" } |
        ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
    Ok "Build directories removed."
}

# ------------------------------------------------------------------ 4. build
Step "Building ($Variant)... first run pulls Gradle + dependencies, be patient."

# NB: do not name this $args - that is a PowerShell automatic variable.
$gradleArgs = @($Task, "--console=plain", "--warning-mode=summary")
if ($Offline) { $gradleArgs += "--offline" }

& .\gradlew.bat @gradleArgs
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host ""
    Write-Host "[ !! ] Build failed (exit $code)." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Common causes, in order of likelihood:" -ForegroundColor Yellow
    Write-Host "   1. 'kotlin extension already registered' / KSP complaints" -ForegroundColor Gray
    Write-Host "      -> AGP 9 has built-in Kotlin. The org.jetbrains.kotlin.android" -ForegroundColor DarkGray
    Write-Host "         plugin must NOT be applied. Needs KSP 2.3.1+ and Hilt 2.59+." -ForegroundColor DarkGray
    Write-Host "   2. 'SDK location not found' -> delete local.properties and re-run." -ForegroundColor Gray
    Write-Host "   3. 'licenses have not been accepted' -> sdkmanager --licenses" -ForegroundColor Gray
    Write-Host "   4. Wrong JDK -> AGP 9.3 wants JDK 17. Check with recto-doctor.ps1." -ForegroundColor Gray
    Write-Host "   5. Out of memory -> raise org.gradle.jvmargs in gradle.properties." -ForegroundColor Gray
    Write-Host ""
    Write-Host "  For the full story:  .\gradlew.bat $Task --stacktrace" -ForegroundColor Cyan
    Write-Host "  Then paste the output to me and I'll fix it." -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

# -------------------------------------------------------------- 5. find APK
Step "Locating APK..."

$apk = Get-ChildItem -Path "app\build\outputs\apk\$Variant" -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $apk) { Die "No APK produced, despite the build reporting success." }

$sizeMb = [math]::Round($apk.Length / 1MB, 2)
Ok "APK: $($apk.Name)  ($sizeMb MB)"
Info $apk.FullName

# ---------------------------------------------------------------- 6. install
$installed = $false

if (-not $NoInstall) {
    Step "Looking for a connected phone..."

    $sdkDir = ($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk" |
               Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1)
    $adb = if ($sdkDir) { Join-Path $sdkDir "platform-tools\adb.exe" } else { $null }
    if (-not ($adb -and (Test-Path $adb))) {
        $c = Get-Command adb -ErrorAction SilentlyContinue
        if ($c) { $adb = $c.Source } else { $adb = $null }
    }

    if ($adb) {
        $devs = & $adb devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
        if ($devs) {
            Ok "Device connected."
            Step "Installing..."
            & $adb install -r $apk.FullName
            if ($LASTEXITCODE -eq 0) {
                $installed = $true
                Ok "Installed."
                Step "Launching..."
                & $adb shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
                Ok "Launched on device."
            } else {
                Warn "adb install failed. Falling back to manual transfer."
            }
        } else {
            Warn "No device detected over USB."
            Info "Enable Developer Options -> USB debugging, plug in, tap Allow."
        }
    } else {
        Warn "adb not found."
    }
}

if (-not $installed) {
    Step "Opening the APK folder for manual transfer..."
    Start-Process explorer.exe -ArgumentList "/select,`"$($apk.FullName)`""
    Write-Host ""
    Write-Host "  Send the APK to your phone via cable, Drive, or WhatsApp, then tap it." -ForegroundColor Yellow
    Write-Host "  On Infinix/Tecno: Settings -> Safety -> Unknown Sources -> ON" -ForegroundColor Yellow
    Write-Host "  On stock Android: the installer offers the toggle when you tap the file." -ForegroundColor DarkGray
}

# ----------------------------------------------------------------- 7. done
$sw.Stop()
Write-Host ""
Write-Host ("-" * 62) -ForegroundColor DarkGray
Write-Host "  DONE in $([math]::Round($sw.Elapsed.TotalSeconds,1))s  |  $Variant  |  $sizeMb MB" -ForegroundColor Magenta
Write-Host ("-" * 62) -ForegroundColor DarkGray

# --------------------------------------------------------------- 8. logcat
if ($installed -and -not $NoLogcat) {
    Write-Host ""
    Write-Host "  Tailing logcat for $AppId. Ctrl+C to stop." -ForegroundColor Cyan
    Write-Host ""

    Start-Sleep -Seconds 1   # give the process a moment to appear
    $pidRaw = (& $adb shell pidof -s $AppId 2>$null | Out-String).Trim()

    if ($pidRaw -match '^\d+$') {
        & $adb logcat "--pid=$pidRaw"
    } else {
        Warn "Could not resolve the app PID; showing an unfiltered tail instead."
        Info "Filter manually with:  adb logcat | Select-String 'Recto'"
        & $adb logcat -v brief
    }
}
