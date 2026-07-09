## Why

Trainers need to customize lessons, lesson order, learning resources, and inline quizzes for their assigned classes without changing the SME-authored master course or any other trainer's class. The current architecture stores curriculum directly under `courses`, so class-specific learning content cannot be isolated or resolved cleanly.

## What Changes

- Introduce versioned curriculum as a separate domain from course catalog metadata.
- Add class curriculum bindings so each class can inherit the master curriculum or point to a class-specific published version and draft version.
- Add backend resolution rules so public users see master curriculum, trainer owners see class draft/published curriculum, and enrolled trainees see only the published effective class curriculum.
- Add trainer APIs to initialize a class draft, edit/reorder sections and lessons, add/remove resources, customize inline quiz content, and publish the draft.
- Update trainee learning content to return effective curriculum metadata such as curriculum version, scope, customization state, and source.
- Preserve master course isolation: trainer changes must not mutate the SME master course or other classes.
- Update lesson progress to become class-aware and stable across curriculum versions.
- Add frontend class navigation from staff course lists and public course detail, and add a trainer class curriculum workspace.
- Keep master-to-class sync/compare, TMO approval workflows, and formal `tests/test_questions` deep quiz integration out of this first phase.

## Capabilities

### New Capabilities
- `versioned-curriculum`: Defines master and class-scoped curriculum versions, section/lesson/resource snapshots, class bindings, and curriculum resolution behavior.
- `trainer-class-curriculum`: Defines trainer-owned class draft editing, lesson/resource/inline-quiz customization, publish behavior, and access restrictions.
- `effective-learning-content`: Defines how enrolled trainees receive the correct published curriculum for a class and how progress remains class-aware.
- `course-class-navigation`: Defines frontend navigation and filtering between courses and classes in staff and public course screens.

### Modified Capabilities

None. No existing OpenSpec capabilities are present in this repository.

## Impact

- Backend database migrations for curriculum versions, class bindings, curriculum sections, curriculum lessons, curriculum resources, and class-aware progress.
- Backend domain/entities/repositories/services under course, learning, classroom, or a new curriculum package.
- Backend APIs for trainer class curriculum management and updated learning content responses.
- Existing backend services affected: `CourseContentAdminService`, `LearningContentService`, `TraineeProgressService`, class services, assignment/test validation boundaries, and course preview/detail flows.
- Frontend affected areas: staff/admin course list, staff classroom list filters, class workspace, public course detail, learning workspace metadata handling, and service modules.
- Security impact: trainer mutations require resource-level ownership checks, not only role checks.
- Testing impact: migration validation, curriculum resolver tests, ownership/security tests, learning content regression tests, progress tests, and frontend build/navigation checks.
