
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.Test;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepository
        extends JpaRepository<Test, UUID> {

    List<Test> findByCourseId(UUID courseId);

    List<Test> findByClassId(UUID classId);

    List<Test> findByModuleId(UUID moduleId);

    List<Test> findByCreatedBy(UUID createdBy);
}

