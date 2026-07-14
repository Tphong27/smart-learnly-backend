## ADDED Requirements

### Requirement: Curriculum versions separate catalog courses from learning structure
The system SHALL represent learning structure with curriculum versions instead of treating trainer or class customization as separate course records.

#### Scenario: Master course retains catalog identity
- **WHEN** a course is displayed in public catalog, admin course management, or checkout
- **THEN** the system SHALL use the original course record for catalog metadata and SHALL NOT create or expose trainer-specific course records.

#### Scenario: Curriculum content belongs to a version
- **WHEN** sections, lessons, resources, or inline quiz content are read or written after this change
- **THEN** the system SHALL associate them with a curriculum version that has a scope of `MASTER` or `CLASS`.

### Requirement: Master curriculum is globally visible
The system SHALL keep the SME-authored master curriculum visible to public users, staff master previews, and classes that have not published a class-specific curriculum.

#### Scenario: Public course detail uses master curriculum
- **WHEN** a user opens a public course detail or preview screen without a class learning context
- **THEN** the system SHALL return the course's published master curriculum.

#### Scenario: Inherited class uses master curriculum
- **WHEN** a trainee learns in a class that has no published class curriculum
- **THEN** the system SHALL return the published master curriculum as the effective curriculum for that class.

### Requirement: Class curriculum binding determines effective class content
The system SHALL store a binding for each class that can reference the master base version, an active class draft version, and an active class published version.

#### Scenario: Class has no custom curriculum
- **WHEN** a class curriculum binding has no published class version
- **THEN** the system SHALL resolve trainee learning content to the current published master curriculum.

#### Scenario: Class has custom published curriculum
- **WHEN** a class curriculum binding has a published class version
- **THEN** the system SHALL resolve trainee learning content to that class published version.

#### Scenario: Draft is isolated from learning
- **WHEN** a class curriculum binding has a draft version
- **THEN** the system SHALL expose the draft only to authorized trainer editing flows and SHALL NOT expose it to trainee learning flows.

### Requirement: Curriculum resolution returns explicit metadata
The system SHALL return curriculum metadata to clients for learning and curriculum editor responses.

#### Scenario: Learning response identifies source
- **WHEN** learning content is returned for a class
- **THEN** the response SHALL include curriculum version id, curriculum scope, class id, whether it is customized, and a source value indicating master inherited or class published.

#### Scenario: Frontend does not infer version
- **WHEN** the frontend renders learning or class curriculum screens
- **THEN** it SHALL use backend-provided curriculum metadata instead of inferring curriculum source from route or role.
