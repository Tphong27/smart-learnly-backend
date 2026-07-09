package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import java.util.UUID;

public record CurriculumResolution(
        CurriculumVersion version,
        ClassCurriculumBinding binding,
        UUID classId,
        boolean customized,
        String source
) {
}
