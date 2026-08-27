<#
    recto-doctor.ps1 - environment check for building Recto on Windows.

    Run this FIRST, before any scaffolding exists. It changes nothing on your
    machine; it only looks around and reports what it finds.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\scripts\recto-doctor.ps1

    It writes a summary to doctor-report.txt next to this script. Paste that
    back to me and I'll generate a build setup that matches your machine.

    NOTE: this file is deliberately pure ASCII. Windows PowerShell 5.1 reads
    BOM-less files as cp1252, where the bytes of a UTF-8 em-dash decode to a
    curly quote and silently break parsing. Do not add smart quotes, em-dashes,
    arrows or accented characters to this file.
#>

$ErrorActionPreference = "Continue"

$script:Report   = New-Object System.Collections.ArrayList
$script:Problems = New-Object System.Collections.ArrayList

function Say {
    param($Text, $Colour = "Gray")
    Write-Host $Text -ForegroundColor $Colour
    [void]$script:Report.Add($Text)
}
function Head {
    param($Text)
    Say ""
    Say ("=" * 62) "DarkGray"
    Say "  $Text" "Cyan"
    Say ("=" * 62) "DarkGray"
}
function Ok   { param($Text) Say "  [ OK ] $Text" "Green" }
function Warn { param($Text) Say "  [ ?? ] $Text" "Yellow" }
function Bad  { param($Text) Say "  [ !! ] $Text" "Red"; [void]$script:Problems.Add($Text) }
function Note { param($Text) Say "         $Text" "DarkGray" }

# Work out where the repo root is, whether this script sits in scripts\ or at
# the repo root itself.
$here = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$root = $here
if (-not (Test-Path (Join-Path $root ".git"))) {
    $parent = Split-Path $root -Parent
    if ($parent -and (Test-Path (Join-Path $parent ".git"))) { $root = $parent }
}

Head "RECTO - BUILD ENVIRONMENT DOCTOR"
Say "  Run at        : $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
Say "  Script folder : $here"
Say "  Repo root     : $root"

# ---------------------------------------------------------------- 1. System
Head "1. System"
Say "  OS            : $([System.Environment]::OSVersion.VersionString)"
Say "  Architecture  : $env:PROCESSOR_ARCHITECTURE"
Say "  PowerShell    : $($PSVersionTable.PSVersion)  (edition: $($PSVersionTable.PSEdition))"
Say "  User          : $env:USERNAME"

if ($PSVersionTable.PSVersion.Major -lt 5) {
    Bad "PowerShell $($PSVersionTable.PSVersion) is very old. Please upgrade."
}

try {
    $driveLetter = (Split-Path $root -Qualifier).TrimEnd(":")
    $drive  = Get-PSDrive -Name $driveLetter -ErrorAction Stop
    $freeGb = [math]::Round($drive.Free / 1GB, 1)
    Say "  Free disk     : $freeGb GB on $($drive.Name):"
    if ($freeGb -lt 15) {
        Bad "Under 15 GB free. Gradle, the SDK and build caches need room."
    } else {
        Ok "Enough disk space."
    }
} catch {
    Warn "Could not read disk space."
}

# ------------------------------------------------------------------ 2. Java
Head "2. Java (JDK)"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
    $raw = (& java -version 2>&1 | Out-String).Trim()
    Say "  java on PATH  : $($javaCmd.Source)"
    foreach ($line in ($raw -split "`n")) { Note $line.Trim() }

    if ($raw -match '"(\d+)') {
        $major = [int]$Matches[1]
        Say "  Major version : $major"
        if ($major -eq 17) {
            Ok "JDK 17 - exactly what AGP 9.3 wants."
        } elseif ($major -eq 21) {
            Ok "JDK 21 - supported, fine."
        } elseif ($major -lt 17) {
            Bad "JDK $major is too old. AGP 9.x needs JDK 17 minimum."
        } else {
            Warn "JDK $major is newer than the tested baseline."
            Note "Your previous project hit a Kotlin JVM-target fallback on JDK 25"
            Note "and had to pin org.gradle.java.home to a JDK 17. If the build"
            Note "misbehaves, install Temurin 17 and we'll pin it properly."
        }
    } else {
        Warn "Could not parse the Java version string."
    }
} else {
    Bad "No 'java' on PATH."
    Note "Install Eclipse Temurin JDK 17:"
    Note "https://adoptium.net/temurin/releases/?version=17"
}

