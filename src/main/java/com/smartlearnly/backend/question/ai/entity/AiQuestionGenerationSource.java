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
@Table(name = "ai_question_generation_sources", schema = "public")
public class AiQuestionGenerationSource {
    public static final String KIND_MATERIAL = "material";
    public static final String KIND_PASTED_TEXT = "pasted_text";
    public static final String KIND_TEMPORARY_FILE = "temporary_file";
    public static final String KIND_TRANSCRIPT = "transcript";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "source_kind", nullable = false, length = 32)
    private String sourceKind;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "material_snapshot_id")
    private UUID materialSnapshotId;

    @Column(name = "source_payload_ref", columnDefinition = "TEXT")
    private String sourcePayloadRef;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "normalized_char_count")
    private Integer normalizedCharCount;

    @Column(name = "transcript_content_id")
    private UUID transcriptContentId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "downloadable", nullable = false)
    private Boolean downloadable;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "source_checksum", nullable = false, length = 128)
    private String sourceChecksum;

    @Column(name = "source_version", nullable = false, length = 64)
    private String sourceVersion;

    @Column(name = "rag_status", nullable = false, length = 32)
    private String ragStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (sourceKind == null) sourceKind = KIND_MATERIAL;
        if (downloadable == null) downloadable = false;
        if (createdAt == null) createdAt = Instant.now();
    }
}
