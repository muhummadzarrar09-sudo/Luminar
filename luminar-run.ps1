# luminar-build.ps1
# Builds the APK and opens the folder so you can grab it.

param(
    [string]$ProjectPath = $PSScriptRoot
)

function Write-Step { param($msg) Write-Host "`n[ >> ] $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg) Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Write-Fail { param($msg) Write-Host "[ !! ] $msg" -ForegroundColor Red; exit 1 }
function Write-Warn { param($msg) Write-Host "[ ~~ ] $msg" -ForegroundColor Yellow }

Set-Location $ProjectPath

if (-not (Test-Path "settings.gradle.kts")) {
    Write-Fail "Not an Android project root. Run from the Luminar folder."
}

# ---- 1. Gradle wrapper bootstrap -------------------------------------------

Write-Step "Checking Gradle wrapper..."

if (-not (Test-Path "gradlew.bat")) {
    Write-Warn "gradlew.bat not found. Bootstrapping..."

    New-Item -ItemType Directory -Force -Path "gradle\wrapper" | Out-Null

    $props = "distributionBase=GRADLE_USER_HOME`r`n" +
             "distributionPath=wrapper/dists`r`n" +
             "distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip`r`n" +
             "networkTimeout=10000`r`n" +
             "validateDistributionUrl=true`r`n" +
             "zipStoreBase=GRADLE_USER_HOME`r`n" +
             "zipStorePath=wrapper/dists"
    $props | Set-Content "gradle\wrapper\gradle-wrapper.properties" -Encoding UTF8

    Write-Warn "Downloading gradle-wrapper.jar..."
    $jarDest = "gradle\wrapper\gradle-wrapper.jar"
    $jarUrl  = "https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradle/wrapper/gradle-wrapper.jar"

    try {
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarDest -UseBasicParsing -ErrorAction Stop
        Write-Ok "Downloaded gradle-wrapper.jar."
    } catch {
        Write-Warn "GitHub failed. Downloading full Gradle distribution..."
        $zipUrl  = "https://services.gradle.org/distributions/gradle-9.5.1-bin.zip"
        $zipDest = "$env:TEMP\gradle-9.5.1-bin.zip"
        $exDest  = "$env:TEMP\gradle-951"

        Invoke-WebRequest -Uri $zipUrl -OutFile $zipDest -UseBasicParsing
        Expand-Archive -Path $zipDest -DestinationPath $exDest -Force

        $found = Get-ChildItem -Path $exDest -Recurse -Filter "gradle-wrapper.jar" | Select-Object -First 1
        if (-not $found) { Write-Fail "Could not find gradle-wrapper.jar in distribution." }

        Copy-Item $found.FullName $jarDest
        Remove-Item $zipDest -Force -ErrorAction SilentlyContinue
        Remove-Item $exDest  -Recurse -Force -ErrorAction SilentlyContinue
        Write-Ok "Extracted gradle-wrapper.jar."
    }

    $bat = '@echo off' + "`r`n" +
           'setlocal' + "`r`n" +
           'set DIRNAME=%~dp0' + "`r`n" +
           'if "%DIRNAME%"=="" set DIRNAME=.' + "`r`n" +
           'set APP_HOME=%DIRNAME%' + "`r`n" +
           'set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"' + "`r`n" +
           'if defined JAVA_HOME goto findJavaFromJavaHome' + "`r`n" +
           'set JAVA_EXE=java.exe' + "`r`n" +
           '%JAVA_EXE% -version >NUL 2>&1' + "`r`n" +
           'if %ERRORLEVEL% equ 0 goto execute' + "`r`n" +
           'echo ERROR: JAVA_HOME not set and java not on PATH.' + "`r`n" +
           'goto fail' + "`r`n" +
           ':findJavaFromJavaHome' + "`r`n" +
           'set JAVA_HOME=%JAVA_HOME:"=%' + "`r`n" +
           'set JAVA_EXE=%JAVA_HOME%/bin/java.exe' + "`r`n" +
           'if exist "%JAVA_EXE%" goto execute' + "`r`n" +
           'echo ERROR: JAVA_HOME set to invalid directory: %JAVA_HOME%' + "`r`n" +
           'goto fail' + "`r`n" +
           ':execute' + "`r`n" +
           'set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar' + "`r`n" +
           '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*' + "`r`n" +
           'goto end' + "`r`n" +
           ':fail' + "`r`n" +
           'exit /b 1' + "`r`n" +
           ':end' + "`r`n" +
           'endlocal'

    $bat | Set-Content "gradlew.bat" -Encoding ASCII
    Write-Ok "Wrapper ready."
} else {
    Write-Ok "gradlew.bat found."
}

# ---- 2. Build --------------------------------------------------------------

Write-Step "Building APK (first run downloads Gradle + deps, a few mins)..."

.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Fail "Build failed. Check output above." }

# ---- 3. Find APK -----------------------------------------------------------

Write-Step "Locating APK..."

$apk = Get-ChildItem -Path "app\build\outputs\apk\debug\" -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) { Write-Fail "APK not found. Build may have silently failed." }

Write-Ok "APK ready: $($apk.FullName)"

# ---- 4. Open folder --------------------------------------------------------

Write-Step "Opening APK folder..."

Start-Process explorer.exe -ArgumentList "/select,`"$($apk.FullName)`""

Write-Host "`n[ DONE ] APK folder is open. Send the file to your phone via WhatsApp / Drive / cable and install it.`n" -ForegroundColor Magenta
Write-Host "         NOTE: On your Infinix, go to Settings -> Safety -> Unknown Sources -> ON" -ForegroundColor Yellow
Write-Host "         (lets you install APKs outside the Play Store)`n" -ForegroundColor Yellow