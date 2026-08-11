# AGENTS.md — Smart Learnly Backend

This file gives AI coding agents the minimum context and rules required before changing this repository.

## Required shared organization rules

Before reorganizing, refactoring, renaming, moving, or deleting backend code,
read `../AI_CODE_ORGANIZATION_RULES.md` in full. Its simplicity, dead-code
evidence, small-batch, and verification rules are mandatory in addition to this
file. In particular, do not create tiny pass-through services/helpers and do
not treat Spring-managed code as unused based only on static imports.

If the repository is opened without the parent workspace, these core rules
still apply:

- Preserve behavior and separate structural refactors from feature changes.
- Prefer the fewest files, functions, layers, and abstractions that keep each
  responsibility clear.
- Keep one-use one-to-three-line operations inline unless they name an
  important business rule.
- Extract code only for reuse, a distinct business responsibility, meaningful
  complexity or side effects, independent testing, or materially clearer flow.
- Do not add pass-through helpers, services, factories, builders, managers, or
  mappers with no independent responsibility.
- Before deleting code, check direct use, Spring injection and annotations,
  scheduled jobs, reflection, serialization, scripts, tests, docs, and public
  API contracts. Report uncertain candidates instead of deleting them.
- Never delete historical migrations, payment/learning records, uploads,
  secrets, deployment files, or unrelated working-tree changes.
- Refactor one bounded feature at a time and compile/test after every batch.
- Use feature-first role folders. Checkout belongs under
  `commerce/checkout/controller|dto|service`; SePay belongs under
  `payment/sepay/controller|dto|service|config`. Shared commerce entities and
  repositories stay at the commerce level when multiple subfeatures use them.
- Add a concise Vietnamese documentation comment above every named production
  method. Business methods explain purpose and important rules; trivial
  technical helpers need one sentence. Do not comment obvious lines, and keep
  comments synchronized with behavior. Apply this to every production file
  created or materially edited in the current task.

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
