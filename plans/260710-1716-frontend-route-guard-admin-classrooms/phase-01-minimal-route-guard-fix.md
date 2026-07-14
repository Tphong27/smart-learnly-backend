# Phase 01: Minimal route guard fix

## Context links
- Plan: `E:/KLTN/back-end/smart-learnly-backend/plans/260710-1716-frontend-route-guard-admin-classrooms/plan.md`
- Frontend work context: `E:/KLTN/front-end/smart-learnly-frontend`
- Primary file: `E:/KLTN/front-end/smart-learnly-frontend/src/app/routes/staffRoutes.jsx`

## Overview
- Priority: P2
- Status: pending
- Scope: one-line route guard permission update.

## Key insights
- `AdminCoursesPage.jsx` link is already correct for existing classroom list behavior: `/staff/classrooms?courseId=...`.
- `StaffClassListPage.jsx` already reads `courseId` from query params and uses admin listing for any non-trainer role.
- `RoleGuard` redirects unauthorized roles to `/403`; current nested classroom guard omits admin.

## Requirements
- Admin can open `/staff/classrooms?courseId=<courseId>` from `/admin/courses`.
- Existing TRAINER/TMO access unchanged.
- SME remains blocked from classroom management unless product decides otherwise.
- No backend/API changes.
- No new enhanced/duplicate files.

## Architecture
Existing flow stays unchanged:
`/admin/courses` link -> `/staff/classrooms?courseId=...` -> staff classroom route guard -> `StaffClassListPage` -> `classService.listAdmin(filters)` for admin/TMO.

## Related code files
Modify:
- `E:/KLTN/front-end/smart-learnly-frontend/src/app/routes/staffRoutes.jsx`

Inspect only if needed:
- `E:/KLTN/front-end/smart-learnly-frontend/src/features/admin/courses/pages/AdminCoursesPage.jsx`
- `E:/KLTN/front-end/smart-learnly-frontend/src/features/classroom/pages/StaffClassListPage.jsx`
- `E:/KLTN/front-end/smart-learnly-frontend/src/app/layouts/Sidebar.jsx`

## Implementation steps
1. In `staffRoutes.jsx`, find the classroom route group comment "Chỉ Trainer và TMO vào được quản lý lớp học".
2. Update its `RoleGuard` from `[ROLES.TRAINER, ROLES.TMO]` to `[ROLES.ADMIN, ROLES.TRAINER, ROLES.TMO]`.
3. Optionally adjust the nearby comment to mention Admin too.
4. Do not change the admin course link, class list page, service calls, or sidebar unless testing proves necessary.

## Todo list
- [ ] Update classroom route guard allowed roles.
- [ ] Build frontend.
- [ ] Smoke test admin navigation.
- [ ] Regression smoke TRAINER/TMO allowed, SME blocked.

## Success criteria
- Admin click no longer lands on `/403`.
- `/staff/classrooms?courseId=<id>` renders class list and filter chip.
- `npm run build` completes.
- No behavior change for unrelated staff routes.

## Risk assessment
- Wider admin access to all `/staff/classrooms/*` child routes, including workspace. Likely acceptable because admin is already allowed on `/staff` and can view/manage courses; confirm if admin should only list classes.
- Admin will not see sidebar "Classrooms" unless sidebar roles are changed. Not required for this bug because entry point is direct course action.
- If backend rejects admin on class APIs, frontend guard fix will expose API error instead of `/403`; then backend permission must be checked separately.

## Security considerations
- This is authorization surface change. Keep scope to `ROLES.ADMIN` only, not `SME`.
- Do not weaken `RoleGuard` globally.
- Do not remove role checks from create route; `classrooms/create` remains TMO-only.

## Next steps
- Implement the one-line route guard change.
- Run build and smoke tests listed above.

## Unresolved questions
None.
