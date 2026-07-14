---
title: "Fix admin classroom route guard"
description: "Minimal frontend route guard fix so admin can open course-filtered classrooms from admin courses."
status: pending
priority: P2
effort: 45m
branch: long
tags: [frontend, route-guard, admin, classrooms]
created: 2026-07-10
---

# Fix admin classroom route guard

## Problem
Admin can access `/admin/courses`, but clicking "View classes for this course" navigates to `/staff/classrooms?courseId=...` and hits `/403` because `staffRoutes.jsx` wraps `staff/classrooms` with `RoleGuard` allowing only `TRAINER,TMO`.

## Minimal decision
Use KISS/YAGNI: update only the classroom route guard to include `ROLES.ADMIN`. Do not add new routes, duplicate pages, or change the link.

## Phase
- [Phase 01: Minimal route guard fix](phase-01-minimal-route-guard-fix.md) — pending

## Files to change
- `E:/KLTN/front-end/smart-learnly-frontend/src/app/routes/staffRoutes.jsx`

## Files to inspect only
- `E:/KLTN/front-end/smart-learnly-frontend/src/features/admin/courses/pages/AdminCoursesPage.jsx`
- `E:/KLTN/front-end/smart-learnly-frontend/src/features/classroom/pages/StaffClassListPage.jsx`
- `E:/KLTN/front-end/smart-learnly-frontend/src/app/routes/RoleGuard.jsx`

## Validation
From `E:/KLTN/front-end/smart-learnly-frontend`:
1. `npm run build`
2. `npm run lint` if time permits; do not block on unrelated existing style warnings.
3. Manual smoke:
   - Login/admin session.
   - Open `/admin/courses`.
   - Click "View classes for this course".
   - Verify URL stays `/staff/classrooms?courseId=<id>` and no `/403`.
   - Verify course filter chip loads and list fetches.
   - Regression: TRAINER and TMO still access `/staff/classrooms`; SME still gets `/403`.

## Key dependencies
- `StaffClassListPage` already routes non-trainer users to `classService.listAdmin(filters)`, so admin should use existing admin list API path.
- `/staff` is already globally allowed for admin in `dashboard-path.js`; the blocking layer is the nested `RoleGuard` in `staffRoutes.jsx`.

## Unresolved questions
None.
