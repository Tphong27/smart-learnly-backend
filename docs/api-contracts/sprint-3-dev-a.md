# Sprint 3 Dev A API Contract

## Assumptions

- Backend resolves the trainee from the authenticated security context.
- The free enrollment request contains only `courseId`.
- My Courses is based on enrollment access, not public course visibility.
- `active` and `completed` are the only statuses that grant learner access.
- `cancelled` and `refunded` never reactivate silently.
- Enrollment history currently covers course enrollments. Order and transaction
  history belongs to Dev B.
- Paid course/class grant methods are internal service APIs invoked only after
  an exact, successful payment match.

## POST `/api/v1/enrollments/free`

Role: `TRAINEE`

Request:

```json
{
  "courseId": "uuid"
}
```

Successful response data:

```json
{
  "enrollmentId": "uuid",
  "courseId": "uuid",
  "status": "ACTIVE",
  "enrollmentDate": "2026-06-18T10:00:00Z",
  "alreadyEnrolled": false,
  "reactivated": false
}
```

An existing active/completed enrollment returns the same record with
`alreadyEnrolled: true`. A cancelled/refunded enrollment for a free course may
be reactivated, returns `reactivated: true`, and writes an enrollment status
audit row. Paid, draft, inactive, deleted, or missing courses do not create a
new free enrollment.

## GET `/api/v1/enrollments/my-courses`

Role: `TRAINEE`

Returns active/completed enrolled courses with category and enrollment
metadata. A course remains in this list after it becomes draft/inactive or is
soft-deleted because access is determined by the enrollment. The response also
includes `courseStatus`, `accessAllowed`, and `accessBlockedReason`; an explicit
Admin/TMO access block sets `accessAllowed: false` without hiding the record.

## GET `/api/v1/enrollments`

Role: `TRAINEE`

Query parameters: `page` (default `0`), `size` (default `20`, max `100`).

Returns paginated course enrollment history owned by the authenticated
trainee.

## GET `/api/v1/enrollments/{enrollmentId}/status-history`

Roles: owner `TRAINEE`, `ADMIN`, or `TMO`.

Returns the append-only transition history for one course enrollment. A trainee
cannot read another learner's enrollment history.

## Internal paid grant services

- `CourseEnrollmentService.grantPaidCourseEnrollment(studentId, courseId, transactionId)`
- `ClassEnrollmentService.grantPaidClassEnrollment(studentId, classId, paidPrice, transactionId)`

Both methods require a matching `SUCCESS` transaction. Paid cancelled/refunded
enrollments can only return to active through these methods, and every
transition stores the payment transaction in `enrollment_status_history`.
Class grants lock the class row before counting active enrollments, preventing
concurrent successful payments from overselling the final seat.

## Admin/TMO class management

Base path: `/api/v1/admin/classes`. Roles: `ADMIN`, `TMO`.

- `GET /` — paginated list; filters: `courseId`, `trainerId`, `status`, `keyword`.
- `GET /{classId}` — class detail and current active/available seat counts.
- `POST /` — create and configure course, name, trainer, schedule, dates, and capacity.
- `PATCH /{classId}` — partial update; capacity cannot fall below active enrollments.
- `POST /{classId}/cancel` — cancel while preserving history.
- `DELETE /{classId}` — soft-delete and cancel.

Changing the course is rejected after enrollment/order/transaction history
exists. Trainer assignment accepts only an active user with role `TRAINER`.

## PATCH `/api/v1/admin/courses/{courseId}/access`

Roles: `ADMIN`, `TMO`.

Request:

```json
{
  "accessBlocked": true,
  "reason": "Compliance hold"
}
```

A non-blank reason is mandatory when blocking. Unblocking clears the block
timestamp, reason, and actor.

Learner content endpoints must call
`EnrollmentAccessService.requireCourseAccess(courseId)`. The evaluator accepts
only active/completed enrollment, honors explicit course access blocks, and
does not revoke access merely because a course is no longer published or was
soft-deleted.

## Class pricing contract

`price` is required when creating a class, must be greater than zero, and is
returned by the Admin/TMO class list and detail APIs. A class price may be
updated for future purchases.

Cart items with a `classId` return the class `price`, and checkout uses that
price instead of the related course price. Existing `order_items` remain
immutable snapshots when a class price changes later.
