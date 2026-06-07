# Database Migrations

Flyway is the source of truth for schema changes. Never edit an applied
migration; add a new versioned migration instead.

Current migrations:

```text
V1__database_foundation.sql
V2__identity_auth_support.sql
V3__auth_session_foundation.sql
```

`V1` enables required PostgreSQL extensions. `V2` adds identity token tables.
`V3` adds refresh-session metadata, login history, and login lockout fields
without rewriting already-applied migrations or existing Supabase data.

Planned order:

```text
V4__course_classroom.sql
V5__enrollment_payment.sql
V6__learning_content.sql
V7__assessment.sql
V8__flashcard_assignment.sql
V9__ai_rag.sql
V10__analytics_notification_audit.sql
```
