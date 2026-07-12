package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private QuestionBankService questionBankService;
    private UserAccount actor;

    @BeforeEach
    void setUp() {
        questionBankService = new QuestionBankService(
                questionBankRepository,
                questionRepository,
                courseRepository,
                currentUserService,
                auditLogService
        );
        actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@slp.vn");
        actor.setRole("ADMIN");
    }

    @Test
    void restore_fromArchivedToApproved_succeeds() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("archived");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));
        when(questionBankRepository.save(any(QuestionBank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(questionRepository.countByQuestionBankId(bankId)).thenReturn(0L);

        QuestionBankDto.Response response = questionBankService.restore(bankId, "approved");

        assertThat(response.status()).isEqualTo("approved");
        assertThat(bank.getStatus()).isEqualTo("approved");
        verify(auditLogService).recordUser(
                org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq(AuditAction.QUESTION_BANK_RESTORED),
                org.mockito.ArgumentMatchers.eq(AuditDomain.CONTENT),
                org.mockito.ArgumentMatchers.eq(AuditResult.SUCCESS),
                org.mockito.ArgumentMatchers.eq("QUESTION_BANK"),
                org.mockito.ArgumentMatchers.eq(bankId.toString()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void restore_fromDraft_throwsBusinessRule() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("draft");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));

        assertThatThrownBy(() -> questionBankService.restore(bankId, "approved"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(questionBankRepository, never()).save(any());
        verify(auditLogService, never()).recordUser(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void restore_toArchived_throwsInvalidRequest() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("archived");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));

        assertThatThrownBy(() -> questionBankService.restore(bankId, "archived"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(questionBankRepository, never()).save(any());
    }

    @Test
    void update_whenBankArchived_throwsBusinessRule_evenIfPayloadChangesStatus() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("archived");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));

        QuestionBankDto.UpdateRequest request = new QuestionBankDto.UpdateRequest(
                "New name", "New description", "draft"
        );
        assertThatThrownBy(() -> questionBankService.update(bankId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(questionBankRepository, never()).save(any());
    }

    @Test
    void archiveBank_whenAlreadyArchived_throwsBusinessRule() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("archived");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));

        assertThatThrownBy(() -> questionBankService.archive(bankId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(questionBankRepository, never()).save(any());
    }

    @Test
    void archiveBank_recordsAuditAndFlipsStatus() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setStatus("approved");
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank));
        when(questionBankRepository.save(any(QuestionBank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        questionBankService.archive(bankId);

        assertThat(bank.getStatus()).isEqualTo("archived");
        verify(auditLogService).recordUser(
                org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq(AuditAction.QUESTION_BANK_ARCHIVED),
                org.mockito.ArgumentMatchers.eq(AuditDomain.CONTENT),
                org.mockito.ArgumentMatchers.eq(AuditResult.SUCCESS),
                org.mockito.ArgumentMatchers.eq("QUESTION_BANK"),
                org.mockito.ArgumentMatchers.eq(bankId.toString()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }
}