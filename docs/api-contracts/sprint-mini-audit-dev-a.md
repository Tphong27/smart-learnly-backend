# FT-18 Mini Sprint - Dev A Audit Foundation Contract

## 1. Ownership

Dev A owns the database and persistence foundation for FT-18 System Activity
Log. Dev A does not own the Admin query controller or frontend page.

Committed effort: **11 SP**.

| Task | SP | Deliverable |
|---|---:|---|
| A1. Flyway migration for `audit_logs` | 3 | Table, constraints, indexes |
| A2. Append-only database protection | 2 | Trigger and safe trigger function |
| A3. Supabase access hardening | 1 | RLS and Data API role revocation |
| A4. Entity, repository, and event model | 2 | Java persistence foundation |
| A5. Sensitive-data sanitizer | 2 | Recursive backend masking |
| A6. Foundation tests and handoff | 1 | Migration, repository, and masking verification |

## 2. Fixed decisions

- This work implements existing feature **FT-18**. It does not create a new FT.
- Audit Log is readable by `ADMIN` only in the MVP.
- Schema changes are committed only through a backend Flyway migration.
- Do not create the table manually through Supabase Dashboard, SQL Editor,
  MCP SQL execution, or ad-hoc production DDL.
- The frontend never reads `audit_logs` directly through Supabase.
- Existing `login_history`, `sepay_webhook_events`, and
  `enrollment_status_history` remain separate specialist history tables.
- Audit rows are append-only and have no update/delete application workflow.
- Sensitive data is sanitized before persistence, not only before API output.

## 3. Runtime baseline

Verified against Supabase project `csbtyhjfzhizaasahwub` on 2026-06-22:

- PostgreSQL 17.
- Runtime IDs use UUID.
- Latest applied Flyway version is `V10`.
- `audit_logs` does not exist.
- Public runtime tables use RLS.
- Current public tables have no direct RLS policies.

Expected migration filename:

```text
src/main/resources/db/migration/V11__system_activity_audit_log.sql
```

`V11` is valid against the verified runtime history. If another migration is
merged first, Dev A must resolve the next available Flyway version before
merging. Never edit an already-applied migration.

## 4. Flyway migration contract

### 4.1 Table

```sql
CREATE TABLE public.audit_logs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     timestamp with time zone NOT NULL DEFAULT now(),

    actor_type      character varying(30) NOT NULL,
    actor_id        uuid,
    actor_email     character varying(255),
    actor_role      character varying(30),

    action          character varying(100) NOT NULL,
    domain          character varying(50) NOT NULL,
    result          character varying(20) NOT NULL,

    target_type     character varying(50),
    target_id       character varying(100),
    summary         character varying(500) NOT NULL,

    old_values      jsonb,
    new_values      jsonb,
    metadata        jsonb,

    ip_address      character varying(45),
    user_agent      character varying(500),
    correlation_id  character varying(100),
    error_code      character varying(100),

    CONSTRAINT audit_logs_actor_id_fkey
        FOREIGN KEY (actor_id)
        REFERENCES public.users(id)
        ON DELETE SET NULL,
    CONSTRAINT chk_audit_logs_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM', 'PAYMENT_PROVIDER')),
    CONSTRAINT chk_audit_logs_result
        CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT chk_audit_logs_action_not_blank
        CHECK (length(trim(action)) > 0),
    CONSTRAINT chk_audit_logs_domain_not_blank
        CHECK (length(trim(domain)) > 0),
    CONSTRAINT chk_audit_logs_summary_not_blank
        CHECK (length(trim(summary)) > 0)
);
```

Do not add foreign keys from `target_id`. Targets are polymorphic and
`target_id` must also support non-UUID external identifiers.

### 4.2 Required indexes

```sql
CREATE INDEX idx_audit_logs_occurred_at
    ON public.audit_logs (occurred_at DESC, id DESC);

CREATE INDEX idx_audit_logs_domain_time
    ON public.audit_logs (domain, occurred_at DESC);

CREATE INDEX idx_audit_logs_action_time
    ON public.audit_logs (action, occurred_at DESC);

CREATE INDEX idx_audit_logs_actor_time
    ON public.audit_logs (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX idx_audit_logs_result_time
    ON public.audit_logs (result, occurred_at DESC);

CREATE INDEX idx_audit_logs_target
    ON public.audit_logs (target_type, target_id, occurred_at DESC)
    WHERE target_type IS NOT NULL AND target_id IS NOT NULL;

CREATE INDEX idx_audit_logs_correlation
    ON public.audit_logs (correlation_id)
    WHERE correlation_id IS NOT NULL;
```

