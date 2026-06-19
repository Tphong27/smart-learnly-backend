$requiredVariables = @(
    "SUPABASE_DB_PASSWORD",
    "JWT_SECRET"
)

$javaHomeBin = Join-Path $env:JAVA_HOME "bin"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $javaHomeBin "java.exe"))) {
    throw "JAVA_HOME must point to a Java 17+ JDK."
}

$env:PATH = "$javaHomeBin;$env:PATH"

foreach ($variable in $requiredVariables) {
    if (-not (Test-Path "Env:$variable")) {
        throw "Missing required environment variable: $variable"
    }
}

$env:SUPABASE_DB_HOST = "your-supabase-pooler-host"
$env:SUPABASE_DB_PORT = "5432"
$env:SUPABASE_DB_NAME = "postgres"
$env:SUPABASE_DB_USERNAME = "your-supabase-pooler-username"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:RESEND_FROM_EMAIL = "Smart Learnly <no-reply@mail.smartlearnly.online>"

# Set RESEND_API_KEY in your local environment to send real emails through Resend SMTP.
# Without it, email delivery is skipped. Set APP_AUTH_DEBUG_LOG_TOKENS=true only for trusted local testing.
# Set GOOGLE_CLIENT_ID to enable Google Identity Services login.
# Set APP_SEED_ADMIN_ENABLED=true and APP_SEED_ADMIN_PASSWORD to create the initial admin safely.
# Set APP_SEED_CATEGORIES_ENABLED=true to seed three development course categories.
# Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY to enable course-thumbnail uploads.
# The public Supabase Storage bucket defaults to SUPABASE_COURSE_THUMBNAIL_BUCKET=course-thumbnails.

$projectRoot = $PSScriptRoot
$launchRoot = "C:\tmp\smart-learnly-backend"

if (Test-Path -LiteralPath $launchRoot) {
    $launchRootItem = Get-Item -LiteralPath $launchRoot
    if (-not ($launchRootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -or $launchRootItem.Target -ne $projectRoot) {
        throw "$launchRoot already exists and does not point to $projectRoot."
    }
}
else {
    New-Item -ItemType Junction -Path $launchRoot -Target $projectRoot | Out-Null
}

Push-Location $launchRoot
try {
    .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}
