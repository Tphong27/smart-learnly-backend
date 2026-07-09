## 1. Database and Backfill

- [x] 1.1 Add curriculum versioning migration for curriculum versions, class curriculum bindings, curriculum sections, curriculum lessons, and curriculum lesson resources.
- [x] 1.2 Add stable logical lesson identity fields and indexes needed for copied curriculum lessons.
- [x] 1.3 Backfill one published master curriculum version per existing course from current course sections, lessons, and lesson resources.
- [x] 1.4 Backfill class curriculum bindings for existing classes as inherited from master curriculum.
- [x] 1.5 Add class-aware progress migration while preserving existing lesson progress where a class context can be derived. ← (verify: migrations run from a clean database and preserve existing master curriculum data)

## 2. Backend Curriculum Domain

- [x] 2.1 Add curriculum entities, enums, repositories, and DTOs for curriculum versions, bindings, sections, lessons, and resources.
- [x] 2.2 Add mapping utilities to convert curriculum rows into existing section, lesson, resource, and learning response shapes.
- [x] 2.3 Add a curriculum cloning service that copies effective source curriculum into a new class draft with lineage and lesson identity preserved.
- [x] 2.4 Add `CurriculumResolutionService` for public master, master authoring, trainer editing, and trainee learning contexts. ← (verify: resolver never returns draft curriculum for trainee learning and enforces trainer ownership)

## 3. Backend Master and Learning Read Paths

- [x] 3.1 Update public course detail and preview reads to use the unified curriculum source instead of legacy `modules` reads where needed.
- [x] 3.2 Update admin master content read/write flows to operate on master curriculum versions while preserving route compatibility.
- [x] 3.3 Update trainee learning content to resolve by `courseId + classId`, load the effective published curriculum version, and return version/scope/source metadata.
- [x] 3.4 Update lesson progress services and repositories to use class-aware logical lesson identity for reads and writes. ← (verify: progress in one class does not mark lessons complete in another class)

## 4. Backend Trainer Class Curriculum APIs

- [x] 4.1 Add trainer class curriculum controller routes under `/api/v1/trainer/classes/{classId}/curriculum`.
- [x] 4.2 Implement class curriculum editor read endpoint with inherited, draft, and published state metadata.
- [x] 4.3 Implement draft initialization from master inherited or class published curriculum.
- [x] 4.4 Implement draft section and lesson create, update, delete, and reorder operations.
- [x] 4.5 Implement draft lesson resource add, remove, replace, and reorder behavior.
- [x] 4.6 Implement inline quiz content validation for draft quiz lessons using existing quiz validation behavior where possible.
- [x] 4.7 Implement publish operation that makes class published curriculum effective immediately and clears the active draft pointer. ← (verify: trainer edits affect only the assigned class and never mutate master or other classes)

## 5. Backend Compatibility and Authorization

- [x] 5.1 Preserve existing assignment visibility while validating lesson references against class effective curriculum when class context is available.
- [x] 5.2 Preserve existing formal test engine behavior without attempting deep class-scoped cloning in this phase.
- [x] 5.3 Ensure every trainer curriculum read/write operation checks `class.trainerId == currentUser.id` at service level.
- [x] 5.4 Ensure trainee learning continues to require active or completed course and class enrollment. ← (verify: unauthorized users cannot infer class draft or class curriculum content)

## 6. Frontend Staff and Public Navigation

- [x] 6.1 Add a Class action to staff/admin course list rows that navigates to class list filtered by course id.
- [x] 6.2 Update staff class list and filters to initialize from `courseId` query parameter and pass it to class API calls.
- [x] 6.3 Add public course detail class action or section that clearly shows available classes and reuses existing enroll/checkout class selection.
- [x] 6.4 Avoid overwriting unrelated existing frontend quiz/course local changes. ← (verify: git diff only includes intended frontend files)

## 7. Frontend Trainer Curriculum Workspace

- [x] 7.1 Add frontend service methods for trainer class curriculum read, draft initialization, draft edits, and publish.
- [x] 7.2 Add class workspace curriculum tab or section showing inherited, draft, and published state.
- [x] 7.3 Add trainer actions for initialize draft, edit/reorder lessons, manage resources, customize inline quiz content, and publish.
- [x] 7.4 Display backend curriculum metadata in trainer and trainee-facing UI where available. ← (verify: frontend does not infer curriculum source without backend metadata)

## 8. Validation

- [x] 8.1 Add or update backend tests for curriculum resolution, trainer ownership, draft isolation, publish behavior, and trainee learning fallback.
- [x] 8.2 Add or update backend tests for class-aware lesson progress.
- [x] 8.3 Run backend compile/test command and fix failures introduced by this change.
- [x] 8.4 Run frontend build/lint command and fix failures introduced by this change.
- [ ] 8.5 Manually verify key flows: staff course-to-class navigation, public class selection, trainer class draft/publish, trainee effective learning content. ← (verify: all spec scenarios are covered by tests or manual verification notes)
