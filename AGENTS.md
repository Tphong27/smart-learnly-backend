# AGENTS.md — Smart Learnly Backend

This file gives AI coding agents the minimum context and rules required before changing this repository.

## Repository responsibility

This repository contains the backend service for Smart Learnly Platform (SLP).

The backend is responsible for:

- Authentication and authorization.
- User, role, and permission management.
- Course, class, module, lesson, and material APIs.
- Enrollment, payment status, invoice, and manual refund support APIs.
- Question bank, test, attempt, assignment, and flashcard APIs.
- AI/RAG integration endpoints and service orchestration.
- Learning progress, weakness analysis, recommendation, readiness, reporting, and churn-risk APIs.
- Audit logs, notifications, and system configuration.

The backend is not responsible for:

- React components.
- Page routing.
- Visual design implementation.
- Figma/draw.io design artifacts.
- Static UI-only states that do not require backend state.

## Current technical baseline

Use the current repository as the source of truth before editing.

Known baseline:

- Java 17.
- Spring Boot.
- Maven.
- Spring Web MVC.
- Spring Security.
- Spring Data JPA.
- Bean Validation.
- PostgreSQL driver.
- Lombok.

## Related repositories

- Frontend repository: `smart-learnly-frontend`.
- Documentation/project hub repository: `smart-learnly-platform`, if still used by the team.

## Product truth rules

Do not invent business requirements. Use project documents as the authority:

- Vision & Scope / Report 1 defines product purpose, major features, limitations, and exclusions.
- Project Management Plan / Report 2 defines schedule, iterations, roles, risks, and team responsibilities.
- SRS / Report 3 defines scenarios, feature behavior, acceptance criteria, negative cases, and boundary values.
- Screen Design Specification defines site map, navigation, screen flows, and page IDs.
- RTW workbook should be treated as the traceability and permission reference when available.

When a requested implementation conflicts with these documents, stop and explain the conflict before coding.

## Development rules

1. Keep backend changes aligned with SRS feature IDs, use case IDs, business rules, and boundary values.
2. Implement API behavior before UI assumptions.
3. Validate all external input using DTO validation, not only frontend validation.
4. Never trust role or user identity sent from the frontend. Resolve identity from authenticated context.
5. Do not expose unpublished, deactivated, private, or unauthorized learning content.
6. Do not commit API keys, database credentials, payment credentials, OAuth secrets, or AI provider keys.
7. Do not hardcode environment-specific URLs.
8. Use consistent API responses and error messages.
9. Preserve historical records for learning progress, submitted attempts, payments, invoices, assignment submissions, and AI review decisions.
10. For AI-generated educational content, keep human review before official publication.

## Implementation order preference

Prefer building stable foundations first:

1. Domain model and database constraints.
2. DTOs and validation.
3. Repository layer.
4. Service layer with business rules.
5. Controller layer.
6. Security and permission checks.
7. Tests for success, negative, and boundary cases.

## Definition of done for backend tasks

A backend task is not done until:

- The API compiles.
- Input validation exists.
- Authorization is checked.
- Success response and error response are clear.
- Boundary values from SRS are handled where applicable.
- No secrets are committed.
- The frontend contract is documented when the API is new or changed.
