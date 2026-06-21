package com.smartlearnly.backend.course.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface CourseDetailProjection {

	UUID getId();

	String getTitle();

	String getSlug();

	String getDescription();

	BigDecimal getPrice();

	BigDecimal getDiscountedPrice();

	String getAvatarUrl();

	boolean isFeatured();

	UUID getCategoryId();

	String getCategoryName();

	String getCategorySlug();
}
