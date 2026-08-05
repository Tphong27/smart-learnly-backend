package com.smartlearnly.backend.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import jakarta.persistence.Column;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the reference + delta composition migration contract: the new
 * class_curriculum_entries table must exist with the columns the entity maps and the
 * indexes/backfill the composition model depends on.
 */
class ClassCurriculumCompositionMigrationContractTest {
        private static final Path MIGRATION = Path
                        .of("src/main/resources/db/migration/V86__class_curriculum_composition.sql");
        private static final Path VERSION_MIGRATION = Path
                        .of("src/main/resources/db/migration/V87__class_curriculum_entry_optimistic_version.sql");

        @Test
        void migrationShouldDefineCompositionTable() throws Exception {
                String sql = Files.readString(MIGRATION);

                assertThat(sql).contains("CREATE TABLE IF NOT EXISTS public.class_curriculum_entries");
                assertThat(sql).contains("class_version_id uuid NOT NULL REFERENCES public.curriculum_versions(id) ON DELETE CASCADE");
                assertThat(sql).contains("section_id uuid NOT NULL REFERENCES public.curriculum_sections(id) ON DELETE CASCADE");
                assertThat(sql).contains("source_curriculum_lesson_id uuid");
                assertThat(sql).contains("lesson_identity_id uuid NOT NULL");
                assertThat(sql).contains("sort_order integer NOT NULL DEFAULT 0");
                assertThat(sql).contains("materialized_lesson_id uuid REFERENCES public.curriculum_lessons(id) ON DELETE SET NULL");
                assertThat(sql).contains("idx_class_curriculum_entries_version_section_sort");
                assertThat(sql).contains("uq_class_curriculum_entries_version_source");
                assertThat(sql).contains("idx_class_curriculum_entries_source_lesson");
                assertThat(sql).contains("idx_class_curriculum_entries_identity");
        }

        @Test
        void optimisticVersionMigrationShouldAddVersionColumnAndIdentityIndex() throws Exception {
                String sql = Files.readString(VERSION_MIGRATION);

                assertThat(sql).contains("ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0");
                assertThat(sql).contains("uq_class_curriculum_entries_version_identity");
        }

        @Test
        void migrationShouldBackfillExistingClassVersionsAndGuardPins() throws Exception {
                String sql = Files.readString(MIGRATION);

                assertThat(sql).contains("INSERT INTO public.class_curriculum_entries");
                assertThat(sql).contains("curriculum_lessons");
                assertThat(sql).contains("lesson_identity_id");
                assertThat(sql).contains("flashcard_sets");
                assertThat(sql).contains("flashcard_staging_batches");
                assertThat(sql).contains("video_ai_contents");
                assertThat(sql).contains("assignments");
                assertThat(sql).contains("RAISE EXCEPTION");
        }

        @Test
        void entityShouldMapRequiredColumns() throws Exception {
                assertThat(ClassCurriculumEntry.class.getDeclaredField("classVersionId")
                                .getAnnotation(Column.class).name()).isEqualTo("class_version_id");
                assertThat(ClassCurriculumEntry.class.getDeclaredField("sourceCurriculumLessonId")
                                .getAnnotation(Column.class).name()).isEqualTo("source_curriculum_lesson_id");
                assertThat(ClassCurriculumEntry.class.getDeclaredField("lessonIdentityId")
                                .getAnnotation(Column.class).name()).isEqualTo("lesson_identity_id");
                assertThat(ClassCurriculumEntry.class.getDeclaredField("materializedLessonId")
                                .getAnnotation(Column.class).name()).isEqualTo("materialized_lesson_id");
                assertThat(ClassCurriculumEntry.class.getDeclaredField("deletedAt")
                                .getAnnotation(Column.class).name()).isEqualTo("deleted_at");
        }
}
