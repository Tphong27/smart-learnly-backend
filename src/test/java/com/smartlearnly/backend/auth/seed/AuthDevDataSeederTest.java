package com.smartlearnly.backend.auth.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.service.AuthPasswordService;
import com.smartlearnly.backend.auth.registration.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.registration.service.AuthRegistrationService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthDevDataSeederTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthPasswordService passwordService;
    @Mock
    private AuthRegistrationService registrationService;

    private AuthDevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new AuthDevDataSeeder(userRepository, passwordEncoder, passwordService, registrationService);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    }

    @Test
    void runShouldContinueWhenSeedVerificationOtpIsRateLimited() {
        doThrow(new BusinessException(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many verification OTP requests. Please try again later"
        )).when(registrationService).resendVerification(any(ResendVerificationRequest.class));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        verify(passwordService).forgotPassword(any(ForgotPasswordRequest.class));
        verify(registrationService).resendVerification(any(ResendVerificationRequest.class));
        verify(userRepository, times(3)).save(any(UserAccount.class));
    }

    @Test
    void runShouldRethrowUnexpectedVerificationOtpError() {
        doThrow(new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "Unexpected verification failure"
        )).when(registrationService).resendVerification(any(ResendVerificationRequest.class));

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
