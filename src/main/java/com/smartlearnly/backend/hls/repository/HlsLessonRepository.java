package com.smartlearnly.backend.hls.repository;

import com.smartlearnly.backend.hls.entity.HlsLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HlsLessonRepository extends JpaRepository<HlsLesson, UUID> {

    Optional<HlsLesson> findByLessonId(UUID lessonId);

    boolean existsByLessonIdAndHlsStatus(UUID lessonId, String hlsStatus);
}
