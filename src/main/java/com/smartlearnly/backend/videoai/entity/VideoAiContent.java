package com.smartlearnly.backend.videoai.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_ai_contents", schema = "public")
public class VideoAiContent {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;
    @Column(name = "lesson_scope", nullable = false, length = 16)
    private String lessonScope;
    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Column(name = "class_id")
    private UUID classId;
    @Column(name = "source_version", nullable = false)
    private UUID sourceVersion;
    @Column(length = 16)
    private String language;
    @Column(name = "transcript_text", columnDefinition = "text")
    private String transcriptText;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "key_points", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String keyPointsJson = "[]";
    @Column(nullable = false, length = 16)
    private String status = "draft";
    @Version
    @Column(nullable = false)
    private Long revision = 0L;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "published_by")
    private UUID publishedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("segmentIndex ASC")
    private List<VideoAiTranscriptSegment> segments = new ArrayList<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterIndex ASC")
    private List<VideoAiChapter> chapters = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = "draft";
        if (keyPointsJson == null) keyPointsJson = "[]";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceSegments(List<VideoAiTranscriptSegment> values) {
        segments.clear();
        if (values != null) values.forEach(this::addSegment);
    }

    public void addSegment(VideoAiTranscriptSegment value) {
        value.setContent(this);
        segments.add(value);
    }

    public void replaceChapters(List<VideoAiChapter> values) {
        chapters.clear();
        if (values != null) values.forEach(this::addChapter);
    }

    public void addChapter(VideoAiChapter value) {
        value.setContent(this);
        chapters.add(value);
    }
}
