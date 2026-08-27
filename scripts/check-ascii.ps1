<#
    check-ascii.ps1 - guard against the encoding bug that broke recto-doctor.ps1.

    Windows PowerShell 5.1 reads BOM-less files as cp1252. A UTF-8 em-dash is
    the bytes E2 80 94, and cp1252 decodes 0x94 as a right curly double-quote.
    PowerShell then treats it as a string delimiter, so a single em-dash inside
    a comment silently opens a phantom string and the whole file fails to parse
    with misleading errors pointing at unrelated lines.

    This script flags any non-ASCII byte in the .ps1 files, and also asks
    PowerShell itself to parse each one.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\scripts\check-ascii.ps1
#>

$here  = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$files = Get-ChildItem -Path $here -Filter "*.ps1" -File
$bad   = 0

Write-Host ""
Write-Host "Checking $($files.Count) PowerShell file(s) in $here" -ForegroundColor Cyan
Write-Host ""

foreach ($f in $files) {
    $bytes  = [System.IO.File]::ReadAllBytes($f.FullName)
    $offend = @()

    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -gt 127) { $offend += $i }
    }

    if ($offend.Count -gt 0) {
        $bad++
        Write-Host "  [ !! ] $($f.Name): $($offend.Count) non-ASCII byte(s)" -ForegroundColor Red

        # Report the line numbers, not just byte offsets.
        $text  = [System.IO.File]::ReadAllText($f.FullName)
        $lines = $text -split "`n"
        for ($n = 0; $n -lt $lines.Length; $n++) {
            $chars = $lines[$n].ToCharArray() | Where-Object { [int]$_ -gt 127 }
            if ($chars.Count -gt 0) {
                $shown = ($chars | ForEach-Object { "U+{0:X4}" -f [int]$_ }) -join " "
                Write-Host "         line $($n + 1): $shown" -ForegroundColor Yellow
                Write-Host "           $($lines[$n].Trim())" -ForegroundColor DarkGray
            }
        }
    } else {
        Write-Host "  [ OK ] $($f.Name): pure ASCII" -ForegroundColor Green
    }

    # Ask PowerShell to actually parse it.
    $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile(
        $f.FullName, [ref]$null, [ref]$errors)

    if ($errors -and $errors.Count -gt 0) {
        $bad++
        Write-Host "  [ !! ] $($f.Name): $($errors.Count) parse error(s)" -ForegroundColor Red
        foreach ($e in ($errors | Select-Object -First 5)) {
            Write-Host "         line $($e.Extent.StartLineNumber): $($e.Message)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "         parses cleanly" -ForegroundColor DarkGray
    }
}

Write-Host ""
if ($bad -eq 0) {
    Write-Host "All good. Every script is ASCII-only and parses." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$bad problem(s) found. Fix before committing." -ForegroundColor Red
    exit 1
}
