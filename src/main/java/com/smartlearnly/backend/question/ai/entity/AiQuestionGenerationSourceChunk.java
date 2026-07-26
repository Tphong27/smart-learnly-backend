package com.smartlearnly.backend.question.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_question_generation_source_chunks", schema = "public")
public class AiQuestionGenerationSourceChunk {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "generation_source_id", nullable = false)
    private UUID generationSourceId;

    @Column(name = "material_chunk_id")
    private UUID materialChunkId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_reference", nullable = false)
    private String chunkReference;

    @Column(name = "content_excerpt", nullable = false, columnDefinition = "TEXT")
    private String contentExcerpt;

    @Column(name = "content_checksum", nullable = false, length = 128)
    private String contentChecksum;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
