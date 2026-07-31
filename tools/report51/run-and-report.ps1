$ErrorActionPreference = "Stop"

$testClasses = @(
    "KhiemUpdateClassRequestReportTest",
    "KhiemClassLifecycleReportTest",
    "KhiemClassStatusConverterReportTest",
    "KhiemClassAnalyticsRepositoryReportTest",
    "KhiemClassAdminUpdateReportTest",
    "KhiemClassAnalyticsServiceReportTest",
    "KhiemClassSessionScheduleReportTest",
    "KhiemClassTrainerServiceReportTest",
    "KhiemGoogleMeetServiceReportTest",
    "KhiemOpeningScheduleServiceReportTest",
    "KhiemScheduleServiceReportTest",
    "KhiemTraineeProgressReportTest"
)

$reportClasses = @(
    "com.smartlearnly.backend.classroom.dto.KhiemUpdateClassRequestReportTest",
    "com.smartlearnly.backend.classroom.entity.KhiemClassLifecycleReportTest",
    "com.smartlearnly.backend.classroom.entity.KhiemClassStatusConverterReportTest",
    "com.smartlearnly.backend.classroom.repository.KhiemClassAnalyticsRepositoryReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemClassAdminUpdateReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemClassAnalyticsServiceReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemClassSessionScheduleReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemClassTrainerServiceReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemGoogleMeetServiceReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemOpeningScheduleServiceReportTest",
    "com.smartlearnly.backend.classroom.service.KhiemScheduleServiceReportTest",
    "com.smartlearnly.backend.lessonprogress.service.KhiemTraineeProgressReportTest"
)

foreach ($reportClass in $reportClasses) {
    $reportXml = Join-Path $PSScriptRoot (
        "..\..\target\surefire-reports\TEST-$reportClass.xml"
    )
    $reportXml = [System.IO.Path]::GetFullPath($reportXml)

    if (Test-Path $reportXml) {
        Remove-Item $reportXml -Force
    }
}

$testSelector = $testClasses -join ","
& .\mvnw.cmd "-Dtest=$testSelector" test
$testExitCode = $LASTEXITCODE

$python = if ($env:REPORT51_PYTHON) {
    $env:REPORT51_PYTHON
}
else {
    "py"
}

& $python (Join-Path $PSScriptRoot "sync_scope.py")
$scopeExitCode = $LASTEXITCODE

if ($scopeExitCode -ne 0) {
    Write-Error "Khiem-Scope synchronization failed with exit code $scopeExitCode."
    exit $scopeExitCode
}

& $python (Join-Path $PSScriptRoot "sync_report51.py")
$reportExitCode = $LASTEXITCODE

if ($reportExitCode -ne 0) {
    Write-Error "Report 5.1 synchronization failed with exit code $reportExitCode."
    exit $reportExitCode
}

exit $testExitCode
