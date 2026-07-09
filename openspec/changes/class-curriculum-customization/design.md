## Context

The backend currently treats course curriculum as course-level data: `CourseSection`, `Lesson`, and `LessonResource` are attached directly to `Course`. `ClassOffering` stores only `courseId`, `trainerId`, schedule, capacity, and status. `LearningContentService` accepts both `courseId` and `classId`, but `classId` is only used for access validation; content is still loaded by `courseId`.

This creates a shared mutable curriculum: any trainer edit using existing course content APIs would change the SME-authored master course and every class derived from that course. The frontend is also forced to reason about ambiguous concepts such as master course, trainer-specific course, and class-specific course.

The design introduces versioned curriculum as a separate concept from the course catalog. A course owns master curriculum versions. A class can either inherit the current master published curriculum or bind to its own class-scoped curriculum version.

## Goals / Non-Goals

**Goals:**

- Keep `Course` as the master catalog entity and avoid creating trainer-specific course records.
- Represent master and class-specific curriculum as explicit versions.
- Allow trainer owners to create and edit a draft curriculum for their assigned class.
- Publish class curriculum so enrolled trainees see the custom version immediately.
- Ensure draft curriculum is never visible to trainees or public users.
- Preserve master course and other classes when a trainer customizes one class.
- Return curriculum metadata so frontend screens do not infer version/scope themselves.
- Add staff and public course-to-class navigation.
- Keep progress class-aware and stable across curriculum row copies.

**Non-Goals:**

- Automatic merge of later SME master updates into already-customized classes.
- Compare/sync UI for master-to-class changes in this phase.
- TMO/SME approval workflow for trainer curriculum publishing.
- Full migration of formal `tests/test_questions` into class-scoped quiz versions.
- Creating separate `courses` rows for trainer or class copies.
- Rebuilding the entire assessment engine.

## Decisions

### Decision 1: Use curriculum versions instead of course clones

The system will introduce `curriculum_versions` with `scope = MASTER | CLASS` and `status = DRAFT | PUBLISHED | ARCHIVED`. `Course` remains catalog metadata. Class custom content is represented by class-scoped curriculum versions, not by cloned courses.

Alternatives considered:

- Clone `Course` per trainer/class: rejected because it pollutes catalog, pricing, slug, enrollment, and analytics semantics.
- Override/inheritance tables only: rejected for first phase because resolving effective order, hidden lessons, resources, and quiz content would be complex and harder to verify.
- Event sourcing: rejected as overkill for the current project.

### Decision 2: Use snapshot-per-version rows for sections, lessons, and resources

Each curriculum version owns copied section, lesson, and resource rows. Class drafts are initialized by copying the effective source version. Trainer edits only the draft rows. Publishing marks the draft as published and updates the class binding.

This favors simple, predictable reads and strong isolation over storage minimization. Lineage fields such as `source_section_id`, `source_lesson_id`, and `source_resource_id` preserve traceability for future compare/sync.

### Decision 3: Add class curriculum binding

A `class_curriculum_bindings` table will store the class's curriculum state:

- `base_master_version_id`
- `draft_version_id`
- `published_version_id`
- `customization_state = INHERITED | DRAFT | PUBLISHED`

Resolution rules:

- Public and master admin views use master published or master draft/published as appropriate.
- A class without `published_version_id` inherits master published curriculum.
- A class with `published_version_id` serves that class version to enrolled trainees.
- Trainer edit views use draft when present; otherwise they can initialize draft from class published or master published.

### Decision 4: Centralize curriculum selection in a resolver service

A `CurriculumResolutionService` will determine the correct version for each context. Controllers and services must not independently choose between master and class curriculum.

Resolution contexts include:

- public master preview/detail
- master admin authoring
- trainer class editing
- trainee learning

The resolver will enforce class ownership and enrollment preconditions through service-level checks.

### Decision 5: Keep inline quiz customization in curriculum lesson content

For this phase, lesson type `QUIZ` uses the curriculum lesson `content` field as the customization target. Existing `QuizContentValidator` behavior should be reused where applicable. Formal `tests/test_questions` deep class-scoping is deferred.

### Decision 6: Introduce stable lesson identity for progress and references

Curriculum lesson rows are physical version rows. A logical `lesson_identity_id` must survive copies between master and class versions. Progress should be keyed by `student_id`, `class_id`, and `lesson_identity_id` rather than only by `student_id` and physical lesson id.

Existing responses may continue to expose physical lesson ids for compatibility, but learning/progress logic must validate against the effective curriculum for the requested class.

### Decision 7: Frontend follows backend-provided metadata

Frontend screens will not infer curriculum scope by route or role. Learning and curriculum APIs will return metadata such as `curriculumVersionId`, `curriculumScope`, `customized`, and `source`.

Staff course lists will add a Class navigation action. Public course detail will expose class selection more clearly while still showing master course content.

## Risks / Trade-offs

- Migration complexity → Add version tables first, backfill master versions from existing course sections/lessons/resources, then switch read paths gradually.
- Legacy `modules` versus `course_sections` drift → Normalize public and learning reads to a single curriculum source during implementation.
- Storage growth from snapshots → Accept for simpler reads and safer isolation; optimize later with deduplication or copy-on-write only if needed.
- Progress migration risk → Preserve existing lesson progress where possible by mapping current lessons to generated lesson identities and adding class context from enrollment.
- Trainer ownership leaks → Enforce `class.trainerId == currentUser.id` in services for every trainer curriculum read/write operation.
- Trainee seeing drafts → Resolver must never return draft versions for learning context; add tests for this rule.
- Formal test engine ambiguity → Keep test engine integration out of scope except for preserving existing behavior and avoiding broken links.
- Frontend repo has existing uncommitted quiz/course edits → Implementation must read before editing and avoid overwriting unrelated local changes.

## Migration Plan

1. Add database migrations for curriculum versioning and class bindings.
2. Backfill one published master curriculum version per existing course from existing `course_sections`, `lessons`, and `lesson_resources`.
3. Assign `lesson_identity_id` values for backfilled master lessons.
4. Add resolver and read APIs while preserving current endpoint compatibility.
5. Update trainee learning content to load by resolved version and include metadata.
6. Add trainer class curriculum draft/edit/publish APIs.
7. Update progress schema and services to include class-aware lesson identity.
8. Update frontend navigation and class curriculum workspace.
9. Run backend and frontend validation.

Rollback strategy:

- The first deployment should keep old master tables intact until the new read path is verified.
- If versioned reads fail before trainer customization is used, revert services to old course-section reads and leave new tables unused.
- Once trainer class versions are published, rollback requires preserving class version data or disabling class curriculum resolution while retaining table data for recovery.

## Open Questions

- Whether existing production data contains active legacy `modules` rows that still need to be mirrored or migrated during public course detail normalization.
- Whether future phases will require TMO approval before trainer-published curriculum becomes visible.
- Whether formal `tests/test_questions` should eventually replace inline lesson quiz content or coexist with it under class-scoped rules.
