package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.TraineeScheduleResponse;
import com.smartlearnly.backend.classroom.dto.TraineeScheduleSessionResponse;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraineeScheduleService {

    private final ClassSessionRepository classSessionRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public TraineeScheduleResponse getMySchedule(LocalDate requestedDate) {
        UserAccount trainee = currentUserService.requireAuthenticatedUser();
        LocalDate referenceDate = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        var sessions = classSessionRepository
                .findTraineeSchedule(trainee.getId(), weekStart, weekEnd)
                .stream()
                .map(session -> new TraineeScheduleSessionResponse(
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

        return new TraineeScheduleResponse(weekStart, weekEnd, sessions);
    }
}