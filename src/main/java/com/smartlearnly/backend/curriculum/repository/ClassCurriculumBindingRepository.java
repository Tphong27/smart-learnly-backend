package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassCurriculumBindingRepository extends JpaRepository<ClassCurriculumBinding, UUID> {
    Optional<ClassCurriculumBinding> findByClassId(UUID classId);

    Optional<ClassCurriculumBinding> findByClassIdAndCourseId(UUID classId, UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from ClassCurriculumBinding binding where binding.classId = :classId")
    Optional<ClassCurriculumBinding> findByClassIdForUpdate(@Param("classId") UUID classId);
}
