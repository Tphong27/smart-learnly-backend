package com.smartlearnly.backend.course.entity;

import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "courses", schema = "public")
public class Course {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String slug;

    @Column(name = "short_description")
    private String shortDescription;

    @Column
    private String description;

    @Column
    private String outcomes;

    @Column
    private String requirements;

    @Column
    private String language;

    @Column
    private String level;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private UserAccount creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_sme_id")
    private UserAccount assignedSme;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "is_featured", nullable = false)
    private Boolean featured;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "discounted_price")
    private BigDecimal discountedPrice;

    @Column(name = "is_free", nullable = false)
    private Boolean free;

    @Convert(converter = CourseStatusConverter.class)
    @Column(nullable = false, columnDefinition = "course_status")
    @ColumnTransformer(write = "?::course_status")
    private CourseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "access_blocked_at")
    private Instant accessBlockedAt;

    @Column(name = "access_block_reason")
    private String accessBlockReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_blocked_by")
    private UserAccount accessBlockedBy;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        if (free == null) {
            free = false;
        }
        if (featured == null) {
            featured = false;
        }
        if (status == null) {
            status = CourseStatus.DRAFT;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