Do not add a broad JSONB GIN index in this mini sprint.

### 4.3 Append-only protection

```sql
CREATE OR REPLACE FUNCTION public.prevent_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON public.audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_audit_log_mutation();
```

`SET search_path = ''` is mandatory. The migration must not introduce the
Supabase `function_search_path_mutable` warning.

### 4.4 RLS and Data API access

```sql
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.audit_logs FROM anon, authenticated;
```

Do not create `anon` or `authenticated` policies. The Spring Boot database
connection is the only data-access path in the MVP.

### 4.5 Documentation

Add a table comment:

```sql
COMMENT ON TABLE public.audit_logs IS
    'FT-18 append-only system activity audit ledger; backend access only.';
```

Update `src/main/resources/db/migration/README.md` with the migration purpose.

## 5. Java persistence contract

Suggested package:

```text
com.smartlearnly.backend.common.audit
```

Required outputs:

```text
common/audit/AuditLog.java
common/audit/AuditLogRepository.java
common/audit/AuditEvent.java
common/audit/AuditActorType.java
common/audit/AuditResult.java
common/audit/AuditDomain.java
common/audit/AuditAction.java
common/audit/AuditDataSanitizer.java
```

The existing `AuditLogService` remains Dev B's integration responsibility.
Dev A may adapt constructor/package dependencies only when required to make the
foundation compile, but must not independently integrate business services.

### 5.1 Entity rules

- Map to `public.audit_logs`.
- Use generated UUID ID.
- Map JSONB fields as structured maps, not serialized JSON strings.
- `occurredAt` is immutable after insert.
- No update method or delete repository operation should be used by services.
- Actor relation should not require loading a `UserAccount` entity. Store
  `actorId` as nullable UUID to keep system/provider events lightweight.
- Normalize enum persistence to the exact uppercase values in this contract.

Recommended JSONB mapping for the current Spring Boot/Hibernate baseline:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "old_values", columnDefinition = "jsonb")
private Map<String, Object> oldValues;
```

Apply the same mapping to `newValues` and `metadata`.

### 5.2 Event model

`AuditEvent` must carry enough information for Dev B to persist:

```text
actorType
actorId
actorEmail
actorRole
action
domain
result
targetType
targetId
summary
oldValues
newValues
metadata
ipAddress
userAgent
correlationId
errorCode
```

Provide factory/builders for:

- Authenticated user event.
- System event.
- Payment-provider event.

The event model must not accept or depend on a frontend-provided actor identity.

### 5.3 Mandatory action allowlist

`AuditAction` must define these MVP actions:

```text
ACCOUNT_REGISTERED
LOGIN_SUCCEEDED
LOGIN_FAILED
LOGIN_BLOCKED
GOOGLE_LOGIN_SUCCEEDED
LOGOUT_SUCCEEDED
PASSWORD_RESET_REQUESTED
PASSWORD_RESET_COMPLETED
PASSWORD_CHANGED
EMAIL_VERIFIED
PROFILE_UPDATED

CATEGORY_CREATED
CATEGORY_UPDATED
CATEGORY_DELETED

COURSE_CREATED
COURSE_UPDATED
COURSE_PUBLISHED
COURSE_DEACTIVATED
COURSE_DELETED
COURSE_ACCESS_BLOCKED
COURSE_ACCESS_UNBLOCKED

SECTION_CREATED
SECTION_UPDATED
SECTION_DELETED
SECTIONS_REORDERED

LESSON_CREATED
LESSON_UPDATED
LESSON_DEACTIVATED
LESSONS_REORDERED

CLASS_CREATED
CLASS_UPDATED
CLASS_CANCELLED
CLASS_DELETED

ENROLLMENT_CREATED
ENROLLMENT_REACTIVATED
ENROLLMENT_STATUS_CHANGED

