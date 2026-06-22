package com.smartlearnly.backend.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String supabaseUrl;
    private String supabaseServiceRoleKey;
    private String courseThumbnailBucket = "course-thumbnails";
    private DataSize courseThumbnailMaxSize = DataSize.ofMegabytes(5);
    private String lessonMaterialBucket = "lesson-materials";
    private DataSize lessonMaterialMaxSize = DataSize.ofMegabytes(50);
    private String lessonResourceBucket = "lesson-resources";
    private DataSize lessonResourceMaxSize = DataSize.ofMegabytes(20);
}
