package com.smartlearnly.backend.assignment.repository;

import com.smartlearnly.backend.assignment.entity.Assignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findByClassId(UUID classId);

}