# System Settings

## Overview

System Settings lets administrators update runtime integration settings without editing `run-dev.ps1` or restarting the application.

Settings are stored in `public.system_settings` and resolved through `SystemSettingsService` with this priority:

1. Stored database override
2. Existing environment or `application.yml` fallback

Secret values are encrypted at rest when `SETTINGS_ENCRYPTION_KEY` is configured. API responses only expose `hasApiKey`, `hasRefreshToken`, or similar boolean flags instead of returning secret values.

## Current groups

### Email

Existing Email settings include Resend API key and sender identity.

### OAuth Providers

Existing Google OAuth settings include client ID, client secret, and scope.

### Google Meet

Google Meet settings now include:

- `enabled`
- `refreshToken`

Google client ID and client secret continue to come from the Google OAuth settings group. Base URLs and timeout remain environment-backed operational defaults.

### SePay Payment Display

SePay checkout display settings now include:

- `accountNumber`
- `bankName`
- `accountName`

These values are not secret because checkout returns them to learners for bank transfer instructions. Admin updates are stored as database overrides and take effect for new checkout orders without an application restart. If no database override exists, checkout falls back to `SEPAY_ACCOUNT_NUMBER`, `SEPAY_BANK_NAME`, and `SEPAY_ACCOUNT_NAME` from environment or `application.yml`.

Existing pending orders keep the bank details captured when the order was created, so changing settings only affects new checkout instructions.

### AI Integrations

AI settings now include:

Question image import:

- `enabled`
- `provider`
- `apiKey`
- `model`
- `timeoutSeconds`
- `maxFileSizeMb`
- `maxFiles`

Assignment AI:

- `enabled`
- `provider`
- `apiKey`
- `model`
- `fallbackModel`
- `timeoutSeconds`

## Settings that should stay in environment variables

Keep startup and infrastructure settings in environment variables or a secret manager:

- Database connection settings such as `SUPABASE_DB_*`
- `SPRING_PROFILES_ACTIVE`
- `JWT_SECRET`
- `SETTINGS_ENCRYPTION_KEY`
- Storage provider selection such as `APP_STORAGE_PROVIDER`
- Storage credentials and bucket wiring
- HLS token/callback secrets and external pipeline credentials

These values are required before the application can safely read database-backed settings or have startup-time wiring concerns.

## Operational notes

- Blank secret values clear the stored database override. If an environment fallback exists, the effective setting can still appear configured.
- Keeping `********` in the UI preserves the existing stored secret.
- Missing `SETTINGS_ENCRYPTION_KEY` prevents writing secret settings.
- Fresh databases need the `public.system_settings` migration to run before admin settings endpoints are used.
