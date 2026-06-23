package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(
            String keyword,
            String domain,
            String action,
            String result,
            String actorRole,
            String targetType,
            String targetId,
            String from,
            String to,
            int page,
            int size
    ) {
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                normalize(keyword),
                parseEnum(AuditDomain.class, domain, "Domain"),
                parseEnum(AuditAction.class, action, "Action"),
                parseEnum(AuditResult.class, result, "Result"),
                normalize(actorRole),
                normalize(targetType),
                normalize(targetId),
                parseInstant(from, "From"),
                parseInstant(to, "To")
        );

        Page<AuditLog> auditLogs = auditLogRepository.findAll(
                toSpecification(criteria),
                PageRequest.of(
                        page,
                        Math.min(size, MAX_PAGE_SIZE),
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))
                )
        );

        return new PageResponse<>(
                auditLogs.stream().map(AuditLogResponse::from).toList(),
                auditLogs.getNumber(),
                auditLogs.getSize(),
                auditLogs.getTotalElements(),
                auditLogs.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(UUID auditLogId) {
        return auditLogRepository.findById(auditLogId)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Audit log was not found"));
    }

    private Specification<AuditLog> toSpecification(AuditLogSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null) {
                String keywordPattern = "%" + criteria.keyword().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("actorEmail")), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("action").as(String.class)), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("domain").as(String.class)), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("targetType")), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("targetId")), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("summary")), keywordPattern)
                ));
            }

            if (criteria.domain() != null) {
                predicates.add(criteriaBuilder.equal(root.get("domain"), criteria.domain()));
            }
            if (criteria.action() != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), criteria.action()));
            }
            if (criteria.result() != null) {
                predicates.add(criteriaBuilder.equal(root.get("result"), criteria.result()));
            }
            if (criteria.actorRole() != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("actorRole")),
                        criteria.actorRole().toLowerCase(Locale.ROOT)
                ));
            }
            if (criteria.targetType() != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("targetType")),
                        criteria.targetType().toLowerCase(Locale.ROOT)
                ));
            }
            if (criteria.targetId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetId"), criteria.targetId()));
            }
            if (criteria.from() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), criteria.to()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " filter is invalid");
        }
    }

    private Instant parseInstant(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Instant.parse(normalized);
        }
        catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(normalized).toInstant();
            }
            catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant();
                }
                catch (DateTimeParseException exception) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " date-time filter is invalid");
                }
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record AuditLogSearchCriteria(
            String keyword,
            AuditDomain domain,
            AuditAction action,
            AuditResult result,
            String actorRole,
            String targetType,
            String targetId,
            Instant from,
            Instant to
    ) {
    }
}
