package com.smartlearnly.backend.classroom.schedule.service;

import com.smartlearnly.backend.classroom.schedule.dto.ScheduleResponse;
import com.smartlearnly.backend.classroom.schedule.dto.ScheduleSessionResponse;
import com.smartlearnly.backend.classroom.schedule.repository.ScheduleProjection;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final ClassSessionRepository classSessionRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    // Lấy các phiên học trong tuần chứa ngày được yêu cầu cho học viên hiện tại.
    public ScheduleResponse getMySchedule(LocalDate requestedDate) {
        UserAccount trainee = currentUserService.requireAuthenticatedUser();
        LocalDate referenceDate = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        var sessions = classSessionRepository
                .findTraineeSchedule(trainee.getId(), weekStart, weekEnd)
                .stream()
                .map(session -> new ScheduleSessionResponse(
                        session.getSessionId(),
                        session.getClassId(),
                        session.getCourseId(),
                        session.getCourseTitle(),
                        session.getClassName(),
                        session.getSessionDate(),
                        session.getStartTime(),
                        session.getEndTime(),
                        session.getTrainerId(),
                        session.getTrainerName(),
                        session.getMeetingUrl()))
                .toList();

        return new ScheduleResponse(weekStart, weekEnd, sessions);
    }

    @Transactional(readOnly = true)
    // Lấy lịch tuần cho giảng viên được xác định theo vai trò người gọi.
    public ScheduleResponse getStaffSchedule(LocalDate requestedDate, UUID requestedTrainerId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        UUID effectiveTrainerId = resolveTrainerId(actor, requestedTrainerId);
        LocalDate referenceDate = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        var sessions = classSessionRepository
                .findStaffSchedule(effectiveTrainerId, weekStart, weekEnd)
                .stream()
                .map(session -> new ScheduleSessionResponse(
                        session.getSessionId(),
                        session.getClassId(),
                        session.getCourseId(),
                        session.getCourseTitle(),
                        session.getClassName(),
                        session.getSessionDate(),
                        session.getStartTime(),
                        session.getEndTime(),
                        session.getTrainerId(),
                        session.getTrainerName(),
                        session.getMeetingUrl()))
                .toList();

        return new ScheduleResponse(weekStart, weekEnd, sessions);
    }

    // Chỉ cho Admin/TMO chọn giảng viên khác; Trainer luôn xem lịch của chính mình.
    private UUID resolveTrainerId(UserAccount actor, UUID requestedTrainerId) {
        if ("TRAINER".equalsIgnoreCase(actor.getRole())) {
            return actor.getId();
        }

        if ("TMO".equalsIgnoreCase(actor.getRole())) {
            if (requestedTrainerId == null) {
                return null;
            }

            userRepository
                    .findActiveUserByIdAndRole(requestedTrainerId, "TRAINER", "active")
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.INVALID_TRAINER,
                            "Selected trainer must exist, be active, "
                                    + "and have the TRAINER role"));
            return requestedTrainerId;
        }

        throw new BusinessException(
                ErrorCode.FORBIDDEN,
                "Only trainers and TMO can view staff schedules");
    }
}
