$requiredVariables = @(
    "SUPABASE_DB_PASSWORD",
    "JWT_SECRET"
    # SETTINGS_ENCRYPTION_KEY is optional. Without it the backend will keep working
    # but Admin > System Settings cannot persist secret values (Google client secret,
    # Resend API key) and a warning is logged at startup. Generate one with:
    #   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    # then paste the base64 string below or export it in your shell before running.
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
$env:SUPABASE_DB_PORT = "6543"
$env:SUPABASE_DB_NAME = "postgres"
$env:SUPABASE_DB_USERNAME = "your-supabase-pooler-username"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:RESEND_FROM_EMAIL = "Smart Learnly <no-reply@mail.smartlearnly.online>"

# AES-256-GCM key used to encrypt secret system settings (email API key, Google
# client secret, ...). Must decode to exactly 32 bytes. Leave blank if you do not
# need to edit secret settings from the admin UI; env fallbacks (e.g. RESEND_API_KEY,
# GOOGLE_CLIENT_SECRET) will still work for non-UI flows.
$env:SETTINGS_ENCRYPTION_KEY = "REPLACE_WITH_32_BYTE_BASE64_KEY"

$env:APP_STORAGE_PROVIDER = "r2"

$env:CLOUDFLARE_R2_ACCOUNT_ID = "account-id-cua-ban"
$env:CLOUDFLARE_R2_ENDPOINT = "https://account-id-cua-ban.r2.cloudflarestorage.com"
$env:CLOUDFLARE_R2_REGION = "auto"

$env:CLOUDFLARE_R2_ACCESS_KEY_ID = "access-key-id-vua-tao"
$env:CLOUDFLARE_R2_SECRET_ACCESS_KEY = "secret-access-key-vua-tao"

# Public/custom domain base URLs for each R2 bucket. Use the bucket's public r2.dev URL
# for local testing, and a custom domain for production.
$env:CLOUDFLARE_R2_COURSE_THUMBNAIL_PUBLIC_BASE_URL = "https://course-thumbnails.example.com"
$env:CLOUDFLARE_R2_LESSON_MATERIAL_PUBLIC_BASE_URL = "https://lesson-materials.example.com"
$env:CLOUDFLARE_R2_LESSON_RESOURCE_PUBLIC_BASE_URL = "https://lesson-resources.example.com"

$env:APP_STORAGE_COURSE_THUMBNAIL_BUCKET = "course-thumbnails"
$env:APP_STORAGE_LESSON_MATERIAL_BUCKET = "lesson-materials"
$env:APP_STORAGE_LESSON_RESOURCE_BUCKET = "lesson-resources"
$env:APP_STORAGE_AVATAR_BUCKET = "avatar"

# Set RESEND_API_KEY in your local environment to send real emails through Resend SMTP.
# Without it, email delivery is skipped. Set APP_AUTH_DEBUG_LOG_TOKENS=true only for trusted local testing.
# Set GOOGLE_CLIENT_ID to enable Google Identity Services login.
# Set APP_SEED_ADMIN_ENABLED=true and APP_SEED_ADMIN_PASSWORD to create the initial admin safely.
# Set APP_SEED_CATEGORIES_ENABLED=true to seed three development course categories.
# Set CLOUDFLARE_R2_ACCESS_KEY_ID and CLOUDFLARE_R2_SECRET_ACCESS_KEY to enable file uploads through Cloudflare R2.
# Set APP_STORAGE_PROVIDER=r2 to use Cloudflare R2 instead of Supabase Storage.

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
