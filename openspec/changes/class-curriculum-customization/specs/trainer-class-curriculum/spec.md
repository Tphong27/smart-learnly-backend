## ADDED Requirements

### Requirement: Trainer can initialize a class curriculum draft
The system SHALL allow only the trainer assigned to a class to initialize a draft curriculum for that class.

#### Scenario: Trainer initializes draft for inherited class
- **WHEN** the assigned trainer requests draft initialization for a class that inherits master curriculum
- **THEN** the system SHALL create a class-scoped draft version by copying the current effective master curriculum.

#### Scenario: Trainer initializes draft for previously customized class
- **WHEN** the assigned trainer requests draft initialization for a class that already has a published class curriculum and no active draft
- **THEN** the system SHALL create a draft by copying the class published curriculum.

#### Scenario: Non-owner trainer cannot initialize draft
- **WHEN** a trainer who is not assigned to the class requests draft initialization
- **THEN** the system SHALL reject the request with a forbidden error and SHALL NOT create a draft.

### Requirement: Trainer can edit only class draft curriculum
The system SHALL allow trainer curriculum edits only against the active draft version of the trainer's assigned class.

#### Scenario: Trainer edits draft lessons and sections
- **WHEN** the assigned trainer creates, updates, deletes, or reorders sections or lessons in a class curriculum
- **THEN** the system SHALL mutate only the class draft version and SHALL NOT mutate the master curriculum or other class curriculum versions.

#### Scenario: Trainer edits draft resources
- **WHEN** the assigned trainer adds, removes, or reorders learning resources in a class curriculum lesson
- **THEN** the system SHALL mutate only resources attached to the class draft lesson.

#### Scenario: Trainer customizes inline quiz content
- **WHEN** the assigned trainer updates a draft lesson with lesson type `QUIZ`
- **THEN** the system SHALL validate and store inline quiz content on the draft curriculum lesson.

#### Scenario: Edit without draft is rejected
- **WHEN** the assigned trainer attempts to mutate class curriculum and no draft version exists
- **THEN** the system SHALL reject the mutation and require draft initialization first.

### Requirement: Trainer can publish class curriculum draft
The system SHALL allow the assigned trainer to publish a class draft so enrolled trainees immediately receive the class-specific curriculum.

#### Scenario: Publish draft
- **WHEN** the assigned trainer publishes a valid draft curriculum
- **THEN** the system SHALL mark the draft as published, update the class binding published version, clear the active draft pointer, and make the class version effective for enrolled trainees immediately.

#### Scenario: Published class curriculum does not affect master
- **WHEN** a trainer publishes a class curriculum
- **THEN** the system SHALL leave the master curriculum and all other class curriculum bindings unchanged.

#### Scenario: Draft is no longer editable after publish
- **WHEN** a draft has been published
- **THEN** the system SHALL prevent further mutation of that published version through draft-edit endpoints.

### Requirement: Trainer class curriculum APIs enforce ownership
The system SHALL enforce resource-level class ownership checks for every trainer curriculum read and write operation.

#### Scenario: Trainer reads assigned class curriculum
- **WHEN** the assigned trainer requests a class curriculum editor view
- **THEN** the system SHALL return the class curriculum state and effective editor content.

#### Scenario: Trainer reads another trainer's class curriculum
- **WHEN** a trainer requests curriculum data for a class assigned to another trainer
- **THEN** the system SHALL reject the request with a forbidden error.
