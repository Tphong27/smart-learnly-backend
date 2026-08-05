# Copy this file to run-dev.ps1 and replace only the placeholder values.
# run-dev.ps1 is ignored by Git so local credentials are not committed.

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SUPABASE_DB_PASSWORD = "replace-with-database-password"
$env:JWT_SECRET = "replace-with-at-least-32-characters"

# SePay checkout display configuration.
$env:SEPAY_ACCOUNT_NUMBER = "replace-with-bank-account-number"
$env:SEPAY_BANK_NAME = "replace-with-vietqr-bank-identifier"
$env:SEPAY_ACCOUNT_NAME = "replace-with-bank-account-name"
$env:SEPAY_PAYMENT_CODE_PREFIX = "SLP"
# Optional override. VietinBank is detected automatically and uses "SEVQR {paymentCode}".
# $env:SEPAY_TRANSFER_CONTENT_TEMPLATE = "SEVQR {paymentCode}"

# SePay server-side credentials. Never expose these to Vite/frontend env vars.
$env:SEPAY_WEBHOOK_SECRET = "replace-with-hmac-webhook-secret"
$env:SEPAY_API_TOKEN = "replace-with-sepay-api-v2-token"
$env:SEPAY_API_BASE_URL = "https://userapi.sepay.vn"
$env:SEPAY_RECONCILIATION_INTERVAL = "PT5M"

# Required only when secrets are saved through Admin > System Settings.
# This must be a base64 string that decodes to exactly 32 random bytes.
$env:SETTINGS_ENCRYPTION_KEY = "replace-with-base64-encoded-32-byte-key"

.\mvnw.cmd spring-boot:run
