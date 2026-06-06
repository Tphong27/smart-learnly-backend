# Database Migrations

Flyway is the source of truth for schema changes. Never edit an applied
migration; add a new versioned migration instead.

Current migration:

```text
V1__database_foundation.sql
```

`V1` enables the PostgreSQL extensions required by the platform. The next
migration should introduce the Identity and RBAC schema.

Planned order:

```text
V2__identity_user_role.sql
V3__course_classroom.sql
V4__enrollment_payment.sql
V5__learning_content.sql
V6__assessment.sql
V7__flashcard_assignment.sql
V8__ai_rag.sql
V9__analytics_notification_audit.sql
```
