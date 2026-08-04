# Smart Learnly Backend

Spring Boot backend for Smart Learnly Platform.

## Local setup

Requirements:

- Java 17 or newer.
- Access to the project PostgreSQL database.
- A JWT secret containing at least 32 characters.

Set these environment variables before starting the application:

```text
SUPABASE_DB_PASSWORD=...
JWT_SECRET=...
```

The `dev` profile defaults to the Supabase pooler on port `6543`. If you
override the database URL or port locally, keep PgJDBC server-side prepared
statements disabled for the transaction pooler by retaining
`prepareThreshold=0` in the JDBC URL.

Optional Resend SMTP configuration:

```text
RESEND_API_KEY=re_...
RESEND_FROM_EMAIL=Smart Learnly <no-reply@mail.smartlearnly.online>
GOOGLE_CLIENT_ID=your-google-web-client-id.apps.googleusercontent.com
```

Optional Supabase Storage configuration for course-thumbnail uploads:

```text
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=...
SUPABASE_COURSE_THUMBNAIL_BUCKET=course-thumbnails
APP_STORAGE_COURSE_THUMBNAIL_MAX_SIZE=5MB
```

Provision `course-thumbnails` as a public Supabase Storage bucket before using
the upload endpoint. The service-role key is backend-only and must never be
exposed to the frontend.

SePay runtime configuration:

```text
SEPAY_WEBHOOK_SECRET=replace-with-webhook-secret
SEPAY_ACCOUNT_NUMBER=replace-with-bank-account-number
SEPAY_BANK_NAME=replace-with-bank-name
SEPAY_ACCOUNT_NAME=replace-with-bank-account-name
SEPAY_PAYMENT_CODE_PREFIX=SLP
SEPAY_TRANSFER_CONTENT_TEMPLATE=
SEPAY_RECONCILIATION_INTERVAL=PT5M
SEPAY_API_TOKEN=replace-with-sepay-api-token
SEPAY_API_BASE_URL=https://userapi.sepay.vn
```

`SEPAY_ACCOUNT_NUMBER`, `SEPAY_BANK_NAME`, and `SEPAY_ACCOUNT_NAME` are
required to generate checkout payment instructions. `SEPAY_API_TOKEN` and
`SEPAY_API_BASE_URL` are used by reconciliation for missed webhooks. Use
`SEPAY_TRANSFER_CONTENT_TEMPLATE` only to override bank-specific transfer
content. When it is empty and the configured bank is VietinBank, checkout
automatically uses `SEVQR {paymentCode}`.
Use placeholder values in shared examples only; never commit real bank account
values or payment secrets.

The SePay webhook URL is:

```text
https://<public-backend-domain>/api/v1/payments/webhooks/sepay
```

Configure it as an inbound, JSON, HMAC-SHA256 webhook in SePay and use the
same secret for both SePay and `SEPAY_WEBHOOK_SECRET`. For the complete local,
Test mode, production, and troubleshooting checklist, see
[`docs/SEPAY_SETUP_GUIDE_VI.md`](docs/SEPAY_SETUP_GUIDE_VI.md).

The sending domain `mail.smartlearnly.online` must remain verified in Resend. When
`RESEND_API_KEY` is empty, email delivery is skipped. Set `APP_AUTH_DEBUG_LOG_TOKENS=true`
only in a trusted local environment when the generated OTP/token must be logged. The application connects to
`smtp.resend.com:587` with STARTTLS and sends email asynchronously.

To create the initial administrator without committing a password:

```text
APP_SEED_ADMIN_ENABLED=true
APP_SEED_ADMIN_EMAIL=admin@slp.vn
APP_SEED_ADMIN_PASSWORD=use-a-strong-secret-from-your-environment
```

Use `run-dev.example.ps1` as the non-secret template for a local `run-dev.ps1`.

## Commands

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

### Test coverage

On Windows, run:

```powershell
.\run-coverage.ps1
```

The script selects a JDK 17+ installation, runs `clean verify` through a
temporary ASCII drive alias and always removes the alias afterwards. This
avoids the Java agent startup failure caused by non-ASCII repository paths such
as `Đồ án`.

JaCoCo writes the coverage data and reports to:

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`
- CSV: `target/site/jacoco/jacoco.csv`
- Execution data: `target/jacoco.exec`

Open the HTML report and select
`com.smartlearnly.backend.videoai` to inspect line, branch and method coverage
for the YouTube summary flow. Coverage is report-only and does not currently
fail the build.

On systems where the repository path is already ASCII, the equivalent Maven
command is:

```powershell
.\mvnw.cmd clean verify
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Video AI with local faster-whisper

Video lessons can produce a timestamped transcript, captions, summary, chapters,
key points and reviewable flashcard candidates. Generation is always started
manually after HLS processing finishes; learners only receive instructor-published
content for the current video version.

Install the local transcription runtime using
[`scripts/video-ai/README.md`](scripts/video-ai/README.md), then configure:

```text
APP_VIDEO_AI_ENABLED=true
APP_VIDEO_AI_TRANSCRIPTION_ENABLED=true
APP_VIDEO_AI_TRANSCRIPTION_PYTHON_COMMAND=<absolute path to the venv Python executable>
APP_VIDEO_AI_TRANSCRIPTION_MODEL_CACHE_DIRECTORY=<writable model cache directory>
APP_VIDEO_AI_GENERATION_ENABLED=true
VIDEO_AI_GEMINI_API_KEY=<backend-only Gemini API key>
APP_HLS_AI_AUDIO_BUCKET=<private R2 bucket>
```

For the GitHub Actions provider, configure repository variable
`R2_AI_AUDIO_BUCKET` with the exact same private bucket name. This bucket must
not have a public/custom domain; the workflow excludes `ai/*` from the playback
bucket and uploads the derivative separately.

Keep all feature flags disabled until the Python runtime, private bucket and Gemini
key are configured. Existing HLS videos created before the AI audio derivative was
introduced must be uploaded again. `faster-whisper` has no transcription API fee,
but local compute, model storage, R2 and Gemini usage can still incur costs.

To seed three development course categories while using the `dev` profile:

```text
APP_SEED_CATEGORIES_ENABLED=true
```

## Auth API

Auth endpoints use the `/api/v1/auth` prefix:

- `POST /register`
- `POST /login`
- `POST /google`
- `POST /refresh`
- `POST /logout`
- `POST /forgot-password`
- `POST /reset-password`
- `POST /verify-email`
- `POST /resend-verification`
- `GET/PATCH /profile`
- `POST /change-password`

## Sprint 2 Dev A APIs

Admin-only endpoints use bearer JWT authentication:

- `GET/POST /api/v1/admin/categories`
- `GET/PATCH/DELETE /api/v1/admin/categories/{categoryId}`
- `POST /api/v1/admin/uploads/course-thumbnails`

Login returns a 15-minute bearer access token. The seven-day rotating refresh token is stored in an HttpOnly cookie.
Registration sends a six-digit email-verification OTP. Verify it with:

```json
{ "email": "student@example.com", "otpCode": "123456" }
```
