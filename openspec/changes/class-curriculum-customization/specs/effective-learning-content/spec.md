## ADDED Requirements

### Requirement: Trainee learning uses class effective published curriculum
The system SHALL serve learning content from the class effective published curriculum after validating course and class enrollment.

#### Scenario: Enrolled trainee in inherited class
- **WHEN** an enrolled trainee requests learning content for a class with no published class curriculum
- **THEN** the system SHALL return the published master curriculum and mark the source as master inherited.

#### Scenario: Enrolled trainee in customized class
- **WHEN** an enrolled trainee requests learning content for a class with a published class curriculum
- **THEN** the system SHALL return that class published curriculum and mark the source as class published.

#### Scenario: Trainee cannot see draft
- **WHEN** a class has an active draft curriculum but no published class curriculum
- **THEN** the trainee learning response SHALL continue to use master inherited curriculum.

#### Scenario: Unenrolled user cannot access class content
- **WHEN** a user requests learning content for a class where they do not have active or completed class enrollment
- **THEN** the system SHALL reject the request and SHALL NOT reveal class curriculum content.

### Requirement: Progress is class-aware and version-stable
The system SHALL record lesson progress in a way that is scoped to the class and stable across curriculum version copies.

#### Scenario: Progress is recorded for effective class lesson identity
- **WHEN** a trainee marks a lesson complete in a class learning context
- **THEN** the system SHALL store progress against the student, class, course, and logical lesson identity for the effective curriculum lesson.

#### Scenario: Progress does not leak between classes
- **WHEN** the same trainee is enrolled in two classes that derive from the same course
- **THEN** progress recorded in one class SHALL NOT mark the same lesson complete in the other class unless explicitly recorded there.

#### Scenario: Progress survives class curriculum copy
- **WHEN** a class curriculum is copied from a source version and preserves lesson identities
- **THEN** existing progress for matching lesson identities SHALL remain associated with the trainee's class context.

### Requirement: Assignments and tests remain compatible with class curriculum phase one
The system SHALL preserve existing assignment and test behavior while avoiding unsafe assumptions about class curriculum lesson references.

#### Scenario: Assignment remains class-scoped
- **WHEN** assignments are listed for staff or trainees
- **THEN** existing class-based assignment visibility SHALL continue to work.

#### Scenario: Lesson reference is validated when class context is available
- **WHEN** an assignment or related learning action references a lesson in a class context
- **THEN** the system SHALL validate that the lesson belongs to the effective curriculum for that class or maps to its logical lesson identity.

#### Scenario: Formal test engine customization is out of phase one
- **WHEN** a trainer customizes an inline quiz lesson in class curriculum
- **THEN** the system SHALL store the customization in curriculum lesson content and SHALL NOT require cloning formal `tests` or `test_questions` records in this phase.
