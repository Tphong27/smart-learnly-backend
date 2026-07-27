package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassLifecycleSynchronizationService {

    private final ClassOfferingRepository classOfferingRepository;

    @Transactional
    public int synchronizeStatuses() {
        return classOfferingRepository.synchronizeLifecycleStatuses(ClassLifecycle.today());
    }
}