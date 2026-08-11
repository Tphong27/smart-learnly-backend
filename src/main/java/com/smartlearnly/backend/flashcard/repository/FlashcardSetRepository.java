package com.smartlearnly.backend.flashcard.repository;

import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardSetRepository extends JpaRepository<FlashcardSet, UUID> {
    Optional<FlashcardSet> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            order by flashcardSet.updatedAt desc, flashcardSet.id desc
            """, countQuery = """
            select count(flashcardSet)
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            """)
    Page<FlashcardSet> findPersonalSetsByOwnerOrderByUpdated(
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );

    @Query(value = """
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
              and lower(flashcardSet.title) like concat('%', lower(:query), '%')
            order by flashcardSet.updatedAt desc, flashcardSet.id desc
            """, countQuery = """
            select count(flashcardSet)
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
              and lower(flashcardSet.title) like concat('%', lower(:query), '%')
            """)
    Page<FlashcardSet> findPersonalSetsByOwnerAndTitleSearchOrderByUpdated(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query(value = """
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            order by lower(flashcardSet.title) asc, flashcardSet.id asc
            """, countQuery = """
            select count(flashcardSet)
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            """)
    Page<FlashcardSet> findPersonalSetsByOwnerOrderByTitle(
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );

    @Query(value = """
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
              and lower(flashcardSet.title) like concat('%', lower(:query), '%')
            order by lower(flashcardSet.title) asc, flashcardSet.id asc
            """, countQuery = """
            select count(flashcardSet)
            from FlashcardSet flashcardSet
            where flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
              and lower(flashcardSet.title) like concat('%', lower(:query), '%')
            """)
    Page<FlashcardSet> findPersonalSetsByOwnerAndTitleSearchOrderByTitle(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.id = :setId
              and flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            """)
    Optional<FlashcardSet> findPersonalByIdAndOwnerId(
            @Param("setId") UUID setId,
            @Param("ownerId") UUID ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.id = :setId
              and flashcardSet.createdBy.id = :ownerId
              and flashcardSet.course is null
              and flashcardSet.lesson is null
              and flashcardSet.curriculumLessonId is null
              and flashcardSet.isPublic = false
              and flashcardSet.isOfficial = false
              and flashcardSet.deletedAt is null
            """)
    Optional<FlashcardSet> findPersonalForUpdateByIdAndOwnerId(
            @Param("setId") UUID setId,
            @Param("ownerId") UUID ownerId
    );

    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.lesson.id = :lessonId
              and flashcardSet.deletedAt is null
            """)
    Optional<FlashcardSet> findByLessonIdAndDeletedAtIsNull(@Param("lessonId") UUID lessonId);

    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.curriculumLessonId = :curriculumLessonId
              and flashcardSet.deletedAt is null
            """)
    Optional<FlashcardSet> findByCurriculumLessonIdAndDeletedAtIsNull(@Param("curriculumLessonId") UUID curriculumLessonId);

    /** Tìm bộ thẻ nguồn trong master đã publish theo identity, không lấy nhầm bản tùy biến của lớp khác. */
    @Query("""
            select flashcardSet
            from FlashcardSet flashcardSet
            where flashcardSet.curriculumLessonId in (
                select curriculumLesson.id
                from CurriculumLesson curriculumLesson
                where curriculumLesson.lessonIdentityId = :lessonIdentityId
                  and curriculumLesson.section.curriculumVersion.scope = :scope
                  and curriculumLesson.section.curriculumVersion.status = :status
            )
              and flashcardSet.deletedAt is null
            order by flashcardSet.updatedAt desc, flashcardSet.id desc
            """)
    List<FlashcardSet> findActiveByLessonIdentityIdAndCurriculumStateOrderByUpdatedAtDesc(
            @Param("lessonIdentityId") UUID lessonIdentityId,
            @Param("scope") CurriculumScope scope,
            @Param("status") CurriculumStatus status
    );

    @Query("""
            select count(flashcardSet) > 0
            from FlashcardSet flashcardSet
            where flashcardSet.lesson.id = :lessonId
              and flashcardSet.deletedAt is null
            """)
    boolean existsByLessonIdAndDeletedAtIsNull(@Param("lessonId") UUID lessonId);

}
