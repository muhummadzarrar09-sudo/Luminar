<#
    recto-doctor.ps1 — environment check for building Recto on Windows.

    Run this FIRST, before any scaffolding exists. It changes nothing on your
    machine; it only looks around and reports what it finds.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\scripts\recto-doctor.ps1

    It writes a summary to scripts\doctor-report.txt — paste that back to me
    and I'll generate a build setup that matches your machine exactly.
#>

$ErrorActionPreference = "Continue"

$script:Report = New-Object System.Collections.ArrayList
$script:Problems = New-Object System.Collections.ArrayList

function Say {
    param($Text, $Colour = "Gray")
    Write-Host $Text -ForegroundColor $Colour
    [void]$script:Report.Add($Text)
}
function Head { param($Text) Say "" ; Say ("=" * 62) "DarkGray" ; Say "  $Text" "Cyan" ; Say ("=" * 62) "DarkGray" }
function Ok   { param($Text) Say "  [ OK ] $Text" "Green" }
function Warn { param($Text) Say "  [ ?? ] $Text" "Yellow" }
function Bad  { param($Text) Say "  [ !! ] $Text" "Red" ; [void]$script:Problems.Add($Text) }
function Note { param($Text) Say "         $Text" "DarkGray" }

Head "RECTO — BUILD ENVIRONMENT DOCTOR"
Say "  Run at: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"

# ---------------------------------------------------------------- 1. System
Head "1. System"
Say "  OS            : $([System.Environment]::OSVersion.VersionString)"
Say "  Architecture  : $env:PROCESSOR_ARCHITECTURE"
Say "  PowerShell    : $($PSVersionTable.PSVersion)"
Say "  User          : $env:USERNAME"

try {
    $drive = Get-PSDrive -Name ($PWD.Path.Substring(0,1)) -ErrorAction Stop
    $freeGb = [math]::Round($drive.Free / 1GB, 1)
    Say "  Free disk     : $freeGb GB on $($drive.Name):"
    if ($freeGb -lt 15) { Bad "Less than 15 GB free. Gradle + SDK + build cache need room." }
    else { Ok "Enough disk space." }
} catch { Warn "Could not read disk space." }

# ------------------------------------------------------------------ 2. Java
Head "2. Java (JDK)"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
    $raw = (& java -version 2>&1 | Out-String).Trim()
    Say "  java on PATH  : $($javaCmd.Source)"
    foreach ($line in $raw -split "`n") { Note $line.Trim() }

    if ($raw -match '"(\d+)') {
        $major = [int]$Matches[1]
        Say "  Detected major version: $major"
        if ($major -eq 17)     { Ok  "JDK 17 — exactly what AGP 9.3 wants." }
        elseif ($major -eq 21) { Ok  "JDK 21 — supported, fine." }
        elseif ($major -lt 17) { Bad "JDK $major is too old. AGP 9.x requires JDK 17 minimum." }
        else                   { Warn "JDK $major is newer than the tested baseline. Your previous project hit a Kotlin JVM-target fallback on JDK 25 and had to pin org.gradle.java.home to a JDK 17. If the build misbehaves, install Temurin 17 and pin it." }
    } else { Warn "Could not parse the Java version string." }
} else {
    Bad "No 'java' on PATH."
    Note "Install Eclipse Temurin JDK 17: https://adoptium.net/temurin/releases/?version=17"
}

if ($env:JAVA_HOME) {
    Say "  JAVA_HOME     : $env:JAVA_HOME"
    if (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe")) { Ok "JAVA_HOME looks valid." }
    else { Bad "JAVA_HOME is set but bin\java.exe is missing there." }
} else {
    Warn "JAVA_HOME is not set. Gradle usually copes if java is on PATH, but setting it avoids surprises."
}

# Look for other installed JDKs, useful if we need to pin one
Say ""
Say "  Other JDKs found on this machine:"
$jdkRoots = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft\jdk",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\Zulu",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
)
$foundJdk = $false
foreach ($root in $jdkRoots) {
    if (Test-Path $root) {
        Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if (Test-Path (Join-Path $_.FullName "bin\java.exe")) {
                Note "$($_.FullName)"
                $foundJdk = $true
            }
        }
    }
}
if (-not $foundJdk) { Note "(none found in the usual locations)" }

# --------------------------------------------------------- 3. Android Studio
Head "3. Android Studio"

$studioPaths = @(
    "C:\Program Files\Android\Android Studio\bin\studio64.exe",
    "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe",
    "C:\Program Files\Android\Android Studio1\bin\studio64.exe"
)
$studio = $studioPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($studio) {
    Ok "Android Studio found: $studio"
    $jbr = Join-Path (Split-Path (Split-Path $studio)) "jbr\bin\java.exe"
    if (Test-Path $jbr) { Note "Bundled JBR (a JDK you can point Gradle at): $jbr" }
    Say "  => PATH (b) is open to you: create the project skeleton in Studio."
} else {
    Warn "Android Studio not found in the usual locations."
    Note "That's fine — it is NOT required to build. It only matters for which"
    Note "scaffolding path we pick. If you do have it somewhere unusual, say so."
    Say "  => PATH (a) + scripts is likely the better fit for you."
}

