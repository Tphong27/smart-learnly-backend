package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.VideoAiChapter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoAiChapterRepository extends JpaRepository<VideoAiChapter, UUID> {
}
