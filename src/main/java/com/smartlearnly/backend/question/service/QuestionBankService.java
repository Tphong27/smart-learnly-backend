package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.question.dto.QuestionBankDto;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionBankService {
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_ARCHIVED = "archived";

    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    private static final int MAX_PAGE_SIZE = 200;

    @Transactional(readOnly = true)
    public List<QuestionBankDto.Response> list(UUID courseId, String search, String status) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedSearch = normalizeNullable(search);

        return questionBankRepository.searchForAdmin(
                courseId,
                normalizedStatus,
                normalizedSearch,
                PageRequest.of(0, MAX_PAGE_SIZE)
        ).getContent().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionBankDto.Response> listPaged(UUID courseId, String search, String status, int page, int size) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedSearch = normalizeNullable(search);

        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Page<QuestionBank> result = questionBankRepository.searchForAdmin(
                courseId,
                normalizedStatus,
                normalizedSearch,
                PageRequest.of(effectivePage, effectiveSize)
        );

        List<QuestionBankDto.Response> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public QuestionBankDto.Response get(UUID bankId) {
        return toResponse(findBank(bankId));
    }

    @Transactional
    public QuestionBankDto.Response create(QuestionBankDto.CreateRequest request) {
        ensureCourseExists(request.courseId());
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        QuestionBank bank = new QuestionBank();
        bank.setCourseId(request.courseId());
        bank.setName(normalizeRequired(request.name(), "Bank name is required"));
        bank.setDescription(normalizeNullable(request.description()));
        bank.setStatus(normalizeStatus(request.status(), STATUS_DRAFT));
        bank.setCreatedBy(actor.getId());

        QuestionBank saved = questionBankRepository.save(bank);
        auditLogService.recordUser(
                actor,
                AuditAction.QUESTION_BANK_CREATED,
                AuditDomain.CONTENT,
                AuditResult.SUCCESS,
                "QUESTION_BANK",
                saved.getId().toString(),
                "Question bank was created",
                null,
                Map.of("status", saved.getStatus()),
                null
        );
        return toResponse(saved);
    }

    @Transactional
    public QuestionBankDto.Response update(UUID bankId, QuestionBankDto.UpdateRequest request) {
        QuestionBank bank = findBank(bankId);
        if (STATUS_ARCHIVED.equals(bank.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot update an archived question bank");
        }

        String previousStatus = bank.getStatus();
        bank.setName(normalizeRequired(request.name(), "Bank name is required"));
        bank.setDescription(normalizeNullable(request.description()));
        if (request.status() != null && !request.status().isBlank()) {
            bank.setStatus(normalizeStatus(request.status(), bank.getStatus()));
        }

        QuestionBank saved = questionBankRepository.save(bank);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.recordUser(
                actor,
                AuditAction.QUESTION_BANK_UPDATED,
                AuditDomain.CONTENT,
                AuditResult.SUCCESS,
                "QUESTION_BANK",
                saved.getId().toString(),
                "Question bank was updated",
                Map.of("status", previousStatus),
                Map.of("status", saved.getStatus()),
                null
        );
        return toResponse(saved);
    }

    @Transactional
    public void archive(UUID bankId) {
        QuestionBank bank = findBank(bankId);
        if (STATUS_ARCHIVED.equals(bank.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Question bank is already archived");
        }

        String previousStatus = bank.getStatus();
        bank.setStatus(STATUS_ARCHIVED);
        QuestionBank saved = questionBankRepository.save(bank);

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.recordUser(
                actor,
                AuditAction.QUESTION_BANK_ARCHIVED,
                AuditDomain.CONTENT,
                AuditResult.SUCCESS,
                "QUESTION_BANK",
                saved.getId().toString(),
                "Question bank was archived",
                Map.of("status", previousStatus),
                Map.of("status", STATUS_ARCHIVED),
                null
        );
    }

    @Transactional
    public QuestionBankDto.Response restore(UUID bankId, String targetStatus) {
        QuestionBank bank = findBank(bankId);
        if (!STATUS_ARCHIVED.equals(bank.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only an archived question bank can be restored");
        }

        String normalizedTarget = normalizeStatus(targetStatus, null);
        if (STATUS_ARCHIVED.equals(normalizedTarget)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Restored question bank cannot remain archived");
        }

        String previousStatus = bank.getStatus();
        bank.setStatus(normalizedTarget);
        QuestionBank saved = questionBankRepository.save(bank);

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.recordUser(
                actor,
                AuditAction.QUESTION_BANK_RESTORED,
                AuditDomain.CONTENT,
                AuditResult.SUCCESS,
                "QUESTION_BANK",
                saved.getId().toString(),
                "Question bank was restored",
                Map.of("status", previousStatus),
                Map.of("status", saved.getStatus()),
                null
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuestionBank findActiveBankEntity(UUID bankId) {
        QuestionBank bank = findBank(bankId);
        if (STATUS_ARCHIVED.equals(bank.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Question bank is archived");
        }
        return bank;
    }

    private QuestionBank findBank(UUID bankId) {
        return questionBankRepository.findById(bankId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question bank not found"));
    }

    private void ensureCourseExists(UUID courseId) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course ID is required");
        }
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found");
        }
    }

    private QuestionBankDto.Response toResponse(QuestionBank bank) {
        long questionCount = questionRepository.countByQuestionBankId(bank.getId());
        return new QuestionBankDto.Response(
                bank.getId(),
                bank.getId(),
                bank.getCourseId(),
                bank.getName(),
                bank.getDescription(),
                bank.getStatus(),
                questionCount,
                bank.getCreatedBy(),
                bank.getCreatedAt(),
                bank.getUpdatedAt()
        );
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeStatus(status, null);
    }

    private String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_DRAFT.equals(normalized)
                && !STATUS_APPROVED.equals(normalized)
                && !STATUS_ARCHIVED.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question bank status must be draft, approved, or archived");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}