package com.smartlearnly.backend.curriculum.util;

import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;

/**
 * Builder for creating CurriculumLessonResource entities from requests.
 */
public final class LessonResourceBuilder {

    private LessonResourceBuilder() {}

    /**
     * Creates a new lesson resource from a request.
     *
     * @param request the resource request
     * @param fallbackSortOrder the sort order to use if not specified
     * @return the created resource entity
     */
    public static CurriculumLessonResource create(LessonResourceRequest request, int fallbackSortOrder) {
        String url = CurriculumRequestNormalizer.normalizeRequired(request.url(), "Resource URL is required");
        String name = resolveResourceName(request, url, fallbackSortOrder);

        CurriculumRequestNormalizer.validateResourceNameLength(name);

        CurriculumLessonResource resource = new CurriculumLessonResource();
        resource.setUrl(url);
        resource.setObjectPath(CurriculumRequestNormalizer.normalizeNullable(request.objectPath()));
        resource.setName(name);
        resource.setFileSize(request.fileSize());
        resource.setContentType(CurriculumRequestNormalizer.normalizeNullable(request.contentType()));
        resource.setSortOrder(request.sortOrder() == null ? fallbackSortOrder : request.sortOrder());
        return resource;
    }

    /**
     * Calculates the next sort order for a resource.
     *
     * @param existingResources the existing resources
     * @return the next sort order
     */
    public static int nextSortOrder(java.util.List<CurriculumLessonResource> existingResources) {
        return existingResources.stream()
                .map(CurriculumLessonResource::getSortOrder)
                .filter(sortOrder -> sortOrder != null)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private static String resolveResourceName(LessonResourceRequest request, String url, int index) {
        String name = CurriculumRequestNormalizer.normalizeNullable(request.name());
        if (name == null) {
            name = CurriculumRequestNormalizer.normalizeNullable(request.fileName());
        }
        if (name == null) {
            name = CurriculumRequestNormalizer.fileNameFromUrl(url);
        }
        if (name == null) {
            name = "resource-" + (index + 1);
        }
        return name;
    }
}
