package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumEntryRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassCurriculumCompositionServiceTest {

    @Mock
    private ClassCurriculumEntryRepository entryRepository;
    @Mock
    private CurriculumLessonRepository lessonRepository;
    @Mock
    private CurriculumSectionRepository sectionRepository;
    @Mock
    private CurriculumVersionRepository versionRepository;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;

    private ClassCurriculumCompositionService service;

    @BeforeEach
    void setUp() {
        service = new ClassCurriculumCompositionService(
                entryRepository,
                lessonRepository,
                sectionRepository,
                versionRepository,
                flashcardSetRepository,
                flashcardCardRepository);
    }

    @Test
    void isCompositionVersionShouldOnlyBeTrueForClassScope() {
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.DRAFT);
        CurriculumVersion masterVersion = version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);

        assertThat(service.isCompositionVersion(classVersion)).isTrue();
        assertThat(service.isCompositionVersion(masterVersion)).isFalse();
    }

    @Test
    void snapshotStructureFromMasterShouldCreateSectionsAndEntriesWithoutLessonRows() {
        CurriculumVersion draft = version(CurriculumScope.CLASS, CurriculumStatus.DRAFT);
        CurriculumVersion source = version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        CurriculumSection sourceSection = new CurriculumSection();
        sourceSection.setId(UUID.randomUUID());
        sourceSection.setTitle("Section 1");
        sourceSection.setSortOrder(0);
        sourceSection.setSourceModuleId(UUID.randomUUID());
        CurriculumLesson sourceLesson = new CurriculumLesson();
        sourceLesson.setId(UUID.randomUUID());
        sourceLesson.setLessonIdentityId(UUID.randomUUID());
        sourceLesson.setTitle("Lesson 1");
        sourceLesson.setSortOrder(0);
        sourceSection.addLesson(sourceLesson);
        source.addSection(sourceSection);

        when(sectionRepository.save(any(CurriculumSection.class))).thenAnswer(invocation -> {
            CurriculumSection saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(entryRepository.save(any(ClassCurriculumEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.snapshotStructure(draft, source);

        ArgumentCaptor<ClassCurriculumEntry> entryCaptor = ArgumentCaptor.forClass(ClassCurriculumEntry.class);
        verify(entryRepository).save(entryCaptor.capture());
        ClassCurriculumEntry entry = entryCaptor.getValue();
        assertThat(entry.getSourceCurriculumLessonId()).isEqualTo(sourceLesson.getId());
        assertThat(entry.getLessonIdentityId()).isEqualTo(sourceLesson.getLessonIdentityId());
        assertThat(entry.getMaterializedLessonId()).isNull();
        assertThat(entry.getSection().getCurriculumVersion()).isEqualTo(draft);
        verify(sectionRepository).save(any(CurriculumSection.class));
        verify(lessonRepository, never()).save(any(CurriculumLesson.class));
    }

    @Test
    void snapshotStructureFromMasterShouldCreateClassOwnedFlashcards() {
        CurriculumVersion draft = version(CurriculumScope.CLASS, CurriculumStatus.DRAFT);
        CurriculumVersion source = version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        CurriculumSection sourceSection = section(source, 0);
        source.addSection(sourceSection);

        CurriculumLesson sourceLesson = materializedLesson("Master flashcards");
        sourceLesson.setType(LessonType.FLASHCARD);
        sourceLesson.setLessonIdentityId(UUID.randomUUID());
        sourceSection.addLesson(sourceLesson);
        FlashcardSet sourceSet = flashcardSet(sourceLesson, "Master set");
        FlashcardCard sourceCard = flashcardCard(sourceSet, "Master front", "Master back");
        UUID classLessonId = UUID.randomUUID();

        when(sectionRepository.save(any(CurriculumSection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson saved = invocation.getArgument(0);
            saved.setId(classLessonId);
            return saved;
        });
        when(entryRepository.save(any(ClassCurriculumEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(any(UUID.class)))
                .thenAnswer(invocation -> sourceLesson.getId().equals(invocation.getArgument(0))
                        ? Optional.of(sourceSet)
                        : Optional.empty());
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceSet.getId()))
                .thenReturn(List.of(sourceCard));

        service.snapshotStructure(draft, source);

        ArgumentCaptor<ClassCurriculumEntry> entryCaptor = ArgumentCaptor.forClass(ClassCurriculumEntry.class);
        verify(entryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getMaterializedLessonId()).isEqualTo(classLessonId);
        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(setCaptor.getValue()).isNotSameAs(sourceSet);
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isEqualTo(classLessonId);
        verify(flashcardCardRepository).saveAll(argThat(cards -> {
            List<FlashcardCard> copiedCards = StreamSupport.stream(cards.spliterator(), false).toList();
            return copiedCards.size() == 1
                    && copiedCards.get(0) != sourceCard
                    && copiedCards.get(0).getFlashcardSet() == setCaptor.getValue();
        }));
    }

    @Test
    void materializeInheritedFlashcardShouldCloneSetForExistingClass() {
        CurriculumVersion draft = version(CurriculumScope.CLASS, CurriculumStatus.DRAFT);
        draft.setCourseId(UUID.randomUUID());
        CurriculumVersion masterVersion = version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        CurriculumSection classSection = section(draft, 0);
        UUID identityId = UUID.randomUUID();
        CurriculumLesson masterLesson = materializedLesson("Inherited flashcards");
        masterLesson.setType(LessonType.FLASHCARD);
        masterLesson.setLessonIdentityId(identityId);
        ClassCurriculumEntry entry = entry(classSection, masterLesson.getId(), identityId, null);
        entry.setClassVersionId(draft.getId());
        FlashcardSet sourceSet = flashcardSet(masterLesson, "Inherited set");
        FlashcardCard sourceCard = flashcardCard(sourceSet, "Inherited front", "Inherited back");
        UUID classLessonId = UUID.randomUUID();

        when(entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(draft.getId(), masterLesson.getId()))
                .thenReturn(Optional.of(entry));
        when(entryRepository.findByIdAndClassVersionIdForUpdate(entry.getId(), draft.getId()))
                .thenReturn(Optional.of(entry));
        when(versionRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(versionRepository.findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                draft.getCourseId(), CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.of(masterVersion));
        when(lessonRepository.findByCurriculumVersionIdAndLessonIdentityId(masterVersion.getId(), identityId))
                .thenReturn(Optional.of(masterLesson));
        when(lessonRepository.findByCurriculumVersionIdAndLessonIdentityId(draft.getId(), identityId))
                .thenReturn(Optional.empty());
        when(lessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson saved = invocation.getArgument(0);
            saved.setId(classLessonId);
            return saved;
        });
        when(entryRepository.save(any(ClassCurriculumEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(classLessonId))
                .thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(masterLesson.getId()))
                .thenReturn(Optional.of(sourceSet));
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceSet.getId()))
                .thenReturn(List.of(sourceCard));

        CurriculumLesson materialized = service.ensureMaterializedForWrite(draft, masterLesson.getId());

        assertThat(materialized.getId()).isEqualTo(classLessonId);
        assertThat(entry.getMaterializedLessonId()).isEqualTo(classLessonId);
        verify(flashcardSetRepository).save(argThat(set ->
                classLessonId.equals(set.getCurriculumLessonId()) && set != sourceSet));
        verify(flashcardCardRepository).saveAll(argThat(cards ->
                StreamSupport.stream(cards.spliterator(), false)
                        .allMatch(card -> card != sourceCard && card.getFlashcardSet() != sourceSet)));
    }

    @Test
    void snapshotStructureFromClassVersionShouldCopyFlashcardSetAndCards() {
        CurriculumVersion draft = version(CurriculumScope.CLASS, CurriculumStatus.DRAFT);
        CurriculumVersion source = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        CurriculumSection sourceSection = section(source, 0);
        source.addSection(sourceSection);

        CurriculumLesson sourceLesson = materializedLesson("Published flashcards");
        sourceLesson.setType(LessonType.FLASHCARD);
        sourceLesson.setLessonIdentityId(UUID.randomUUID());
        sourceLesson.setSection(sourceSection);
        ClassCurriculumEntry sourceEntry = entry(
                sourceSection, null, sourceLesson.getLessonIdentityId(), sourceLesson.getId());

        FlashcardSet sourceSet = new FlashcardSet();
        sourceSet.setId(UUID.randomUUID());
        sourceSet.setCurriculumLessonId(sourceLesson.getId());
        sourceSet.setTitle("Published flashcards");
        sourceSet.setDescription("Three terms");
        sourceSet.setIsPublic(false);
        sourceSet.setIsOfficial(false);
        FlashcardCard sourceCard = new FlashcardCard();
        sourceCard.setId(UUID.randomUUID());
        sourceCard.setFlashcardSet(sourceSet);
        sourceCard.setFrontText("Front");
        sourceCard.setBackText("Back");
        sourceCard.setOrderIndex(0);

        when(sectionRepository.save(any(CurriculumSection.class))).thenAnswer(invocation -> {
            CurriculumSection saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(entryRepository.findByClassVersionIdAndSectionIdOrderBySortOrderAsc(
                source.getId(), sourceSection.getId())).thenReturn(List.of(sourceEntry));
        when(lessonRepository.findById(sourceLesson.getId())).thenReturn(Optional.of(sourceLesson));
        when(lessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(entryRepository.save(any(ClassCurriculumEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(any(UUID.class)))
                .thenAnswer(invocation -> sourceLesson.getId().equals(invocation.getArgument(0))
                        ? Optional.of(sourceSet)
                        : Optional.empty());
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> {
            FlashcardSet saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceSet.getId()))
                .thenReturn(List.of(sourceCard));

        service.snapshotStructure(draft, source);

        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isNotEqualTo(sourceLesson.getId());
        assertThat(setCaptor.getValue().getTitle()).isEqualTo(sourceSet.getTitle());
        verify(flashcardCardRepository).saveAll(argThat(cards -> {
            List<FlashcardCard> copiedCards = StreamSupport.stream(cards.spliterator(), false).toList();
            return copiedCards.size() == 1
                    && "Front".equals(copiedCards.get(0).getFrontText())
                    && "Back".equals(copiedCards.get(0).getBackText())
                    && setCaptor.getValue() == copiedCards.get(0).getFlashcardSet();
        }));
    }

    @Test
    void effectiveLessonsShouldMergeMaterializedRowsAndTransientInheritedLessons() {
        UUID courseId = UUID.randomUUID();
        UUID masterVersionId = UUID.randomUUID();
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        classVersion.setCourseId(courseId);
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(classVersion);

        UUID rowId = UUID.randomUUID();
        CurriculumLesson materialized = new CurriculumLesson();
        materialized.setId(rowId);
        materialized.setTitle("Materialized");
        materialized.setStatus(LessonStatus.PUBLISHED);

        UUID masterId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        ClassCurriculumEntry inheritedEntry = entry(section, masterId, identity, null);
        ClassCurriculumEntry materializedEntry = entry(section, null, UUID.randomUUID(), rowId);

        when(entryRepository.findByClassVersionIdAndSectionIdOrderBySortOrderAsc(classVersion.getId(), section.getId()))
                .thenReturn(List.of(materializedEntry, inheritedEntry));
        when(lessonRepository.findAllById(List.of(rowId))).thenReturn(List.of(materialized));
        when(versionRepository.findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.of(version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED, masterVersionId)));
        CurriculumLesson master = new CurriculumLesson();
        master.setId(masterId);
        master.setLessonIdentityId(identity);
        master.setTitle("Inherited title");
        master.setStatus(LessonStatus.PUBLISHED);
        master.setType(LessonType.RICH_TEXT);
        when(lessonRepository.findByCurriculumVersionIdAndLessonIdentityIdIn(masterVersionId, List.of(identity)))
                .thenReturn(List.of(master));

        List<CurriculumLesson> lessons = service.effectiveLessons(section);

        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).getId()).isEqualTo(rowId);
        assertThat(lessons.get(1).getId()).isEqualTo(masterId);
        assertThat(lessons.get(1).getLessonIdentityId()).isEqualTo(identity);
        assertThat(lessons.get(1).getTitle()).isEqualTo("Inherited title");
    }

    @Test
    void orderedEffectiveLessonsShouldBatchEntriesAndMaterializedLessonsAcrossSections() {
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        CurriculumSection firstSection = section(classVersion, 0);
        CurriculumSection secondSection = section(classVersion, 1);
        classVersion.addSection(secondSection);
        classVersion.addSection(firstSection);

        CurriculumLesson firstLesson = materializedLesson("First lesson");
        CurriculumLesson secondLesson = materializedLesson("Second lesson");
        ClassCurriculumEntry firstEntry = entry(
                firstSection, null, UUID.randomUUID(), firstLesson.getId());
        ClassCurriculumEntry secondEntry = entry(
                secondSection, null, UUID.randomUUID(), secondLesson.getId());

        when(entryRepository.findByClassVersionIdOrderBySortOrderAscCreatedAtAsc(classVersion.getId()))
                .thenReturn(List.of(firstEntry, secondEntry));
        when(lessonRepository.findAllById(List.of(firstLesson.getId(), secondLesson.getId())))
                .thenReturn(List.of(firstLesson, secondLesson));

        List<CurriculumLesson> lessons = service.orderedEffectiveLessons(classVersion);

        assertThat(lessons).extracting(CurriculumLesson::getTitle)
                .containsExactly("First lesson", "Second lesson");
        verify(entryRepository).findByClassVersionIdOrderBySortOrderAscCreatedAtAsc(classVersion.getId());
        verify(lessonRepository).findAllById(List.of(firstLesson.getId(), secondLesson.getId()));
        verify(entryRepository, never())
                .findByClassVersionIdAndSectionIdOrderBySortOrderAsc(any(UUID.class), any(UUID.class));
    }

    @Test
    void resolveEffectiveLessonShouldResolveInheritedEntryByMasterSourceId() {
        UUID classVersionId = UUID.randomUUID();
        UUID masterVersionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        classVersion.setId(classVersionId);
        classVersion.setCourseId(courseId);
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(classVersion);
        UUID masterId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        ClassCurriculumEntry inheritedEntry = entry(section, masterId, identity, null);
        inheritedEntry.setClassVersionId(classVersionId);

        when(entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(classVersionId, masterId))
                .thenReturn(Optional.of(inheritedEntry));
        when(versionRepository.findById(classVersionId)).thenReturn(Optional.of(classVersion));
        when(versionRepository.findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.of(version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED, masterVersionId)));
        CurriculumLesson master = new CurriculumLesson();
        master.setId(masterId);
        master.setLessonIdentityId(identity);
        master.setTitle("Master title");
        when(lessonRepository.findByCurriculumVersionIdAndLessonIdentityId(masterVersionId, identity))
                .thenReturn(Optional.of(master));

        Optional<CurriculumLesson> resolved = service.resolveEffectiveLesson(classVersion, masterId);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(masterId);
        assertThat(resolved.get().getLessonIdentityId()).isEqualTo(identity);
    }

    @Test
    void resolveEffectiveLessonShouldResolveMaterializedEntryByRowId() {
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        UUID classVersionId = classVersion.getId();
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(classVersion);
        UUID rowId = UUID.randomUUID();
        ClassCurriculumEntry materializedEntry = entry(section, null, UUID.randomUUID(), rowId);
        materializedEntry.setClassVersionId(classVersionId);

        when(entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(classVersionId, rowId))
                .thenReturn(Optional.empty());
        when(entryRepository.findByClassVersionIdAndLessonIdentityId(eq(classVersionId), any(UUID.class)))
                .thenReturn(Optional.empty());
        when(entryRepository.findByMaterializedLessonId(rowId)).thenReturn(Optional.of(materializedEntry));
        CurriculumLesson row = new CurriculumLesson();
        row.setId(rowId);
        when(lessonRepository.findById(rowId)).thenReturn(Optional.of(row));

        Optional<CurriculumLesson> resolved = service.resolveEffectiveLesson(classVersion, rowId);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(rowId);
    }

    @Test
    void resolveEffectiveLessonShouldDelegateToRepositoryForMasterVersion() {
        CurriculumVersion masterVersion = version(CurriculumScope.MASTER, CurriculumStatus.PUBLISHED);
        UUID refId = UUID.randomUUID();
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(refId);
        when(lessonRepository.findEffectiveLessonReference(masterVersion.getId(), refId))
                .thenReturn(Optional.of(lesson));

        Optional<CurriculumLesson> resolved = service.resolveEffectiveLesson(masterVersion, refId);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(refId);
    }

    @Test
    void resolveEffectiveLessonShouldReturnEmptyForDanglingInheritedEntry() {
        UUID classVersionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CurriculumVersion classVersion = version(CurriculumScope.CLASS, CurriculumStatus.PUBLISHED);
        classVersion.setId(classVersionId);
        classVersion.setCourseId(courseId);
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(classVersion);
        UUID masterId = UUID.randomUUID();
        ClassCurriculumEntry danglingEntry = entry(section, masterId, UUID.randomUUID(), null);
        danglingEntry.setClassVersionId(classVersionId);

        when(entryRepository.findByClassVersionIdAndSourceCurriculumLessonId(classVersionId, masterId))
                .thenReturn(Optional.of(danglingEntry));
        when(versionRepository.findById(classVersionId)).thenReturn(Optional.of(classVersion));
        when(versionRepository.findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                courseId, CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        Optional<CurriculumLesson> resolved = service.resolveEffectiveLesson(classVersion, masterId);

        assertThat(resolved).isEmpty();
    }

    private CurriculumVersion version(CurriculumScope scope, CurriculumStatus status) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setScope(scope);
        version.setStatus(status);
        return version;
    }

    private CurriculumVersion version(CurriculumScope scope, CurriculumStatus status, UUID id) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(id);
        version.setScope(scope);
        version.setStatus(status);
        return version;
    }

    private ClassCurriculumEntry entry(
            CurriculumSection section,
            UUID sourceCurriculumLessonId,
            UUID lessonIdentityId,
            UUID materializedLessonId) {
        ClassCurriculumEntry entry = new ClassCurriculumEntry();
        entry.setId(UUID.randomUUID());
        entry.setSection(section);
        entry.setSourceCurriculumLessonId(sourceCurriculumLessonId);
        entry.setLessonIdentityId(lessonIdentityId);
        entry.setMaterializedLessonId(materializedLessonId);
        return entry;
    }

    private CurriculumSection section(CurriculumVersion version, int sortOrder) {
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(version);
        section.setSortOrder(sortOrder);
        return section;
    }

    private CurriculumLesson materializedLesson(String title) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setTitle(title);
        lesson.setStatus(LessonStatus.PUBLISHED);
        return lesson;
    }

    private FlashcardSet flashcardSet(CurriculumLesson lesson, String title) {
        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCurriculumLessonId(lesson.getId());
        set.setTitle(title);
        set.setIsPublic(false);
        set.setIsOfficial(false);
        return set;
    }

    private FlashcardCard flashcardCard(FlashcardSet set, String frontText, String backText) {
        FlashcardCard card = new FlashcardCard();
        card.setId(UUID.randomUUID());
        card.setFlashcardSet(set);
        card.setFrontText(frontText);
        card.setBackText(backText);
        card.setOrderIndex(0);
        return card;
    }
}
