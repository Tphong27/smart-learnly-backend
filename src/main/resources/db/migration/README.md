# Database Migrations

Flyway is the source of truth for schema changes. Never edit an applied
migration; add a new versioned migration instead.

Current migrations:

```text
V0__auth_database_baseline.sql
V1__database_foundation.sql
V2__identity_auth_support.sql
V3__auth_session_foundation.sql
V4__complete_auth_foundation.sql
```

`V0` creates the user and role foundation before later auth migrations when
provisioning a clean database. Existing Supabase databases baselined at version
2 safely ignore it. `V1` enables required PostgreSQL extensions. `V2` adds identity token tables.
`V3` adds refresh-session metadata, login history, and login lockout fields
without rewriting already-applied migrations or existing Supabase data.
`V4` adds the canonical role catalog, email-verification OTP storage, and
security-limit storage. The initial admin account is created by the
environment-controlled `AdminAccountSeeder`, never with a password committed
to a migration.

Implemented course/content foundation:

```text
V5__course_catalog_content_foundation.sql
```

Planned order:

```text
V6__enrollment_payment.sql
V7__learning_content.sql
V8__assessment.sql
V9__flashcard_assignment.sql
V10__ai_rag.sql
V11__analytics_notification_audit.sql
```
