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
    private String provider = "supabase"; // "supabase" or "r2"

    // Supabase configuration
    private String supabaseUrl;
    private String supabaseServiceRoleKey;

    // Cloudflare R2 configuration
    private String r2AccountId;
    private String r2Endpoint;
    private String r2Region = "auto";
    private String r2AccessKeyId;
    private String r2SecretAccessKey;
    private String r2PublicUrl;
    private String r2CourseThumbnailPublicUrl;
    private String r2LessonMaterialPublicUrl;
    private String r2LessonResourcePublicUrl;
    private String r2QuestionMediaPublicUrl;

    private String courseThumbnailBucket = "course-thumbnails";
    private DataSize courseThumbnailMaxSize = DataSize.ofMegabytes(5);
    private String lessonMaterialBucket = "lesson-materials";
    private DataSize lessonMaterialMaxSize = DataSize.ofMegabytes(500);
    private String lessonResourceBucket = "lesson-resources";
    private DataSize lessonResourceMaxSize = DataSize.ofMegabytes(20);
    private String questionMediaBucket = "question-media";
    private DataSize questionImageMaxSize = DataSize.ofMegabytes(5);
    private DataSize questionAudioMaxSize = DataSize.ofMegabytes(20);
    private DataSize questionVideoMaxSize = DataSize.ofMegabytes(100);
}

