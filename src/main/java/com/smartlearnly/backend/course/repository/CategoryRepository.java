package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Category;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
