package com.smartlearnly.backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnStructuredBusinessErrorResponse() throws Exception {
        mockMvc.perform(get("/api/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Email already verified"))
                .andExpect(jsonPath("$.path").value("/api/test/business"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnFieldErrorsForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/test/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/test/profile"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"))
                .andExpect(jsonPath("$.errors[0].message").value("Full name is required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnBadRequestForPathVariableTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/test/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.path").value("/api/test/users/not-a-uuid"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value for request parameter"));
    }

    @Controller
    @ResponseBody
    @RequestMapping("/api/test")
    static class TestController {

        @GetMapping("/business")
        String businessError() {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already verified");
        }

        @PostMapping("/profile")
        String validateBody(@Valid @RequestBody UpdateProfileRequest request) {
            return "ok";
        }

        @GetMapping("/users/{id}")
        String pathVariable(@PathVariable UUID id) {
            return id.toString();
        }
    }

    record UpdateProfileRequest(
            @NotBlank(message = "Full name is required")
            String fullName
    ) {
    }
}
