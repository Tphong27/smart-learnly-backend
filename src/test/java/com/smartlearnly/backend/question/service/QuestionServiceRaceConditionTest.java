package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class QuestionServiceRaceConditionTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository answerRepository;
    @Mock
    private QuestionAnswerMediaAttachmentRepository answerMediaRepository;
    @Mock
    private QuestionMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private CourseModuleRepository courseModuleRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private QuestionMediaImportService questionMediaImportService;
    @Mock
    private CourseAccessService courseAccessService;

    private QuestionService questionService;
    private UUID courseId;
    private UUID moduleId;
    private UserAccount actor;

    @BeforeEach
    void setUp() {
        questionService = new QuestionService(
                questionRepository,
                answerRepository,
                answerMediaRepository,
                mediaAttachmentRepository,
                courseModuleRepository,
                currentUserService,
                questionMediaImportService,
                courseAccessService
        );
        courseId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@slp.vn");
        actor.setRole("ADMIN");

        lenient().when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        lenient().when(courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                moduleId,
                courseId,
                "active"
        )).thenReturn(true);
    }

    @Test
    void importBatch_singleRow_succeedsWhenNoDuplicate() {
        when(questionRepository.existsActiveDuplicateInCourse(any(), any(), any())).thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QuestionImportDtos.ImportBatchResponse response = questionService.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(validRow(1, "What is 2+2?")), "excel"));

        assertThat(response.created()).isEqualTo(1);
        verify(questionRepository, times(1)).save(any(Question.class));
    }

    @Test
    void importBatch_singleRow_throwsWhenDuplicateAlreadyExists() {
        when(questionRepository.existsActiveDuplicateInCourse(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> questionService.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(validRow(1, "What is 2+2?")), "excel")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists in this course");

        verify(questionRepository, times(0)).save(any(Question.class));
    }

    @Test
    void importBatch_concurrentDuplicateImports_serviceLayerAllowsBoth() {
        AtomicInteger existsCalls = new AtomicInteger();
        when(questionRepository.existsActiveDuplicateInCourse(any(), any(), any())).thenAnswer(invocation -> {
            existsCalls.incrementAndGet();
            return false;
        });
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        int threadCount = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    start.await();
                    var resp = questionService.importBatchForCourse(
                            courseId,
                            new QuestionImportDtos.ImportBatchRequest(List.of(validRow(1, "Race text")), "excel"));
                    done.countDown();
                    return resp.created();
                } catch (Throwable t) {
                    done.countDown();
                    throw new RuntimeException(t);
                }
            }));
        }
        start.countDown();

        try {
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();

        long totalCreated = futures.stream().mapToInt(f -> {
            try {
                return f.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                return -1;
            }
        }).sum();
        assertThat(totalCreated).isEqualTo(threadCount);
        verify(questionRepository, atLeastOnce()).save(any(Question.class));
    }

    private QuestionImportDtos.ImportRow validRow(int rowNumber, String text) {
        return new QuestionImportDtos.ImportRow(
                Integer.valueOf(rowNumber),
                text,
                QuestionType.MULTIPLE_CHOICE.name().toLowerCase(),
                List.of("A", "B"),
                "A",
                null,
                (Short) null,
                null,
                moduleId,
                (List<String>) null,
                (List<String>) null
        );
    }
}
