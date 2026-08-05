$ErrorActionPreference = "Stop"

$projectJavaHome = $env:JAVA_HOME
if (
    [string]::IsNullOrWhiteSpace($projectJavaHome) -or
    -not (Test-Path -LiteralPath (Join-Path $projectJavaHome "bin\javac.exe") -PathType Leaf)
) {
    $projectJavaHome = Join-Path $env:USERPROFILE ".jdks\openjdk-24.0.1"
}

if (-not (Test-Path -LiteralPath (Join-Path $projectJavaHome "bin\javac.exe") -PathType Leaf)) {
    throw "Set JAVA_HOME to a JDK 17+ installation before running coverage."
}

$env:JAVA_HOME = $projectJavaHome
$env:Path = "$(Join-Path $projectJavaHome 'bin');$env:Path"

$driveLetter = @("S", "R", "Q", "P") |
    Where-Object { -not (Get-PSDrive -Name $_ -ErrorAction SilentlyContinue) } |
    Select-Object -First 1

if (-not $driveLetter) {
    throw "No temporary drive letter is available for the JaCoCo coverage run."
}

$coverageDrive = "${driveLetter}:"
$coverageRoot = "${coverageDrive}\"
$driveCreated = $false
$exitCode = 1
$previousLocation = Get-Location

try {
    & subst.exe $coverageDrive $PSScriptRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create temporary coverage drive $coverageDrive."
    }
    $driveCreated = $true

    Set-Location -LiteralPath $coverageRoot
    & .\mvnw.cmd clean verify
    $exitCode = $LASTEXITCODE
} finally {
    Set-Location -LiteralPath $previousLocation
    if ($driveCreated) {
        & subst.exe $coverageDrive /d
    }
}

exit $exitCode
