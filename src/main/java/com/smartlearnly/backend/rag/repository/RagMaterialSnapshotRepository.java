package com.smartlearnly.backend.rag.repository;

import com.smartlearnly.backend.rag.entity.RagMaterialSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RagMaterialSnapshotRepository extends JpaRepository<RagMaterialSnapshot, UUID> {
    @Query("""
            SELECT snapshot
            FROM RagMaterialSnapshot snapshot
            WHERE snapshot.courseId = :courseId
              AND LOWER(snapshot.status) = 'ready'
            ORDER BY snapshot.updatedAt DESC
            """)
    List<RagMaterialSnapshot> findReadyByCourseId(@Param("courseId") UUID courseId);
}
