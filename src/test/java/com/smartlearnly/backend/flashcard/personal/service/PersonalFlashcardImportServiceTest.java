package com.smartlearnly.backend.flashcard.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkCreatePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.GeneratePersonalFlashcardsFromTextRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetDetailResponse;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PersonalFlashcardImportServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PersonalFlashcardImageUrlPolicy imageUrlPolicy;
    @Mock
    private FlashcardGeminiGenerationService geminiGenerationService;
    @Mock
    private FlashcardDocumentTextExtractionService documentTextExtractionService;
    @Mock
    private FlashcardDocumentGenerationService documentGenerationService;

    private PersonalFlashcardImportService service;

    @BeforeEach
    void setUp() {
        service = new PersonalFlashcardImportService(
                flashcardSetRepository,
                flashcardCardRepository,
                currentUserService,
                imageUrlPolicy,
                geminiGenerationService,
                documentTextExtractionService,
                documentGenerationService
        );
    }

    @Test
    void generateFromTextUsesGenericGeminiProviderAndDoesNotPersist() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(geminiGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(new GeneratedFlashcardCandidate(
                        "Front",
                        "Back",
                        "Hint",
                        "Explanation",
                        "Source excerpt"
                ))
        ));

        PersonalGeneratedFlashcardsResponse response = service.generateFromText(
                set.getId(),
                new GeneratePersonalFlashcardsFromTextRequest(
                        "Pasted source content ".repeat(8),
                        10,
                        "vi"
                )
        );

        ArgumentCaptor<GeminiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(GeminiGenerationRequest.class);
        verify(geminiGenerationService).generate(requestCaptor.capture());
        GeminiGenerationRequest request = requestCaptor.getValue();
        assertThat(request.sourceType()).isEqualTo("TEXT");
        assertThat(request.sourceName()).isEqualTo("Pasted Text");
        assertThat(request.desiredCount()).isEqualTo(10);
        assertThat(request.language()).isEqualTo("vi");
        assertThat(response.cards()).singleElement()
                .extracting(candidate -> candidate.sourceExcerpt())
                .isEqualTo("Source excerpt");
        verify(flashcardCardRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void generateFromDocumentChecksOwnershipBeforeExtractionOrProvider() {
        UserAccount actor = user("TRAINER");
        UUID setId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(setId, actor.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateFromFile(
                setId,
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "content".getBytes()),
                5,
                "auto"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(documentTextExtractionService, never()).extract(any());
        verify(documentGenerationService, never()).generate(any());
    }

    @Test
    void generateFromDocumentUsesExistingExtractionAndDocumentAdapter() {
        UserAccount actor = user("SME");
        FlashcardSet set = personalSet(actor);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "content".getBytes());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(documentTextExtractionService.extract(file)).thenReturn(new DocumentTextExtractionResult(
                "DOCX",
                "lesson.docx",
                "Readable document content ".repeat(8)
        ));
        when(documentGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(new GeneratedFlashcardCandidate("Front", "Back", null, null, "Excerpt"))
        ));

        PersonalGeneratedFlashcardsResponse response = service.generateFromFile(
                set.getId(),
                file,
                10,
                "en"
        );

        ArgumentCaptor<DocumentGenerationRequest> requestCaptor = ArgumentCaptor.forClass(DocumentGenerationRequest.class);
        verify(documentGenerationService).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().desiredCount()).isEqualTo(10);
        assertThat(requestCaptor.getValue().sourceType()).isEqualTo("DOCX");
        assertThat(response.requestedCount()).isEqualTo(10);
        assertThat(response.sourceName()).isEqualTo("lesson.docx");
    }

    @Test
    void bulkCreateCreatesAllCardsAtomicallyInRequestOrder() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        Instant now = Instant.now();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(set.getId())).thenReturn(0);
        AtomicReference<List<FlashcardCard>> savedCards = new AtomicReference<>(List.of());
        when(flashcardCardRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<FlashcardCard> cards = invocation.getArgument(0);
            cards.forEach(card -> {
                card.setId(UUID.randomUUID());
                card.setCreatedAt(now);
                card.setUpdatedAt(now);
            });
            savedCards.set(cards);
            return cards;
        });
        when(flashcardCardRepository.findPersonalActiveBySetIdOrderByOrderIndex(set.getId()))
                .thenAnswer(invocation -> savedCards.get());
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalFlashcardSetDetailResponse response = service.bulkCreateCards(
                set.getId(),
                new BulkCreatePersonalFlashcardCardsRequest(List.of(
                        new CreatePersonalFlashcardCardRequest("Front 1", null, "Back 1", null, "Hint", null),
                        new CreatePersonalFlashcardCardRequest("Front 2", null, "Back 2", null, null, "Explanation")
                ))
        );

        ArgumentCaptor<List<FlashcardCard>> cardsCaptor = savedCardsCaptor();
        verify(flashcardCardRepository).saveAllAndFlush(cardsCaptor.capture());
        assertThat(cardsCaptor.getValue()).extracting(FlashcardCard::getOrderIndex)
                .containsExactly(1, 2);
        assertThat(response.cards()).hasSize(2);
        assertThat(set.getUpdatedAt()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<FlashcardCard>> savedCardsCaptor() {
        return ArgumentCaptor.forClass((Class<List<FlashcardCard>>) (Class<?>) List.class);
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setEmail(role.toLowerCase() + "@smartlearnly.dev");
        return user;
    }

    private FlashcardSet personalSet(UserAccount owner) {
        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCreatedBy(owner);
        set.setTitle("Personal set");
        set.setIsPublic(false);
        set.setIsOfficial(false);
        set.setCreatedAt(Instant.now());
        set.setUpdatedAt(Instant.now());
        return set;
    }
}
