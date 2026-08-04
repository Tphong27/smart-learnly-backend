package com.smartlearnly.backend.course.authoring.dto;

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

    // Trả categoryId được gửi cho thao tác PATCH.
    public UUID getCategoryId() {
        return categoryId;
    }

    // Ghi categoryId và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    // Trả tiêu đề được gửi cho thao tác PATCH.
    public String getTitle() {
        return title;
    }

    // Ghi tiêu đề và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    // Trả slug được gửi cho thao tác PATCH.
    public String getSlug() {
        return slug;
    }

    // Ghi slug và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setSlug(String slug) {
        this.slug = slug;
        this.slugProvided = true;
    }

    // Trả mô tả ngắn được gửi cho thao tác PATCH.
    public String getShortDescription() {
        return shortDescription;
    }

    // Ghi mô tả ngắn và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
        this.shortDescriptionProvided = true;
    }

    // Trả mô tả chi tiết được gửi cho thao tác PATCH.
    public String getDescription() {
        return description;
    }

    // Ghi mô tả chi tiết và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    // Trả kết quả đầu ra được gửi cho thao tác PATCH.
    public String getOutcomes() {
        return outcomes;
    }

    // Ghi kết quả đầu ra và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setOutcomes(String outcomes) {
        this.outcomes = outcomes;
        this.outcomesProvided = true;
    }

    // Trả điều kiện đầu vào được gửi cho thao tác PATCH.
    public String getRequirements() {
        return requirements;
    }

    // Ghi điều kiện đầu vào và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setRequirements(String requirements) {
        this.requirements = requirements;
        this.requirementsProvided = true;
    }

    // Trả ngôn ngữ được gửi cho thao tác PATCH.
    public String getLanguage() {
        return language;
    }

    // Ghi ngôn ngữ và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setLanguage(String language) {
        this.language = language;
        this.languageProvided = true;
    }

    // Trả cấp độ được gửi cho thao tác PATCH.
    public String getLevel() {
        return level;
    }

    // Ghi cấp độ và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setLevel(String level) {
        this.level = level;
        this.levelProvided = true;
    }

    // Trả URL ảnh đại diện được gửi cho thao tác PATCH.
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    // Ghi URL ảnh đại diện và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        this.thumbnailUrlProvided = true;
    }

    // Trả giá gốc được gửi cho thao tác PATCH.
    public BigDecimal getPrice() {
        return price;
    }

    // Ghi giá gốc và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceProvided = true;
    }

    // Trả giá giảm được gửi cho thao tác PATCH.
    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    // Ghi giá giảm và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setDiscountedPrice(BigDecimal discountedPrice) {
        this.discountedPrice = discountedPrice;
        this.discountedPriceProvided = true;
    }

    // Trả cờ khóa học miễn phí được gửi cho thao tác PATCH.
    public Boolean getFree() {
        return free;
    }

    // Ghi cờ miễn phí và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setFree(Boolean free) {
        this.free = free;
        this.freeProvided = true;
    }

    // Trả trạng thái được gửi cho thao tác PATCH.
    public String getStatus() {
        return status;
    }

    // Ghi trạng thái và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setStatus(String status) {
        this.status = status;
        this.statusProvided = true;
    }

    // Trả SME được phân công trong thao tác PATCH.
    public UUID getAssignedSmeId() {
        return assignedSmeId;
    }

    // Ghi SME được phân công và đánh dấu trường này đã xuất hiện trong JSON PATCH.
    public void setAssignedSmeId(UUID assignedSmeId) {
        this.assignedSmeId = assignedSmeId;
        this.assignedSmeIdProvided = true;
    }

    // Cho biết categoryId có xuất hiện trong JSON PATCH hay không.
    public boolean isCategoryIdProvided() {
        return categoryIdProvided;
    }

    // Cho biết title có xuất hiện trong JSON PATCH hay không.
    public boolean isTitleProvided() {
        return titleProvided;
    }

    // Cho biết slug có xuất hiện trong JSON PATCH hay không.
    public boolean isSlugProvided() {
        return slugProvided;
    }

    // Cho biết shortDescription có xuất hiện trong JSON PATCH hay không.
    public boolean isShortDescriptionProvided() {
        return shortDescriptionProvided;
    }

    // Cho biết description có xuất hiện trong JSON PATCH hay không.
    public boolean isDescriptionProvided() {
        return descriptionProvided;
    }

    // Cho biết outcomes có xuất hiện trong JSON PATCH hay không.
    public boolean isOutcomesProvided() {
        return outcomesProvided;
    }

    // Cho biết requirements có xuất hiện trong JSON PATCH hay không.
    public boolean isRequirementsProvided() {
        return requirementsProvided;
    }

    // Cho biết language có xuất hiện trong JSON PATCH hay không.
    public boolean isLanguageProvided() {
        return languageProvided;
    }

    // Cho biết level có xuất hiện trong JSON PATCH hay không.
    public boolean isLevelProvided() {
        return levelProvided;
    }

    // Cho biết thumbnailUrl có xuất hiện trong JSON PATCH hay không.
    public boolean isThumbnailUrlProvided() {
        return thumbnailUrlProvided;
    }

    // Cho biết price có xuất hiện trong JSON PATCH hay không.
    public boolean isPriceProvided() {
        return priceProvided;
    }

    // Cho biết discountedPrice có xuất hiện trong JSON PATCH hay không.
    public boolean isDiscountedPriceProvided() {
        return discountedPriceProvided;
    }

    // Cho biết free có xuất hiện trong JSON PATCH hay không.
    public boolean isFreeProvided() {
        return freeProvided;
    }

    // Cho biết status có xuất hiện trong JSON PATCH hay không.
    public boolean isStatusProvided() {
        return statusProvided;
    }

    // Cho biết assignedSmeId có xuất hiện trong JSON PATCH hay không.
    public boolean isAssignedSmeIdProvided() {
        return assignedSmeIdProvided;
    }

    @JsonIgnore
    // Kiểm tra yêu cầu PATCH có ít nhất một trường nghiệp vụ hay không.
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
