$requiredVariables = @(
    "SUPABASE_DB_PASSWORD",
    "JWT_SECRET"
)

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

.\mvnw.cmd spring-boot:run
