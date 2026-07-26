package com.smartlearnly.backend.rag.repository;

import com.smartlearnly.backend.rag.entity.RagMaterialChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagMaterialChunkRepository extends JpaRepository<RagMaterialChunk, UUID> {
    List<RagMaterialChunk> findBySnapshotIdOrderByChunkIndexAsc(UUID snapshotId);
}
