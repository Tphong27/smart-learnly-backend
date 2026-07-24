package com.smartlearnly.backend.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class UpdateCourseRequest {
    private UUID categoryId;

    @Size(max = 255, message = "Course title must not exceed 255 characters")
    private String title;

    @Size(max = 280, message = "Course slug must not exceed 280 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Course slug can contain lowercase letters, numbers, and hyphens only")
    private String slug;

    private String shortDescription;
    private String description;
    private String outcomes;
    private String requirements;

    @Size(max = 50, message = "Language must not exceed 50 characters")
    private String language;

    @Size(max = 30, message = "Level must not exceed 30 characters")
    private String level;

    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    private String thumbnailUrl;

    @DecimalMin(value = "0.00", message = "Course price must be greater than or equal to 0")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "Discounted price must be greater than or equal to 0")
    private BigDecimal discountedPrice;

    @JsonProperty("isFree")
    private Boolean free;

    @Pattern(regexp = "(?i)draft|published|inactive", message = "Course status must be draft, published, or inactive")
    private String status;

    private UUID assignedSmeId;

    private boolean categoryIdProvided;
    private boolean titleProvided;
    private boolean slugProvided;
    private boolean shortDescriptionProvided;
    private boolean descriptionProvided;
    private boolean outcomesProvided;
    private boolean requirementsProvided;
    private boolean languageProvided;
    private boolean levelProvided;
    private boolean thumbnailUrlProvided;
    private boolean priceProvided;
    private boolean discountedPriceProvided;
    private boolean freeProvided;
    private boolean statusProvided;
    private boolean assignedSmeIdProvided;

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
        this.slugProvided = true;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
        this.shortDescriptionProvided = true;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    public String getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(String outcomes) {
        this.outcomes = outcomes;
        this.outcomesProvided = true;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
        this.requirementsProvided = true;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
        this.languageProvided = true;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
        this.levelProvided = true;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        this.thumbnailUrlProvided = true;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceProvided = true;
    }

    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public void setDiscountedPrice(BigDecimal discountedPrice) {
        this.discountedPrice = discountedPrice;
        this.discountedPriceProvided = true;
    }

    public Boolean getFree() {
        return free;
    }

    public void setFree(Boolean free) {
        this.free = free;
        this.freeProvided = true;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.statusProvided = true;
    }

    public UUID getAssignedSmeId() {
        return assignedSmeId;
    }

    public void setAssignedSmeId(UUID assignedSmeId) {
        this.assignedSmeId = assignedSmeId;
        this.assignedSmeIdProvided = true;
    }

    public boolean isCategoryIdProvided() {
        return categoryIdProvided;
    }

    public boolean isTitleProvided() {
        return titleProvided;
    }

    public boolean isSlugProvided() {
        return slugProvided;
    }

    public boolean isShortDescriptionProvided() {
        return shortDescriptionProvided;
    }

    public boolean isDescriptionProvided() {
        return descriptionProvided;
    }

    public boolean isOutcomesProvided() {
        return outcomesProvided;
    }

    public boolean isRequirementsProvided() {
        return requirementsProvided;
    }

    public boolean isLanguageProvided() {
        return languageProvided;
    }

    public boolean isLevelProvided() {
        return levelProvided;
    }

    public boolean isThumbnailUrlProvided() {
        return thumbnailUrlProvided;
    }

    public boolean isPriceProvided() {
        return priceProvided;
    }

    public boolean isDiscountedPriceProvided() {
        return discountedPriceProvided;
    }

    public boolean isFreeProvided() {
        return freeProvided;
    }

    public boolean isStatusProvided() {
        return statusProvided;
    }

    public boolean isAssignedSmeIdProvided() {
        return assignedSmeIdProvided;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return categoryIdProvided
                || titleProvided
                || slugProvided
                || shortDescriptionProvided
                || descriptionProvided
                || outcomesProvided
                || requirementsProvided
                || languageProvided
                || levelProvided
                || thumbnailUrlProvided
                || priceProvided
                || discountedPriceProvided
                || freeProvided
                || statusProvided
                || assignedSmeIdProvided;
    }
}