ORDER_CREATED
ORDER_CANCELLED
PAYMENT_CREATED
PAYMENT_CALLBACK_RECEIVED
PAYMENT_SUCCEEDED
PAYMENT_MISMATCHED
PAYMENT_FAILED
PAYMENT_RECONCILED
```

Do not add action strings outside this allowlist without updating the FT-18
sprint document and receiving team review.

### 5.4 Required domains

```text
AUTH
USER
CATEGORY
COURSE
CONTENT
CLASS
ENROLLMENT
ORDER
PAYMENT
SECURITY
SYSTEM
```

## 6. Sensitive-data sanitizer contract

Sanitization occurs before entity persistence.

The sanitizer must:

- Recursively sanitize nested maps and lists.
- Match key names case-insensitively.
- Normalize separators so aliases such as `password_hash`, `passwordHash`,
  and `password-hash` are treated consistently.
- Replace blocked values with the exact text `***REDACTED***`.
- Return a defensive copy and never mutate caller-owned data.
- Safely handle null, scalar, collection, array, and map values.

Minimum normalized denylist:

```text
password
passwordhash
currentpassword
newpassword
confirmpassword
token
accesstoken
refreshtoken
passwordresettoken
idtoken
otp
otpcode
secret
apikey
servicerolekey
authorization
cookie
signature
```

The following must never be copied into audit JSONB:

- Password or password hash.
- OTP.
- Access, refresh, reset, or Google ID tokens.
- API keys and service-role keys.
- Payment/webhook secrets or signatures.
- Authorization and Cookie headers.
- Raw email bodies containing tokens.
- Raw SePay webhook payload.
- Full lesson content.

## 7. Test contract

### 7.1 Migration verification

Verify:

- Flyway applies after V10.
- Table and all required indexes exist.
- RLS is enabled.
- `anon` and `authenticated` have no table privileges.
- Insert succeeds through the backend database role.
- Update fails with `audit_logs is append-only`.
- Delete fails with `audit_logs is append-only`.
- Trigger function has a fixed empty search path.
- Migration works against the current schema and a clean migration path.

### 7.2 Repository/entity tests

Verify:

- UUID and timestamp are generated.
- User actor can be stored with actor ID/email/role.
- System and provider actors work with null actor ID.
- JSONB maps round-trip without becoming escaped JSON strings.
- All enum values persist with the expected casing.
- Nullable target/correlation/error fields work.

### 7.3 Sanitizer tests

Verify:

- Every denylist key is redacted.
- Matching is case-insensitive.
- Snake, kebab, and camel-case aliases are redacted.
- Nested map/list/array values are sanitized.
- Safe fields remain unchanged.
- Input objects are not mutated.
- Null and empty inputs are handled.

## 8. Supabase verification

After Flyway applies through the normal deployment/test process:

1. Inspect `audit_logs` columns, constraints, indexes, RLS, and privileges.
2. Run Supabase security advisors.
3. Run Supabase performance advisors.
4. Fix any warning introduced by this migration.

Do not remove unrelated existing advisor findings in this task unless the team
explicitly expands scope.

Relevant advisor references:

- [RLS enabled with no policy](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)
- [Function search path mutable](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable)
- [Unindexed foreign keys](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys)

For `audit_logs`, RLS-with-no-policy is intentional because direct Data API
access is revoked. Document this decision in the migration handoff.

## 9. Handoff to Dev B

Dev A must provide:

- Final Flyway migration filename and checksum-safe committed SQL.
- Entity and repository classes.
- `AuditEvent` creation contract.
- Actor/result/domain/action constants.
- Sanitizer API and denylist.
- Example user, system, and payment-provider events.
- Test evidence for insert, append-only protection, and masking.
- Advisor output limited to findings introduced by the migration.

Example handoff event:

```java
AuditEvent event = AuditEvent.userEvent(
        actorId,
        actorEmail,
        actorRole,
        AuditAction.COURSE_UPDATED,
        AuditDomain.COURSE,
        AuditResult.SUCCESS,
        "COURSE",
        courseId.toString(),
        "Course information was updated",
        Map.of("status", "draft"),
        Map.of("status", "published"),
        Map.of("courseTitle", courseTitle)
);
```

Dev B owns:

- Persisting events through `AuditLogService`.
- Resolving actors from the authenticated context.
- Business transaction integration.
- Admin list/detail API and authorization.

## 10. Dev A Definition of Done

- [ ] Work is traceable to FT-18 and creates no new feature ID.
- [ ] Flyway migration follows the next valid runtime version.
- [ ] No audit DDL was created manually on Supabase.
- [ ] `audit_logs` table, constraints, comments, and indexes exist.
- [ ] Append-only update/delete protection works.
- [ ] Trigger function uses `SET search_path = ''`.
- [ ] RLS is enabled and Data API roles have no direct access.
- [ ] Entity and repository compile.
- [ ] JSONB mapping works as structured data.
- [ ] Mandatory action/domain/result constants exist.
- [ ] Sanitizer redacts the complete denylist recursively.
- [ ] Existing specialist history tables remain unchanged.
- [ ] Migration, repository, and sanitizer tests pass.
- [ ] Migration README is updated.
- [ ] Supabase advisors are checked after Flyway verification.
- [ ] Handoff package for Dev B is complete.
- [ ] No credentials or secrets are committed.
