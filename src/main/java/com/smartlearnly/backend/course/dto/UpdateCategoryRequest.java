package com.smartlearnly.backend.course.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class UpdateCategoryRequest {
    @Size(max = 150, message = "Category name must not exceed 150 characters")
    private String name;

    @Size(max = 180, message = "Category slug must not exceed 180 characters")
    private String slug;

    private String description;
    private UUID parentId;
    private boolean parentIdProvided;
    @JsonProperty("isActive")
    private Boolean isActive;

    @PositiveOrZero(message = "Sort order must not be negative")
    private Integer sortOrder;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getParentId() {
        return parentId;
    }

    @JsonSetter("parentId")
    public void setParentId(UUID parentId) {
        this.parentId = parentId;
        this.parentIdProvided = true;
    }

    public boolean isParentIdProvided() {
        return parentIdProvided;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean hasAnyField() {
        return name != null
                || slug != null
                || description != null
                || parentIdProvided
                || isActive != null
                || sortOrder != null;
    }
}
