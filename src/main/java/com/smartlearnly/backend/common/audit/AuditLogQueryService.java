package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private static final Duration MAX_RANGE = Duration.ofDays(90);

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogSummaryResponse> list(
            String keyword,
            AuditDomain domain,
            AuditAction action,
            AuditResult result,
            UUID actorId,
            String actorRole,
            String targetType,
            String targetId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        validateRange(from, to);
        Specification<AuditLog> specification = filters(
                keyword, domain, action, result, actorId, actorRole, targetType, targetId, from, to
        );
        Page<AuditLog> logs = auditLogRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))
        );
        return new PageResponse<>(
                logs.stream().map(AuditLogSummaryResponse::from).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AuditLogDetailResponse get(UUID auditLogId) {
        AuditLog log = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Audit log was not found"));
        return AuditLogDetailResponse.from(log);
    }

    private Specification<AuditLog> filters(
            String keyword,
            AuditDomain domain,
            AuditAction action,
            AuditResult result,
            UUID actorId,
            String actorRole,
            String targetType,
            String targetId,
            Instant from,
            Instant to
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String normalizedKeyword = normalize(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("actorEmail")), pattern),
                        builder.like(builder.lower(root.get("summary")), pattern),
                        builder.like(builder.lower(root.get("targetId")), pattern),
                        builder.like(builder.lower(root.get("action").as(String.class)), pattern)
                ));
            }
            if (domain != null) predicates.add(builder.equal(root.get("domain"), domain));
            if (action != null) predicates.add(builder.equal(root.get("action"), action));
            if (result != null) predicates.add(builder.equal(root.get("result"), result));
            if (actorId != null) predicates.add(builder.equal(root.get("actorId"), actorId));
            addIgnoreCase(predicates, builder, root.get("actorRole"), actorRole);
            addIgnoreCase(predicates, builder, root.get("targetType"), targetType);
            addIgnoreCase(predicates, builder, root.get("targetId"), targetId);
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addIgnoreCase(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Path<String> path,
            String value
    ) {
        String normalized = normalize(value);
        if (normalized != null) {
            predicates.add(builder.equal(builder.lower(path), normalized.toLowerCase(Locale.ROOT)));
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Audit date from must not be after date to");
            }
            if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Audit date range must not exceed 90 days");
            }
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
