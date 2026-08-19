package com.smartlearnly.backend.course.authoring.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCourseRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createShouldAcceptTitleCategoryAndAssignedSme() {
        CreateCourseRequest request = minimalRequest(null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createShouldAcceptDraftStatusForBackwardCompatibility() {
        CreateCourseRequest request = minimalRequest("draft");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createShouldRejectNonDraftStatus() {
        CreateCourseRequest request = minimalRequest("published");

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("status");
                    assertThat(violation.getMessage()).isEqualTo("New courses must be created as draft");
                });
    }

    @Test
    void createShouldRejectMissingAssignedSme() {
        CreateCourseRequest request = new CreateCourseRequest(
                UUID.randomUUID(),
                "Course title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("assignedSmeId");
                    assertThat(violation.getMessage()).isEqualTo("Assigned SME is required");
                });
    }

    private CreateCourseRequest minimalRequest(String status) {
        return new CreateCourseRequest(
                UUID.randomUUID(),
                "Course title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                UUID.randomUUID());
    }
}