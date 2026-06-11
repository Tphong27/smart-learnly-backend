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

Optional Resend SMTP configuration:

```text
RESEND_API_KEY=re_...
RESEND_FROM_EMAIL=Smart Learnly <no-reply@mail.smartlearnly.online>
GOOGLE_CLIENT_ID=your-google-web-client-id.apps.googleusercontent.com
```

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

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

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

Login returns a 15-minute bearer access token. The seven-day rotating refresh token is stored in an HttpOnly cookie.
Registration sends a six-digit email-verification OTP. Verify it with:

```json
{ "email": "student@example.com", "otpCode": "123456" }
```
