package com.smartlearnly.backend.flashcard.personal.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkCreatePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.GeneratePersonalFlashcardsFromTextRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardCardResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetDetailResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalGeneratedFlashcardCandidateResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalGeneratedFlashcardsResponse;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardGeminiGenerationService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardGeminiGenerationService.GeminiGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PersonalFlashcardImportService {
    private static final int MIN_SOURCE_TEXT_LENGTH = 100;
    private static final int MAX_SOURCE_TEXT_LENGTH = 20_000;
    private static final int MAX_GENERATION_COUNT = 30;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("auto", "vi", "en");

    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final CurrentUserService currentUserService;
    private final PersonalFlashcardImageUrlPolicy imageUrlPolicy;
    private final FlashcardGeminiGenerationService geminiGenerationService;
    private final FlashcardDocumentTextExtractionService documentTextExtractionService;
    private final FlashcardDocumentGenerationService documentGenerationService;

    public PersonalGeneratedFlashcardsResponse generateFromText(
            UUID setId,
            GeneratePersonalFlashcardsFromTextRequest request
    ) {
        UserAccount actor = requireEligibleActor();
        FlashcardSet flashcardSet = requirePersonalSet(actor, setId);
        int targetCount = normalizeDesiredCount(request.desiredCount());
        String sourceText = normalizeSourceText(request.sourceText());
        GenerationResult result = geminiGenerationService.generate(GeminiGenerationRequest.text(
                sourceText,
                targetCount,
                normalizeLanguage(request.language()),
                "TEXT",
                "Pasted Text"
        ));
        return toGeneratedResponse("TEXT", "Pasted Text", targetCount, result);
    }

    public PersonalGeneratedFlashcardsResponse generateFromFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language
    ) {
        UserAccount actor = requireEligibleActor();
        requirePersonalSet(actor, setId);
        int targetCount = normalizeDesiredCount(desiredCount);
        String normalizedLanguage = normalizeLanguage(language);
        DocumentTextExtractionResult extraction = documentTextExtractionService.extract(file);
        GenerationResult result = documentGenerationService.generate(new DocumentGenerationRequest(
                extraction.text(),
                extraction.images(),
                extraction.renderedPageImages(),
                targetCount,
                normalizedLanguage,
                extraction.sourceType(),
                extraction.sourceName()
        ));
        return toGeneratedResponse(extraction.sourceType(), extraction.sourceName(), targetCount, result);
    }

    @Transactional
    public PersonalFlashcardSetDetailResponse bulkCreateCards(
            UUID setId,
            BulkCreatePersonalFlashcardCardsRequest request
    ) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        List<CreatePersonalFlashcardCardRequest> cards = request.cards();

        int nextOrderIndex = flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1;
        List<FlashcardCard> entities = cards.stream()
                .map(cardRequest -> {
                    FlashcardCard card = new FlashcardCard();
                    card.setFlashcardSet(flashcardSet);
                    applyCardValues(
                            card,
                            cardRequest.frontText(),
                            cardRequest.frontImageUrl(),
                            cardRequest.backText(),
                            cardRequest.backImageUrl(),
                            cardRequest.hint(),
                            cardRequest.explanation(),
                            flashcardSet.getId()
                    );
                    return card;
                })
                .toList();
        for (FlashcardCard card : entities) {
            card.setOrderIndex(nextOrderIndex++);
        }
        flashcardCardRepository.saveAllAndFlush(entities);
        touchSet(flashcardSet);
        return toDetail(
                flashcardSet,
                flashcardCardRepository.findPersonalActiveBySetIdOrderByOrderIndex(setId));
    }

    private PersonalGeneratedFlashcardsResponse toGeneratedResponse(
            String sourceType,
            String sourceName,
            int requestedCount,
            GenerationResult result
    ) {
        List<GeneratedFlashcardCandidate> candidates = result == null || result.candidates() == null
                ? List.of()
                : result.candidates();
        return new PersonalGeneratedFlashcardsResponse(
                normalizeNullable(sourceType) == null ? "TEXT" : sourceType.trim(),
                normalizeNullable(sourceName) == null ? "Pasted Text" : sourceName.trim(),
                requestedCount,
                candidates.stream()
                        .map(candidate -> new PersonalGeneratedFlashcardCandidateResponse(
                                candidate.frontText(),
                                candidate.backText(),
                                candidate.hint(),
                                candidate.explanation(),
                                candidate.sourceExcerpt()
                        ))
                        .toList()
        );
    }

    private UserAccount requireEligibleActor() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TRAINEE", "TRAINER", "SME").contains(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to use Personal Flashcards");
        }
        return actor;
    }

    private FlashcardSet requirePersonalSet(UserAccount actor, UUID setId) {
        return flashcardSetRepository.findPersonalByIdAndOwnerId(setId, actor.getId())
                .orElseThrow(this::setNotFound);
    }

    private FlashcardSet requirePersonalSetForWrite(UserAccount actor, UUID setId) {
        return flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(setId, actor.getId())
                .orElseThrow(this::setNotFound);
    }

    private void applyCardValues(
            FlashcardCard card,
            String frontText,
            String frontImageUrl,
            String backText,
            String backImageUrl,
            String hint,
            String explanation,
            UUID setId
    ) {
        String normalizedFrontImageUrl = normalizeNullable(frontImageUrl);
        String normalizedBackImageUrl = normalizeNullable(backImageUrl);
        imageUrlPolicy.validateNewOrUnchanged(normalizedFrontImageUrl, card.getFrontImageUrl(), setId);
        imageUrlPolicy.validateNewOrUnchanged(normalizedBackImageUrl, card.getBackImageUrl(), setId);

        card.setFrontText(normalizeNullable(frontText));
        card.setFrontImageUrl(normalizedFrontImageUrl);
        card.setBackText(normalizeNullable(backText));
        card.setBackImageUrl(normalizedBackImageUrl);
        card.setHint(normalizeNullable(hint));
        card.setExplanation(normalizeNullable(explanation));
        validateCard(card);
    }

    private void validateCard(FlashcardCard card) {
        if (!hasText(card.getFrontText()) && !hasText(card.getFrontImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard front needs text or an image");
        }
        if (!hasText(card.getBackText()) && !hasText(card.getBackImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard back needs text or an image");
        }
    }

    private PersonalFlashcardSetDetailResponse toDetail(FlashcardSet flashcardSet, List<FlashcardCard> cards) {
        return new PersonalFlashcardSetDetailResponse(
                flashcardSet.getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream().map(this::toCardResponse).toList(),
                flashcardSet.getCreatedAt(),
                flashcardSet.getUpdatedAt()
        );
    }

    private PersonalFlashcardCardResponse toCardResponse(FlashcardCard card) {
        return new PersonalFlashcardCardResponse(
                card.getId(),
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

    private void touchSet(FlashcardSet flashcardSet) {
        flashcardSet.setUpdatedAt(Instant.now());
        flashcardSetRepository.save(flashcardSet);
    }

    private int normalizeDesiredCount(Integer value) {
        if (value == null || value < 1 || value > MAX_GENERATION_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Desired count must be between 1 and 30");
        }
        return value;
    }

    private String normalizeSourceText(String value) {
        String normalized = normalizeTextBlock(value);
        if (normalized.length() < MIN_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source text must contain at least 100 readable characters");
        }
        if (normalized.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source text must not exceed 20000 characters");
        }
        return normalized;
    }

    private String normalizeLanguage(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return "auto";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Language must be auto, vi, or en");
        }
        return normalized;
    }

    private String normalizeTextBlock(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ');
        return normalized.replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException setNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Personal flashcard set was not found");
    }
}
