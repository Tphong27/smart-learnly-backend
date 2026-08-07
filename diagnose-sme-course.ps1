# =============================================================================
# Diagnose: SME opens course but sees no sections
# Usage (run from this repo):
#   1) Token option:  .\diagnose-sme-course.ps1 -Token "PASTE_ACCESS_TOKEN"
#   2) Login option:  .\diagnose-sme-course.ps1 -Email "admin@..."  (prompts for password)
# Optionally target one course: -CourseId "uuid"
# =============================================================================
param(
    [string]$Token,
    [string]$Email,
    [string]$CourseId
)

$base = 'http://localhost:8080/api/v1'

function Get-AuthToken {
    if ($Token) { return $Token }
    $mail = $Email
    if (-not $mail) { $mail = Read-Host 'Admin email' }
    $sec = Read-Host 'Admin password' -AsSecureString
    $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    $body = @{ email = $mail; password = $plain } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$base/auth/login" -Method Post `
        -Body $body -ContentType 'application/json'
    if (-not $resp.data.accessToken) {
        throw 'Login response has no accessToken. Check email/password.'
    }
    return $resp.data.accessToken
}

function Get-Api($path, $headers) {
    try {
        $resp = Invoke-RestMethod -Uri "$base$path" -Headers $headers
        return @{ ok = $true; data = $resp.data }
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        return @{ ok = $false; status = $status; err = $_.Exception.Message }
    }
}

function Show-Sections($label, $payload) {
    Write-Host "`n--- $label ---"
    if (-not $payload.ok) {
        Write-Host "  FAILED (HTTP $($payload.status)): $($payload.err)"
        return
    }
    $d = $payload.data
    if ($null -eq $d) { Write-Host "  (no data)"; return }
    # learning-preview returns { sections: [...] } or { data: [...] }; modules returns [...]
    $sections = $null
    if ($d -is [array]) { $sections = $d }
    elseif ($d.sections -is [array]) { $sections = $d.sections }
    elseif ($d.data -is [array]) { $sections = $d.data }
    if ($null -eq $sections -or $sections.Count -eq 0) {
        Write-Host "  EMPTY (0 sections)"
        return
    }
    Write-Host "  $($sections.Count) section(s):"
    foreach ($s in $sections) {
        $lessonCount = 0
        if ($s.lessons -is [array]) { $lessonCount = $s.lessons.Count }
        Write-Host ("   - " + $s.title + "  [lessons: " + $lessonCount + "]")
    }
}

$headers = @{ Authorization = "Bearer $(Get-AuthToken)" }
Write-Host "Logged in OK."

$list = Get-Api '/admin/courses' $headers
if (-not $list.ok) { Write-Host "List courses FAILED (HTTP $($list.status)): $($list.err)"; exit 1 }
$courses = $list.data
if ($courses.content -is [array]) { $courses = $courses.content }

Write-Host "`n=== COURSES ==="
$targetIds = @()
foreach ($c in $courses) {
    $assigned = $c.assignedSmeId
    if (-not $assigned -and $c.assignedSme) { $assigned = $c.assignedSme.id }
    Write-Host ("  {0}  status={1}  assignedSmeId={2}  title={3}" -f $c.id, $c.status, $assigned, $c.title)
    if (-not $CourseId -or $CourseId -eq $c.id) { $targetIds += $c.id }
}

if ($targetIds.Count -eq 0) {
    if ($CourseId) { Write-Host "Course $CourseId not found in list."; exit 1 }
    Write-Host 'No courses found.'
    exit 0
}

if ($CourseId) { $targetIds = @($CourseId) }
foreach ($id in $targetIds) {
    Write-Host "`n=============================================="
    Write-Host "COURSE: $id"
    $modules = Get-Api "/admin/courses/$id/modules" $headers
    Show-Sections 'Content page (GET /admin/courses/{id}/modules)' $modules
    $prev = Get-Api "/admin/courses/$id/learning-preview" $headers
    Show-Sections 'Admin preview (GET /admin/courses/{id}/learning-preview)' $prev
    if ($prev.ok -and $prev.data.source) {
        Write-Host "  preview source = $($prev.data.source)"
    }
}
Write-Host '`nDONE.'