# ------------------------------------------------------------- 4. Android SDK
Head "4. Android SDK"

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
                   "$env:LOCALAPPDATA\Android\Sdk", "C:\Android\Sdk") |
                 Where-Object { $_ -and (Test-Path $_) }
$sdk = $sdkCandidates | Select-Object -First 1

if ($sdk) {
    Ok "SDK found: $sdk"
    if (-not $env:ANDROID_HOME) { Warn "ANDROID_HOME is not set (found it by guessing). Set it to: $sdk" }

    # platforms
    $platDir = Join-Path $sdk "platforms"
    if (Test-Path $platDir) {
        $plats = Get-ChildItem $platDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
        Say "  Platforms     : $(if ($plats) { $plats -join ', ' } else { 'none' })"
        if ($plats -contains "android-36") { Ok "android-36 present (compileSdk target)." }
        else { Bad "android-36 missing. Install 'Android 16 (API 36)' — Gradle can auto-download it if you accept licenses." }
    } else { Bad "No platforms directory in the SDK." }

    # build-tools
    $btDir = Join-Path $sdk "build-tools"
    if (Test-Path $btDir) {
        $bts = Get-ChildItem $btDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
        Say "  Build-tools   : $(if ($bts) { $bts -join ', ' } else { 'none' })"
        if ($bts | Where-Object { $_ -like "36.*" }) { Ok "Build-tools 36.x present (AGP 9.3 minimum)." }
        else { Bad "Build-tools 36.0.0+ missing. AGP 9.3.0 requires it." }
    } else { Bad "No build-tools directory in the SDK." }

    # platform-tools / adb
    $adb = Join-Path $sdk "platform-tools\adb.exe"
    if (Test-Path $adb) {
        Ok "adb found: $adb"
        try {
            $devices = & $adb devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match "\S" }
            if ($devices) {
                Ok "Device(s) connected:"
                foreach ($d in $devices) { Note $d.Trim() }
                Note "=> the build script can install straight to your phone over USB."
            } else {
                Warn "No device connected right now."
                Note "For direct install: enable Developer Options -> USB debugging, plug in, tap Allow."
                Note "Without it we just copy the APK to your phone manually. Both work."
            }
        } catch { Warn "Could not run adb devices." }
    } else { Bad "platform-tools/adb.exe missing. Install 'Android SDK Platform-Tools'." }

    # licenses
    $lic = Join-Path $sdk "licenses"
    if (Test-Path $lic) { Ok "SDK licenses directory present." }
    else { Bad "No licenses directory — Gradle will refuse to auto-install SDK bits. Run: sdkmanager --licenses" }

} else {
    Bad "No Android SDK found."
    Note "Either install Android Studio (bundles it), or command-line tools only:"
    Note "  https://developer.android.com/studio#command-line-tools-only"
    Note "Then: sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'"
}

# ------------------------------------------------------------------ 5. Project
Head "5. Project state"

$root = Split-Path $PSScriptRoot -Parent
Say "  Repo root     : $root"

$checks = @{
    "settings.gradle.kts"                    = "Gradle settings"
    "gradlew.bat"                            = "Gradle wrapper (Windows)"
    "gradle\wrapper\gradle-wrapper.jar"      = "Wrapper JAR"
    "gradle\wrapper\gradle-wrapper.properties" = "Wrapper properties"
    "gradle\libs.versions.toml"              = "Version catalog"
    "local.properties"                       = "local.properties (SDK path)"
}
foreach ($k in $checks.Keys) {
    if (Test-Path (Join-Path $root $k)) { Ok "$($checks[$k]) present" }
    else { Note "absent: $k  ($($checks[$k]))" }
}
Note "All absent is EXPECTED right now — Phase 0 has not been scaffolded yet."

# ------------------------------------------------------------------- 6. Git
Head "6. Git"
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
if ($gitCmd) {
    Ok "git: $((& git --version) -replace 'git version ','')"
    Push-Location $root
    try {
        $branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
        Say "  Branch        : $branch"
    } catch { Warn "Not a git repo?" }
    Pop-Location
} else { Warn "git not on PATH (not required to build)." }

# ---------------------------------------------------------------- 7. Verdict
Head "7. VERDICT"

if ($script:Problems.Count -eq 0) {
    Say "  No blockers found. This machine can build Recto." "Green"
} else {
    Say "  $($script:Problems.Count) thing(s) need attention:" "Yellow"
    foreach ($p in $script:Problems) { Say "    - $p" "Yellow" }
}

Say ""
if ($studio) {
    Say "  RECOMMENDED PATH: (b) scaffold in Android Studio, then use the scripts." "Cyan"
} else {
    Say "  RECOMMENDED PATH: (a) I generate everything, you run recto-build.ps1." "Cyan"
}

# ------------------------------------------------------------- write report
$outFile = Join-Path $PSScriptRoot "doctor-report.txt"
$script:Report -join "`r`n" | Set-Content -Path $outFile -Encoding UTF8

Say ""
Say ("=" * 62) "DarkGray"
Say "  Report saved to: $outFile" "Magenta"
Say "  Paste its contents back to me and I'll tailor the build setup." "Magenta"
Say ("=" * 62) "DarkGray"
Say ""
