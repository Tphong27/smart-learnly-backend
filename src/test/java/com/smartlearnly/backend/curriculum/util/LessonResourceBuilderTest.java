package com.smartlearnly.backend.curriculum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonResourceBuilderTest {

    @Test
    void create_withUrl_createsResource() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/file.pdf",
                null, null, null, null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getUrl()).isEqualTo("https://example.com/file.pdf");
        assertThat(result.getSortOrder()).isEqualTo(0);
    }

    @Test
    void create_withName_usesProvidedName() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/file.pdf",
                null,
                "Custom Name",
                null,
                null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getName()).isEqualTo("Custom Name");
    }

    @Test
    void create_withFileName_usesProvidedFileName() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/file.pdf",
                null, null,
                "MyFile.pdf",
                null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getName()).isEqualTo("MyFile.pdf");
    }

    @Test
    void create_nameTakesPrecedenceOverFileName() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/file.pdf",
                null,
                "Custom Name",
                "FileName.pdf",
                null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getName()).isEqualTo("Custom Name");
    }

    @Test
    void create_extractsFilenameFromUrl() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/path/to/document.pdf",
                null, null, null, null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getName()).isEqualTo("document.pdf");
    }

    @Test
    void create_withGeneratedName() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/noextension",
                null, null, null, null, null, null
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 2);

        assertThat(result.getName()).isEqualTo("noextension");
    }

    @Test
    void create_withAllOptionalFields() {
        LessonResourceRequest request = new LessonResourceRequest(
                "https://example.com/file.pdf",
                "/storage/file.pdf",
                "Custom Name",
                "file.pdf",
                1024L,
                "application/pdf",
                5
        );

        CurriculumLessonResource result = LessonResourceBuilder.create(request, 0);

        assertThat(result.getUrl()).isEqualTo("https://example.com/file.pdf");
        assertThat(result.getName()).isEqualTo("Custom Name");
        assertThat(result.getObjectPath()).isEqualTo("/storage/file.pdf");
        assertThat(result.getFileSize()).isEqualTo(1024L);
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getSortOrder()).isEqualTo(5);
    }

    @Test
    void create_nullUrl_throwsException() {
        LessonResourceRequest request = new LessonResourceRequest(
                null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> LessonResourceBuilder.create(request, 0))
                .hasMessageContaining("Resource URL is required");
    }

    @Test
    void create_blankUrl_throwsException() {
        LessonResourceRequest request = new LessonResourceRequest(
                "   ", null, null, null, null, null, null
        );

        assertThatThrownBy(() -> LessonResourceBuilder.create(request, 0))
                .hasMessageContaining("Resource URL is required");
    }

    @Test
    void nextSortOrder_emptyList_returnsZero() {
        int result = LessonResourceBuilder.nextSortOrder(List.of());
        assertThat(result).isEqualTo(0);
    }

    @Test
    void nextSortOrder_nullSortOrders_ignoresNulls() {
        CurriculumLessonResource r1 = new CurriculumLessonResource();
        r1.setSortOrder(null);
        CurriculumLessonResource r2 = new CurriculumLessonResource();
        r2.setSortOrder(null);

        int result = LessonResourceBuilder.nextSortOrder(List.of(r1, r2));

        assertThat(result).isEqualTo(0);
    }

    @Test
    void nextSortOrder_withSortOrders_returnsNext() {
        CurriculumLessonResource r1 = new CurriculumLessonResource();
        r1.setSortOrder(0);
        CurriculumLessonResource r2 = new CurriculumLessonResource();
        r2.setSortOrder(1);
        CurriculumLessonResource r3 = new CurriculumLessonResource();
        r3.setSortOrder(3);

        int result = LessonResourceBuilder.nextSortOrder(List.of(r1, r2, r3));

        assertThat(result).isEqualTo(4);
    }
}
