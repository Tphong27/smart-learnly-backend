package com.smartlearnly.backend.videoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_ai_chapters", schema = "public")
public class VideoAiChapter {
    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private VideoAiContent content;
    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;
    @Column(name = "start_ms", nullable = false)
    private Long startMs;
    @Column(name = "end_ms", nullable = false)
    private Long endMs;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "text")
    private String summary;
}
