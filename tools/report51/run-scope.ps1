$ErrorActionPreference = "Stop"

$python = if ($env:REPORT51_PYTHON) {
    $env:REPORT51_PYTHON
}
else {
    "py"
}

& $python (Join-Path $PSScriptRoot "sync_scope.py")
exit $LASTEXITCODE
