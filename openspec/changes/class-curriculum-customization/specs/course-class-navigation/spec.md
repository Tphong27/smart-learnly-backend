## ADDED Requirements

### Requirement: Staff course list links to course classes
The frontend SHALL provide a class navigation action from staff course management screens.

#### Scenario: Staff opens classes for a course
- **WHEN** staff clicks the Class action for a course in the staff/admin course list
- **THEN** the frontend SHALL navigate to the class list filtered by that course id.

#### Scenario: Class list initializes from course filter
- **WHEN** the staff class list is opened with a course filter in the URL
- **THEN** the frontend SHALL request classes using that course id and show that the list is filtered by course.

### Requirement: Public course detail exposes class selection clearly
The frontend SHALL make available classes visible and actionable from the public course detail screen.

#### Scenario: Public user views course with classes
- **WHEN** a public user opens a course detail page that includes class options
- **THEN** the frontend SHALL show a clear Class action or section for viewing/selecting available classes.

#### Scenario: Public user chooses a class for enrollment or checkout
- **WHEN** a user selects an available class from the public course detail screen
- **THEN** the existing enrollment or checkout flow SHALL use the selected class id.

### Requirement: Frontend displays curriculum source metadata
The frontend SHALL use curriculum metadata returned by backend APIs to label class curriculum and learning content source.

#### Scenario: Trainer class curriculum shows state
- **WHEN** a trainer opens the class curriculum workspace
- **THEN** the frontend SHALL show whether the class is inherited from master, has a draft, or has a published class customization.

#### Scenario: Trainee learning shows effective source when provided
- **WHEN** learning content includes curriculum source metadata
- **THEN** the frontend SHALL be able to display whether the learner is using master curriculum or a trainer-customized class curriculum.
