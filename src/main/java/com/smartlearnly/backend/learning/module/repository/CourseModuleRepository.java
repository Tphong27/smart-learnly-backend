package com.smartlearnly.backend.learning.module.repository;

import com.smartlearnly.backend.learning.module.entity.CourseModule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    Optional<CourseModule> findByIdAndCourseId(UUID id, UUID courseId);

    boolean existsByIdAndCourseIdAndSystemFalseAndStatus(
            UUID id,
            UUID courseId,
            String status
    );

    /**
     * Đổi ID snapshot curriculum section cũ sang ID module chuẩn đang hoạt động.
     * Query này giúp các URL đã mở trước khi frontend sửa ID vẫn tiếp tục hoạt động an toàn.
     */
    @Query("""
            select module.id
            from CourseModule module
            where module.courseId = :courseId
              and module.system = false
              and module.status = :status
              and module.id = (
                  select section.sourceModuleId
                  from CurriculumSection section
                  where section.id = :sectionId
                    and section.curriculumVersion.courseId = :courseId
              )
            """)
    Optional<UUID> findActiveModuleIdByCourseIdAndSectionId(
            @Param("courseId") UUID courseId,
            @Param("sectionId") UUID sectionId,
            @Param("status") String status
    );
}
