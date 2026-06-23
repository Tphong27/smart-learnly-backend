package com.smartlearnly.backend.auth.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.service.AuthService;
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
    private AuthService authService;

    private AuthDevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new AuthDevDataSeeder(userRepository, passwordEncoder, authService);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    }

    @Test
    void runShouldContinueWhenSeedVerificationOtpIsRateLimited() {
        doThrow(new BusinessException(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many verification OTP requests. Please try again later"
        )).when(authService).resendVerification(any(ResendVerificationRequest.class));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        verify(authService).resendVerification(any(ResendVerificationRequest.class));
        verify(userRepository, times(3)).save(any(UserAccount.class));
    }

    @Test
    void runShouldRethrowUnexpectedVerificationOtpError() {
        doThrow(new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "Unexpected verification failure"
        )).when(authService).resendVerification(any(ResendVerificationRequest.class));

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
