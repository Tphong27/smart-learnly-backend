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

Optional Resend configuration:

```text
RESEND_API_KEY=re_...
RESEND_FROM_EMAIL=Smart Learnly <no-reply@mail.smartlearnly.online>
GOOGLE_CLIENT_ID=your-google-web-client-id.apps.googleusercontent.com
```

The sending domain `mail.smartlearnly.online` must remain verified in Resend. When
`RESEND_API_KEY` is empty, verification and password-reset emails are logged for
local development instead of being sent.

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