if ($env:JAVA_HOME) {
    Say "  JAVA_HOME     : $env:JAVA_HOME"
    if (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe")) {
        Ok "JAVA_HOME looks valid."
    } else {
        Bad "JAVA_HOME is set but bin\java.exe is not there."
    }
} else {
    Warn "JAVA_HOME is not set. Gradle usually copes if java is on PATH."
}

Say ""
Say "  Other JDKs on this machine:"
$jdkRoots = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft\jdk",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\Zulu",
    "C:\Program Files\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
)
$foundJdk = $false
foreach ($jdkRoot in $jdkRoots) {
    if (Test-Path $jdkRoot) {
        if (Test-Path (Join-Path $jdkRoot "bin\java.exe")) {
            Note $jdkRoot
            $foundJdk = $true
        }
        Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if (Test-Path (Join-Path $_.FullName "bin\java.exe")) {
                Note $_.FullName
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
    "C:\Program Files\Android\Android Studio1\bin\studio64.exe",
    "D:\Program Files\Android\Android Studio\bin\studio64.exe"
)
$studio = $studioPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($studio) {
    Ok "Android Studio found: $studio"
    $studioHome = Split-Path (Split-Path $studio) -Parent
    $jbr = Join-Path $studioHome "jbr\bin\java.exe"
    if (Test-Path $jbr) {
        Note "Bundled JBR (a usable JDK): $jbr"
    }
    Say "  => Setup path (b) is available: scaffold the skeleton in Studio."
} else {
    Warn "Android Studio not found in the usual locations."
    Note "Not required to build. It only decides which setup path we take."
    Note "If it is installed somewhere unusual, tell me the path."
    Say "  => Setup path (a): I generate everything, the script bootstraps it."
}

# ------------------------------------------------------------- 4. Android SDK
Head "4. Android SDK"

$sdkCandidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    "$env:LOCALAPPDATA\Android\Sdk",
    "C:\Android\Sdk",
    "D:\Android\Sdk"
) | Where-Object { $_ -and (Test-Path $_) }

$sdk = $sdkCandidates | Select-Object -First 1

if ($sdk) {
    Ok "SDK found: $sdk"
    if (-not $env:ANDROID_HOME) {
        Warn "ANDROID_HOME is not set (found the SDK by guessing)."
        Note "Set it to: $sdk"
    }

    $platDir = Join-Path $sdk "platforms"
    if (Test-Path $platDir) {
        $plats = @(Get-ChildItem $platDir -Directory -ErrorAction SilentlyContinue |
                   Select-Object -ExpandProperty Name)
        if ($plats.Count -gt 0) {
            Say "  Platforms     : $($plats -join ', ')"
        } else {
            Say "  Platforms     : none"
        }
        if ($plats -contains "android-36") {
            Ok "android-36 present (our compileSdk)."
        } else {
            Bad "android-36 missing. Gradle can auto-install it once licenses are accepted."
        }
    } else {
        Bad "No platforms directory inside the SDK."
    }

    $btDir = Join-Path $sdk "build-tools"
    if (Test-Path $btDir) {
        $bts = @(Get-ChildItem $btDir -Directory -ErrorAction SilentlyContinue |
                 Select-Object -ExpandProperty Name)
        if ($bts.Count -gt 0) {
            Say "  Build-tools   : $($bts -join ', ')"
        } else {
            Say "  Build-tools   : none"
        }
        if (@($bts | Where-Object { $_ -like "36.*" }).Count -gt 0) {
            Ok "Build-tools 36.x present (AGP 9.3 minimum)."
        } else {
            Bad "Build-tools 36.0.0+ missing. AGP 9.3.0 requires it."
        }
    } else {
        Bad "No build-tools directory inside the SDK."
    }

    $adb = Join-Path $sdk "platform-tools\adb.exe"
    if (Test-Path $adb) {
        Ok "adb found: $adb"
        try {
            $devLines = @(& $adb devices 2>&1 |
                          Select-Object -Skip 1 |
                          Where-Object { $_ -match "\S" })
            if ($devLines.Count -gt 0) {
                Ok "Device(s) connected:"
                foreach ($d in $devLines) { Note $d.Trim() }
                Note "=> the build script can install straight to your phone."
            } else {
                Warn "No device connected right now."
                Note "For one-command installs: enable Developer Options,"
                Note "turn on USB debugging, plug in a DATA cable, tap Allow."
                Note "Without it we just copy the APK over manually. Both work."
            }
        } catch {
            Warn "Could not run 'adb devices'."
        }
    } else {
        Bad "platform-tools\adb.exe missing. Install 'Android SDK Platform-Tools'."
    }

    if (Test-Path (Join-Path $sdk "licenses")) {
        Ok "SDK licenses directory present."
    } else {
        Bad "No licenses directory. Gradle will refuse to auto-install SDK parts."
        Note "Fix with: sdkmanager --licenses"
    }

} else {
    Bad "No Android SDK found."
    Note "Either install Android Studio (bundles it), or command-line tools:"
    Note "https://developer.android.com/studio#command-line-tools-only"
    Note "Then run:"
    Note "sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'"
}

# ------------------------------------------------------------------ 5. Project
Head "5. Project state"

$checks = [ordered]@{
    "settings.gradle.kts"                      = "Gradle settings"
    "gradlew.bat"                              = "Gradle wrapper (Windows)"
    "gradle\wrapper\gradle-wrapper.jar"        = "Wrapper JAR"
    "gradle\wrapper\gradle-wrapper.properties" = "Wrapper properties"
    "gradle\libs.versions.toml"                = "Version catalog"
    "local.properties"                         = "local.properties (SDK path)"
}
foreach ($key in $checks.Keys) {
    if (Test-Path (Join-Path $root $key)) {
        Ok "$($checks[$key]) present"
    } else {
        Note "absent: $key  ($($checks[$key]))"
    }
}
Note "All absent is EXPECTED. Phase 0 has not been scaffolded yet."

# ------------------------------------------------------------------- 6. Git
Head "6. Git"
if (Get-Command git -ErrorAction SilentlyContinue) {
    $gitVer = (& git --version) -replace 'git version ', ''
    Ok "git $gitVer"
    Push-Location $root
    try {
        $branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
        if ($branch) { Say "  Branch        : $branch" }
    } catch {
        Warn "Could not read the git branch."
    }
    Pop-Location
} else {
    Warn "git is not on PATH (not needed to build)."
}

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
    Say "  RECOMMENDED: path (b) - scaffold in Studio, then use the scripts." "Cyan"
} else {
    Say "  RECOMMENDED: path (a) - I generate it all, you run recto-build.ps1." "Cyan"
}

# ------------------------------------------------------------- write report
$outFile = Join-Path $here "doctor-report.txt"
try {
    ($script:Report -join "`r`n") | Set-Content -Path $outFile -Encoding UTF8
    $saved = $true
} catch {
    $saved = $false
}

Say ""
Say ("=" * 62) "DarkGray"
if ($saved) {
    Say "  Report saved to: $outFile" "Magenta"
    Say "  Paste its contents back to me and I'll tailor the setup." "Magenta"
} else {
    Say "  Could not write the report file. Copy the text above instead." "Yellow"
}
Say ("=" * 62) "DarkGray"
Say ""