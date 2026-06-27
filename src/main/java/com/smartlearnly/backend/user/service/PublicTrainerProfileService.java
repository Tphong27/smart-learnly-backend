package com.smartlearnly.backend.user.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.dto.PublicTrainerProfileResponse;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicTrainerProfileService {
    private static final String TRAINER_ROLE = "TRAINER";
    private static final String ACTIVE_STATUS = "active";

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PublicTrainerProfileResponse getPublicTrainerProfile(UUID trainerId) {
        UserAccount trainer = userRepository
                .findByIdAndRoleIgnoreCaseAndStatusIgnoreCaseAndDeletedAtIsNull(
                        trainerId,
                        TRAINER_ROLE,
                        ACTIVE_STATUS
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Trainer was not found"
                ));

        return new PublicTrainerProfileResponse(
                trainer.getId(),
                trainer.getFullName(),
                trainer.getEmail(),
                trainer.getAvatarUrl(),
                trainer.getBio(),
                trainer.getRole(),
                trainer.getStatus()
        );
    }
}