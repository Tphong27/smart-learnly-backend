package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveTemporaryFlashcardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveTemporaryFlashcardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class AdminFlashcardStagingService {
    private static final String STATUS_DRAFT = "draft";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>");
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final List<String> VISIBLE_STAGING_STATUSES = List.of(STATUS_DRAFT, STATUS_APPROVED);
    private static final int MAX_STAGING_ACTION_CARD_COUNT = 500;

    private final FlashcardSetRepository flashcardSetRepository;
    @Autowired
    private CurriculumLessonRepository curriculumLessonRepository;
    @Autowired
    private CourseAccessService courseAccessService;
    @Autowired
    private TrainerClassCurriculumService trainerClassCurriculumService;
    private final FlashcardCardRepository flashcardCardRepository;
    private final FlashcardStagingBatchRepository stagingBatchRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final CurrentUserService currentUserService;

    /** Liệt kê các batch draft/approved và card tương ứng của flashcard set. */
    @Transactional(readOnly = true)
    public List<StagingBatchResponse> listStaging(UUID setId) {
        resolveSetContext(setId);
        List<FlashcardStagingBatch> batches = stagingBatchRepository
                .findByFlashcardSetIdAndStatusInOrderByCreatedAtDesc(setId, VISIBLE_STAGING_STATUSES);
        if (batches.isEmpty()) {
            return List.of();
        }
        List<UUID> batchIds = batches.stream().map(FlashcardStagingBatch::getId).toList();
        Map<UUID, List<FlashcardStagingCard>> cardsByBatchId = stagingCardRepository
                .findByBatchIdInOrderBySortOrderAscCreatedAtAsc(batchIds)
                .stream()
                .collect(Collectors.groupingBy(card -> card.getBatch().getId(), LinkedHashMap::new, Collectors.toList()));

        return batches.stream()
                .map(batch -> toBatchResponse(batch, cardsByBatchId.getOrDefault(batch.getId(), List.of())))
                .toList();
    }

    /** Cập nhật nội dung một staging card đang ở trạng thái draft. */
    @Transactional
    public StagingCardResponse updateCard(UUID stagingCardId, UpdateStagingCardRequest request) {
        FlashcardStagingCard card = findAuthorizedStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be edited");
        applyUpdate(card, request);
        validateCard(card);
        return toCardResponse(stagingCardRepository.save(card));
    }

    /** Từ chối một staging card draft theo ID. */
    @Transactional
    public void rejectCard(UUID stagingCardId) {
        FlashcardStagingCard card = findAuthorizedStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be rejected");
        card.setStatus(STATUS_REJECTED);
        stagingCardRepository.save(card);
    }

    /** Từ chối nhiều staging card sau khi xác thực toàn bộ request trước khi ghi. */
    @Transactional
    public RejectStagingCardsResponse reject(UUID setId, RejectStagingCardsRequest request) {
        resolveSetContext(setId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one staging card id is required");
        }
        List<FlashcardStagingCard> cards = resolveCardsForAction(
                setId,
                request.stagingCardIds(),
                "Rejection request contains duplicate staging card ids",
                "Only draft staging cards can be rejected"
        );

        for (FlashcardStagingCard card : cards) {
            card.setStatus(STATUS_REJECTED);
        }
        List<FlashcardStagingCard> savedCards = stagingCardRepository.saveAll(cards);
        return new RejectStagingCardsResponse(savedCards.size());
    }

    /** Duyệt staging card thành flashcard thật và cập nhật trạng thái batch. */
    @Transactional
    public ApproveStagingCardsResponse approve(UUID setId, ApproveStagingCardsRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one staging card id is required");
        }
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<FlashcardStagingCard> cards = resolveCardsForApproval(setId, request.stagingCardIds());
        assertNoDuplicateApproval(setId, cards);

        int nextOrderIndex = flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1;
        List<FlashcardCard> flashcards = new ArrayList<>();
        for (FlashcardStagingCard stagingCard : cards) {
            FlashcardCard flashcard = new FlashcardCard();
            flashcard.setFlashcardSet(context.flashcardSet());
            flashcard.setFrontText(stagingCard.getFrontText());
            flashcard.setFrontImageUrl(stagingCard.getFrontImageUrl());
            flashcard.setBackText(stagingCard.getBackText());
            flashcard.setBackImageUrl(stagingCard.getBackImageUrl());
            flashcard.setHint(stagingCard.getHint());
            flashcard.setExplanation(stagingCard.getExplanation());
            flashcard.setOrderIndex(nextOrderIndex);
            nextOrderIndex += 1;
            flashcards.add(flashcard);
            stagingCard.setStatus(STATUS_APPROVED);
        }

        List<FlashcardCard> savedFlashcards = flashcardCardRepository.saveAll(flashcards);
        stagingCardRepository.saveAll(cards);
        markFullyApprovedBatches(cards, actor);

        return new ApproveStagingCardsResponse(
                savedFlashcards.size(),
                savedFlashcards.stream().map(FlashcardCard::getId).toList()
        );
    }

    /** Duyệt trực tiếp các ứng viên tạm thời hợp lệ và bỏ qua ứng viên trùng hoặc rỗng. */
    @Transactional
    public ApproveTemporaryFlashcardsResponse approveTemporary(
            UUID setId,
            ApproveTemporaryFlashcardsRequest request
    ) {
        SetContext context = resolveSetContextForWrite(setId);
        if (request == null || request.cards() == null || request.cards().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one flashcard candidate is required");
        }
        if (request.cards().size() > MAX_STAGING_ACTION_CARD_COUNT) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Approval request must not exceed 500 candidates"
            );
        }
        if (request.cards().stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard candidate must not be null");
        }

        List<FlashcardCard> existingCards = flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId);
        Set<String> knownKeys = (existingCards == null ? List.<FlashcardCard>of() : existingCards)
                .stream()
                .map(card -> duplicateKey(card.getFrontText(), card.getBackText()))
                .filter(this::hasDuplicateKey)
                .collect(Collectors.toCollection(HashSet::new));

        int nextOrderIndex = flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1;
        int duplicateSkipped = 0;
        int invalidSkipped = 0;
        List<FlashcardCard> flashcards = new ArrayList<>();
        for (TemporaryFlashcardCardRequest candidate : request.cards()) {
            TemporaryCardDraft draft = toTemporaryCardDraft(candidate);
            if (!isValidTemporaryCard(draft)) {
                invalidSkipped += 1;
                continue;
            }

            String duplicateKey = duplicateKey(draft.frontText(), draft.backText());
            if (hasDuplicateKey(duplicateKey) && !knownKeys.add(duplicateKey)) {
                duplicateSkipped += 1;
                continue;
            }

            FlashcardCard flashcard = new FlashcardCard();
            flashcard.setFlashcardSet(context.flashcardSet());
            flashcard.setFrontText(draft.frontText());
            flashcard.setFrontImageUrl(draft.frontImageUrl());
            flashcard.setBackText(draft.backText());
            flashcard.setBackImageUrl(draft.backImageUrl());
            flashcard.setHint(draft.hint());
            flashcard.setExplanation(draft.explanation());
            flashcard.setOrderIndex(nextOrderIndex);
            nextOrderIndex += 1;
            flashcards.add(flashcard);
        }

        List<FlashcardCard> savedFlashcards = flashcardCardRepository.saveAll(flashcards);
        return new ApproveTemporaryFlashcardsResponse(
                request.cards().size(),
                savedFlashcards.size(),
                duplicateSkipped,
                invalidSkipped,
                savedFlashcards.stream().map(this::toFlashcardCardResponse).toList()
        );
    }

    /** Nạp các card đủ điều kiện để duyệt theo đúng thứ tự ID trong request. */
    private List<FlashcardStagingCard> resolveCardsForApproval(UUID setId, List<UUID> requestedIds) {
        return resolveCardsForAction(
                setId,
                requestedIds,
                "Approval request contains duplicate staging card ids",
                "Only draft staging cards can be approved"
        );
    }

    /** Xác thực quyền sở hữu, trạng thái draft và thứ tự của danh sách staging card. */
    private List<FlashcardStagingCard> resolveCardsForAction(
            UUID setId,
            List<UUID> requestedIds,
            String duplicateMessage,
            String draftMessage
    ) {
        validateStagingActionIds(requestedIds, duplicateMessage);
        Map<UUID, FlashcardStagingCard> cardsById = stagingCardRepository.findByIdIn(requestedIds).stream()
                .collect(Collectors.toMap(FlashcardStagingCard::getId, Function.identity()));
        if (cardsById.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more staging cards were not found");
        }

        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (UUID cardId : requestedIds) {
            FlashcardStagingCard card = cardsById.get(cardId);
            if (!setId.equals(card.getBatch().getFlashcardSet().getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Staging card must belong to the selected flashcard set");
            }
            requireDraftCard(card, draftMessage);
            cards.add(card);
        }
        return cards;
    }

    /** Kiểm tra danh sách ID thao tác không rỗng, không null, không trùng và không vượt giới hạn. */
    private void validateStagingActionIds(List<UUID> requestedIds, String duplicateMessage) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one staging card id is required");
        }
        if (requestedIds.size() > MAX_STAGING_ACTION_CARD_COUNT) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Staging card request must not exceed 500 cards"
            );
        }
        if (requestedIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Staging card id must not be null");
        }
        assertNoDuplicates(requestedIds, duplicateMessage);
    }

    /** Chặn card duyệt trùng flashcard hiện có hoặc trùng nhau trong cùng request. */
    private void assertNoDuplicateApproval(UUID setId, List<FlashcardStagingCard> cards) {
        List<FlashcardCard> existingCards = flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId);
        Set<String> existingKeys = (existingCards == null ? List.<FlashcardCard>of() : existingCards)
                .stream()
                .map(card -> duplicateKey(card.getFrontText(), card.getBackText()))
                .filter(this::hasDuplicateKey)
                .collect(Collectors.toSet());
        Set<String> approvalKeys = new HashSet<>();
        for (FlashcardStagingCard card : cards) {
            String duplicateKey = duplicateKey(card.getFrontText(), card.getBackText());
            if (!hasDuplicateKey(duplicateKey)) {
                continue;
            }
            if (existingKeys.contains(duplicateKey)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate staging card matches an existing Current Flashcard"
                );
            }
            if (!approvalKeys.add(duplicateKey)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate staging cards cannot be approved together"
                );
            }
        }
    }

    /** Đánh dấu batch approved khi batch không còn staging card draft. */
    private void markFullyApprovedBatches(List<FlashcardStagingCard> cards, UserAccount actor) {
        Instant now = Instant.now();
        Map<UUID, FlashcardStagingBatch> batchesById = new LinkedHashMap<>();
        for (FlashcardStagingCard card : cards) {
            batchesById.put(card.getBatch().getId(), card.getBatch());
        }
        List<FlashcardStagingBatch> approvedBatches = new ArrayList<>();
        for (FlashcardStagingBatch batch : batchesById.values()) {
            if (stagingCardRepository.countByBatchIdAndStatus(batch.getId(), STATUS_DRAFT) == 0) {
                batch.setStatus(STATUS_APPROVED);
                batch.setApprovedAt(now);
                batch.setApprovedBy(actor);
                approvedBatches.add(batch);
            }
        }
        if (!approvedBatches.isEmpty()) {
            stagingBatchRepository.saveAll(approvedBatches);
        }
    }

    /** Chuẩn hóa một card được người dùng chọn trong màn hình duyệt tạm thời. */
    /** Chuẩn hóa dữ liệu một ứng viên tạm thời trước khi kiểm tra và xuất bản. */
    private TemporaryCardDraft toTemporaryCardDraft(TemporaryFlashcardCardRequest candidate) {
        return new TemporaryCardDraft(
                normalizeNullable(candidate.frontText()),
                normalizeNullable(candidate.frontImageUrl()),
                normalizeNullable(candidate.backText()),
                normalizeNullable(candidate.backImageUrl()),
                normalizeNullable(candidate.hint()),
                normalizeNullable(candidate.explanation())
        );
    }

    /** Kiểm tra ứng viên tạm thời có ít nhất một nội dung ở một trong hai mặt. */
    private boolean isValidTemporaryCard(TemporaryCardDraft card) {
        if (card == null) {
            return false;
        }
        return hasText(card.frontText())
                || hasText(card.frontImageUrl())
                || hasText(card.backText())
                || hasText(card.backImageUrl());
    }

    /** Tạo khóa so sánh trùng lặp từ nội dung hai mặt flashcard. */
    private String duplicateKey(String frontText, String backText) {
        return normalizeForDuplicate(frontText) + "\n" + normalizeForDuplicate(backText);
    }

    /** Kiểm tra khóa trùng lặp có chứa nội dung thực tế hay không. */
    private boolean hasDuplicateKey(String duplicateKey) {
        return duplicateKey != null && !duplicateKey.trim().isEmpty();
    }

    /** Chuẩn hóa HTML, khoảng trắng và chữ hoa/thường trước khi so sánh trùng lặp. */
    private String normalizeForDuplicate(String value) {
        if (value == null) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(value).replace('\u00A0', ' ');
        String plainText = looksLikeHtml(decoded)
                ? HtmlPlainTextExtractor.extract(decoded)
                : decoded;
        return plainText
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Nạp flashcard set và kiểm tra quyền đọc course hoặc class curriculum tương ứng. */
    private SetContext resolveSetContext(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Lesson lesson = flashcardSet.getLesson();
        if (lesson != null) {
            if (lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            if (lesson.getType() != LessonType.FLASHCARD) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
            }
            if (courseAccessService != null) {
                courseAccessService.requireReadableCourse(lesson.getCourse().getId());
            }
            return new SetContext(flashcardSet, lesson, null, lesson.getCourse());
        }
        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        CurriculumLesson curriculumLesson = curriculumLessonId == null || curriculumLessonRepository == null ? null
                : curriculumLessonRepository.findById(curriculumLessonId).orElse(null);
        Course course = flashcardSet.getCourse();
        if (curriculumLesson == null || curriculumLesson.getType() != LessonType.FLASHCARD
                || course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        var version = curriculumLesson.getSection().getCurriculumVersion();
        if (version.getScope() == CurriculumScope.CLASS) {
            if (version.getClassId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum is inconsistent");
            }
            trainerClassCurriculumService.requireOwnedClassLessonForRead(version.getClassId(), curriculumLessonId);
        } else {
            courseAccessService.requireReadableCourse(course.getId());
        }
        return new SetContext(flashcardSet, null, curriculumLessonId, course);
    }

    /** Nạp flashcard set và kiểm tra quyền chỉnh sửa course hoặc class curriculum tương ứng. */
    private SetContext resolveSetContextForWrite(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Lesson lesson = flashcardSet.getLesson();
        if (lesson != null) {
            if (lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            if (lesson.getType() != LessonType.FLASHCARD) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
            }
            if (courseAccessService != null) {
                courseAccessService.requireUpdatableCourse(lesson.getCourse().getId());
            }
            return new SetContext(flashcardSet, lesson, null, lesson.getCourse());
        }

        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        CurriculumLesson curriculumLesson = curriculumLessonId == null || curriculumLessonRepository == null ? null
                : curriculumLessonRepository.findById(curriculumLessonId).orElse(null);
        Course course = flashcardSet.getCourse();
        if (curriculumLesson == null || curriculumLesson.getType() != LessonType.FLASHCARD
                || course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        var version = curriculumLesson.getSection().getCurriculumVersion();
        if (version.getScope() == CurriculumScope.CLASS) {
            if (version.getClassId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum is inconsistent");
            }
            trainerClassCurriculumService.requireOwnedClassLessonForWrite(version.getClassId(), curriculumLessonId);
        } else if (courseAccessService != null) {
            courseAccessService.requireUpdatableCourse(course.getId());
        }
        return new SetContext(flashcardSet, null, curriculumLessonId, course);
    }

    /** Nạp staging card theo ID hoặc báo lỗi không tìm thấy. */
    private FlashcardStagingCard findStagingCard(UUID stagingCardId) {
        return stagingCardRepository.findById(stagingCardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard staging card was not found"));
    }

    /** Xác nhận người dùng có quyền truy cập set chứa staging card. */
    @Transactional(readOnly = true)
    public void requireCardAccess(UUID stagingCardId) {
        findAuthorizedStagingCard(stagingCardId);
    }

    /** Nạp staging card và kiểm tra quyền truy cập set sở hữu card đó. */
    private FlashcardStagingCard findAuthorizedStagingCard(UUID stagingCardId) {
        FlashcardStagingCard card = findStagingCard(stagingCardId);
        resolveSetContext(card.getBatch().getFlashcardSet().getId());
        return card;
    }

    /** Áp dụng các trường được gửi trong request lên staging card hiện tại. */
    private void applyUpdate(FlashcardStagingCard card, UpdateStagingCardRequest request) {
        if (request.frontText() != null) {
            card.setFrontText(normalizeNullable(request.frontText()));
        }
        if (request.backText() != null) {
            card.setBackText(normalizeNullable(request.backText()));
        }
        if (request.frontImageUrl() != null) {
            card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        }
        if (request.backImageUrl() != null) {
            card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        }
        if (request.hint() != null) {
            card.setHint(normalizeNullable(request.hint()));
        }
        if (request.explanation() != null) {
            card.setExplanation(normalizeNullable(request.explanation()));
        }
        if (request.sortOrder() != null) {
            card.setSortOrder(request.sortOrder());
        }
    }

    /** Bảo đảm cả mặt trước và mặt sau staging card đều có text hoặc ảnh. */
    private void validateCard(FlashcardStagingCard card) {
        boolean hasFront = hasText(card.getFrontText()) || hasText(card.getFrontImageUrl());
        boolean hasBack = hasText(card.getBackText()) || hasText(card.getBackImageUrl());
        if (!hasFront) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging front side requires text or image");
        }
        if (!hasBack) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging back side requires text or image");
        }
    }

    /** Chặn chỉnh sửa, từ chối hoặc duyệt card không còn ở trạng thái draft. */
    private void requireDraftCard(FlashcardStagingCard card, String message) {
        if (!STATUS_DRAFT.equals(card.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /** Chặn danh sách ID trùng trước khi thực hiện ghi dữ liệu. */
    private void assertNoDuplicates(List<UUID> ids, String message) {
        Set<UUID> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /** Chuyển batch và danh sách staging card thành DTO phản hồi. */
    private StagingBatchResponse toBatchResponse(FlashcardStagingBatch batch, List<FlashcardStagingCard> cards) {
        return new StagingBatchResponse(
                batch.getId(),
                batch.getFlashcardSet().getId(),
                batch.getLesson() == null ? batch.getCurriculumLessonId() : batch.getLesson().getId(),
                batch.getCurriculumLessonId(),
                batch.getSourceVideoAiContentId(),
                batch.getCourse().getId(),
                batch.getSourceType(),
                batch.getStatus(),
                batch.getSourceName(),
                cards.stream()
                        .sorted(Comparator.comparing(card -> card.getSortOrder() == null ? 0 : card.getSortOrder()))
                        .map(this::toCardResponse)
                        .toList(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getApprovedAt(),
                batch.getApprovedBy() == null ? null : batch.getApprovedBy().getId()
        );
    }

    /** Chuyển staging card thành DTO phản hồi. */
    private StagingCardResponse toCardResponse(FlashcardStagingCard card) {
        return new StagingCardResponse(
                card.getId(),
                card.getBatch().getId(),
                card.getSourceQuestionId(),
                card.getFrontText(),
                card.getBackText(),
                card.getFrontImageUrl(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getSourceExcerpt(),
                card.getStatus(),
                card.getSortOrder(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    /** Chuyển flashcard đã xuất bản thành DTO phản hồi. */
    private FlashcardCardResponse toFlashcardCardResponse(FlashcardCard card) {
        return new FlashcardCardResponse(
                card.getId(),
                card.getFlashcardSet().getId(),
                card.getFrontText(),
                card.getFrontImageUrl(),
                card.getBackText(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getOrderIndex(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    /** Nhận diện nội dung có HTML tag trước khi tạo khóa so sánh trùng lặp. */
    private boolean looksLikeHtml(String value) {
        return value != null && HTML_TAG_PATTERN.matcher(value).find();
    }

    /** Đổi chuỗi trắng thành null và giữ nguyên nội dung có nghĩa. */
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Kiểm tra chuỗi có nội dung khác khoảng trắng. */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class HtmlPlainTextExtractor extends HTMLEditorKit.ParserCallback {
        private static final Set<HTML.Tag> BLOCK_TAGS = Set.of(
                HTML.Tag.ADDRESS,
                HTML.Tag.BLOCKQUOTE,
                HTML.Tag.DD,
                HTML.Tag.DIV,
                HTML.Tag.DL,
                HTML.Tag.DT,
                HTML.Tag.H1,
                HTML.Tag.H2,
                HTML.Tag.H3,
                HTML.Tag.H4,
                HTML.Tag.H5,
                HTML.Tag.H6,
                HTML.Tag.HR,
                HTML.Tag.LI,
                HTML.Tag.OL,
                HTML.Tag.P,
                HTML.Tag.PRE,
                HTML.Tag.TABLE,
                HTML.Tag.TD,
                HTML.Tag.TH,
                HTML.Tag.TR,
                HTML.Tag.UL
        );

        private final StringBuilder builder = new StringBuilder();
        private boolean pendingLineBreak;

        /** Chuyển HTML thành text; giữ nguyên input nếu parser không đọc được. */
        static String extract(String html) {
            HtmlPlainTextExtractor callback = new HtmlPlainTextExtractor();
            try {
                new ParserDelegator().parse(new StringReader(html), callback, true);
            } catch (IOException exception) {
                return html;
            }
            return callback.builder.toString();
        }

        @Override
        /** Ghi text node sau khi áp dụng line break đang chờ. */
        public void handleText(char[] data, int pos) {
            appendLineBreakIfNeeded();
            builder.append(data);
        }

        @Override
        /** Đánh dấu xuống dòng khi bắt đầu một block HTML. */
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (isBlock(tag)) {
                requestLineBreak();
            }
        }

        @Override
        /** Đánh dấu xuống dòng khi kết thúc một block HTML. */
        public void handleEndTag(HTML.Tag tag, int pos) {
            if (isBlock(tag)) {
                requestLineBreak();
            }
        }

        @Override
        /** Chuyển br và thẻ block đơn thành ranh giới dòng. */
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (tag == HTML.Tag.BR || isBlock(tag)) {
                requestLineBreak();
            }
        }

        /** Kiểm tra tag có cần tạo ranh giới dòng hay không. */
        private boolean isBlock(HTML.Tag tag) {
            return BLOCK_TAGS.contains(tag);
        }

        /** Ghi nhận yêu cầu xuống dòng khi đã có nội dung trước đó. */
        private void requestLineBreak() {
            if (builder.length() > 0) {
                pendingLineBreak = true;
            }
        }

        /** Chèn đúng một line break trước text tiếp theo. */
        private void appendLineBreakIfNeeded() {
            if (!pendingLineBreak) {
                return;
            }
            int length = builder.length();
            if (length > 0 && builder.charAt(length - 1) != '\n') {
                builder.append('\n');
            }
            pendingLineBreak = false;
        }
    }

    private record TemporaryCardDraft(
            String frontText,
            String frontImageUrl,
            String backText,
            String backImageUrl,
            String hint,
            String explanation
    ) {
    }

    private record SetContext(
            FlashcardSet flashcardSet,
            Lesson lesson,
            UUID curriculumLessonId,
            Course course
    ) {
    }

}
