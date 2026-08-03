package com.smartlearnly.backend.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminUserRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createShouldAcceptSupportedVietnameseMobileFormatsAndBlankPhone() {
        List<String> supportedPhones = List.of("0901234567", "+84901234567", "");

        for (String phone : supportedPhones) {
            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    "User Name",
                    "user@example.com",
                    phone,
                    "TRAINEE",
                    "active",
                    true
            );

            assertThat(phoneViolations(request)).as("phone %s", phone).isEmpty();
        }
    }

    @Test
    void createShouldRejectInvalidVietnameseMobilePhone() {
        CreateAdminUserRequest request = new CreateAdminUserRequest(
                "User Name",
                "user@example.com",
                "098123123213",
                "TRAINEE",
                "active",
                true
        );

        assertThat(phoneViolations(request))
                .containsExactly("Phone number must be a valid Vietnamese mobile number, "
                        + "for example 0901234567 or +84901234567");
    }

    @Test
    void updateShouldRejectLettersAndUnsupportedPrefixes() {
        for (String phone : List.of("09012abc67", "0123456789", "+84123456789")) {
            UpdateAdminUserRequest request = new UpdateAdminUserRequest(
                    null,
                    null,
                    phone,
                    null,
                    null,
                    null
            );

            assertThat(phoneViolations(request)).as("phone %s", phone).isNotEmpty();
        }
    }

    private List<String> phoneViolations(Object request) {
        return validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("phoneNumber"))
                .map(violation -> violation.getMessage())
                .toList();
    }
}
