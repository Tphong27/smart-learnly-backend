package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class QuestionMediaImportServiceTest {

    @Mock
    private QuestionMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StorageProperties storageProperties;

    private QuestionMediaImportService service;
    private Question question;
    private HttpClient originalHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        service = new QuestionMediaImportService(
                mediaAttachmentRepository,
                fileStorageService,
                storageProperties
        );
        originalHttpClient = currentHttpClient();
        question = new Question();
        question.setId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        setHttpClient(originalHttpClient);
    }

    @Test
    void validateMediaReferences_returnsEmpty_whenNoMediaUrlsAreProvided() {
        List<String> errors = service.validateMediaReferences(null, Arrays.asList(" ", null));

        assertThat(errors).isEmpty();
    }

    @Test
    void validateMediaReferences_reportsMaxImageCountBeforeParsingUrls() {
        List<String> errors = service.validateMediaReferences(
                List.of("bad-1", "bad-2", "bad-3", "bad-4", "bad-5", "bad-6"),
                null);

        assertThat(errors).containsExactly("A question can have at most 5 images");
    }

    @Test
    void validateMediaReferences_reportsInvalidScheme() {
        List<String> errors = service.validateMediaReferences(
                List.of("ftp://example.com/question.png"),
                null);

        assertThat(errors).singleElement()
                .asString()
                .contains("Image URL is invalid")
                .contains("Media URL must use http or https");
    }

    @Test
    void validateMediaReferences_reportsCredentialsInUrl() {
        List<String> errors = service.validateMediaReferences(
                List.of("https://user:secret@example.com/question.png"),
                null);

        assertThat(errors).singleElement()
                .asString()
                .contains("Media URL must not include credentials");
    }

    @Test
    void validateMediaReferences_reportsDocumentationPlaceholderHost() {
        List<String> errors = service.validateMediaReferences(
                List.of("https://example.com/question.png"),
                null);

        assertThat(errors).singleElement()
                .asString()
                .contains("Image URL is invalid")
                .contains("real, publicly accessible file");
    }

    @Test
    void validateMediaReferences_reportsLocalhostHost() {
        List<String> errors = service.validateMediaReferences(
                null,
                List.of("https://localhost/question.mp3"));

        assertThat(errors).singleElement()
                .asString()
                .contains("Audio URL is invalid")
                .contains("Media URL host is not allowed");
    }

    @Test
    void attachImportedMedia_returnsWithoutStorage_whenNoUrlsAreProvided() {
        service.attachImportedMedia(question, List.of(), List.of(" "), "excel_import");

        verify(mediaAttachmentRepository, never()).countByQuestionIdAndMediaType(
                question.getId(),
                QuestionMediaType.IMAGE);
        verify(fileStorageService, never()).store(null, null, null, null);
    }

    @Test
    void attachImportedMedia_throwsBusinessRuleViolation_whenExistingImagesExceedLimit() {
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.attachImportedMedia(
                question,
                List.of("https://example.com/question.png"),
                null,
                "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("at most 5 images");

        verify(fileStorageService, never()).store(null, null, null, null);
    }

    @Test
    void validateMediaReferences_reportsAudioLimitBeforeParsingUrls() {
        List<String> errors = service.validateMediaReferences(
                null,
                List.of("a", "b", "c", "d"));

        assertThat(errors).containsExactly("A question can have at most 3 audio files");
    }

    @Test
    void validateMediaReferences_reportsMalformedTooLongAndPrivateHosts() {
        List<String> errors = service.validateMediaReferences(
                List.of("", "http://", "https://127.0.0.1/question.png", "https://" + "a".repeat(2040) + ".com/q.png"),
                null);

        assertThat(errors).hasSize(3);
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("Media URL is invalid"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("private or internal address"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("must not exceed 2048"));
    }

    @Test
    void validateMediaReferences_reportsInternalIpv6Host() {
        List<String> errors = service.validateMediaReferences(
                null,
                List.of("https://[fc00::1]/question.mp3"));

        assertThat(errors).singleElement()
                .asString()
                .contains("private or internal address");
    }

    @Test
    void attachImportedMedia_downloadsAndStoresImageUrl() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of("content-length", List.of(String.valueOf(pngBytes().length))), pngBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(1L);
        when(fileStorageService.store(eq("question-media"), any(), eq("image/png"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/imported.png",
                        "questions/imported.png",
                        "stored.png",
                        "image/png",
                        pngBytes().length));

        service.attachImportedMedia(question, List.of(" https://example.com/path/imported.png "), null, "image-import");

        ArgumentCaptor<QuestionMediaAttachment> attachmentCaptor = ArgumentCaptor.forClass(QuestionMediaAttachment.class);
        verify(mediaAttachmentRepository).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getMediaType()).isEqualTo(QuestionMediaType.IMAGE);
        assertThat(attachmentCaptor.getValue().getDisplayOrder()).isEqualTo(2);
        assertThat(attachmentCaptor.getValue().getOriginalFileName()).isEqualTo("imported.png");
        assertThat(attachmentCaptor.getValue().getImportSource()).isEqualTo("image_import");
        verify(fileStorageService).store(eq("question-media"), org.mockito.ArgumentMatchers.contains("/images/"), eq("image/png"), any());
    }

    @Test
    void attachImportedMedia_downloadsAndStoresAudioUrlWithDefaultImportSource() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), mp3Bytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionAudioMaxSize()).thenReturn(DataSize.ofMegabytes(20));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.AUDIO))
                .thenReturn(0L);
        when(fileStorageService.store(eq("question-media"), any(), eq("audio/mpeg"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/audio.mp3",
                        "questions/audio.mp3",
                        "fallback.mp3",
                        "audio/mpeg",
                        mp3Bytes().length));

        service.attachImportedMedia(question, null, List.of("https://example.com"), null);

        ArgumentCaptor<QuestionMediaAttachment> attachmentCaptor = ArgumentCaptor.forClass(QuestionMediaAttachment.class);
        verify(mediaAttachmentRepository).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getMediaType()).isEqualTo(QuestionMediaType.AUDIO);
        assertThat(attachmentCaptor.getValue().getOriginalFileName()).isEqualTo("fallback.mp3");
        assertThat(attachmentCaptor.getValue().getImportSource()).isEqualTo("excel_import");
        verify(fileStorageService).store(eq("question-media"), org.mockito.ArgumentMatchers.contains("/audios/"), eq("audio/mpeg"), any());
    }

    @Test
    void attachImportedMedia_defaultsUnknownImportSourceToExcelImport() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), pngBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(fileStorageService.store(eq("question-media"), any(), eq("image/png"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/image.png",
                        "questions/image.png",
                        "fallback.png",
                        "image/png",
                        pngBytes().length));

        service.attachImportedMedia(question, List.of("https://example.com/image.png"), null, "manual_upload");

        ArgumentCaptor<QuestionMediaAttachment> attachmentCaptor = ArgumentCaptor.forClass(QuestionMediaAttachment.class);
        verify(mediaAttachmentRepository).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getImportSource()).isEqualTo("excel_import");
    }

    @Test
    void attachImportedMedia_followsRedirectBeforeStoring() throws Exception {
        HttpClient httpClient = mockHttpClient(
                response(302, Map.of("location", List.of("https://example.com/final.png")), new byte[]{1}),
                response(200, Map.of(), pngBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(storageProperties.getQuestionMediaBucket()).thenReturn("question-media");
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);
        when(fileStorageService.store(eq("question-media"), any(), eq("image/png"), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "https://cdn.example.com/final.png",
                        "questions/final.png",
                        "fallback.png",
                        "image/png",
                        pngBytes().length));

        service.attachImportedMedia(question, List.of("https://example.com/start"), null, "json_import");

        ArgumentCaptor<QuestionMediaAttachment> attachmentCaptor = ArgumentCaptor.forClass(QuestionMediaAttachment.class);
        verify(mediaAttachmentRepository).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getOriginalFileName()).isEqualTo("final.png");
    }

    @Test
    void attachImportedMedia_throwsInvalidRequest_whenRedirectLocationIsMissing() throws Exception {
        HttpClient httpClient = mockHttpClient(response(302, Map.of(), new byte[]{1}));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/start"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("missing a Location header");
    }

    @Test
    void attachImportedMedia_throwsInvalidRequest_whenRedirectUsesUnsupportedScheme() throws Exception {
        HttpClient httpClient = mockHttpClient(response(302, Map.of("location", List.of("ftp://example.com/final.png")), new byte[]{1}));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/start"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("redirect must use http or https");
    }

    @Test
    void attachImportedMedia_throwsInvalidRequest_whenRedirectLocationIsTooLong() throws Exception {
        HttpClient httpClient = mockHttpClient(response(302, Map.of("location", List.of("https://" + "a".repeat(2040) + ".com/final.png")), new byte[]{1}));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/start"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("redirect is too long");
    }

    @Test
    void attachImportedMedia_throwsInvalidRequest_whenRedirectsExceedLimit() throws Exception {
        HttpClient httpClient = mockHttpClient(
                response(302, Map.of("location", List.of("https://example.com/1.png")), new byte[]{1}),
                response(302, Map.of("location", List.of("https://example.com/2.png")), new byte[]{1}),
                response(302, Map.of("location", List.of("https://example.com/3.png")), new byte[]{1}),
                response(302, Map.of("location", List.of("https://example.com/4.png")), new byte[]{1}));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/start"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("too many redirects");
    }

    @Test
    void attachImportedMedia_throwsExternalUnavailable_whenDownloadReturnsServerError() throws Exception {
        HttpClient httpClient = mockHttpClient(response(500, Map.of(), new byte[]{1}));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/error.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void attachImportedMedia_throwsPayloadTooLarge_whenContentLengthExceedsLimit() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of("content-length", List.of("10")), pngBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofBytes(1));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/large.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void attachImportedMedia_throwsInvalidRequest_whenDownloadedMediaIsEmpty() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), new byte[0]));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/empty.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Downloaded media file is empty");
    }

    @Test
    void attachImportedMedia_throwsPayloadTooLarge_whenStreamExceedsLimit() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), pngBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofBytes(1));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/stream-large.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void attachImportedMedia_throwsUnsupportedMediaType_whenDownloadedContentIsNotAllowed() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), "not-a-real-image".getBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/not-image.txt"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .hasMessageContaining("JPEG, PNG, or WebP");

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void attachImportedMedia_throwsUnsupportedMediaType_whenDownloadedAudioContentIsNotAllowed() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), "not-a-real-audio".getBytes()));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionAudioMaxSize()).thenReturn(DataSize.ofMegabytes(20));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.AUDIO))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, null, List.of("https://example.com/not-audio.txt"), "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .hasMessageContaining("MP3, M4A, or WAV");

        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void attachImportedMedia_throwsExternalUnavailable_whenDownloadedStreamCannotBeRead() throws Exception {
        HttpClient httpClient = mockHttpClient(response(200, Map.of(), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken stream");
            }
        }));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/broken.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessageContaining("Could not read downloaded media file");
    }

    @Test
    void attachImportedMedia_throwsExternalUnavailable_whenHttpClientThrowsIOException() throws Exception {
        HttpClient httpClient = org.mockito.Mockito.mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/network.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessageContaining("Could not download");
    }

    @Test
    void attachImportedMedia_throwsExternalUnavailableAndInterruptsThread_whenHttpClientInterrupted() throws Exception {
        HttpClient httpClient = org.mockito.Mockito.mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));
        setHttpClient(httpClient);
        when(storageProperties.getQuestionImageMaxSize()).thenReturn(DataSize.ofMegabytes(5));
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.attachImportedMedia(question, List.of("https://example.com/interrupted.png"), null, "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessageContaining("interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }

    private HttpClient mockHttpClient(HttpResponse<InputStream>... responses) throws Exception {
        HttpClient httpClient = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.stubbing.OngoingStubbing<HttpResponse<InputStream>> stubbing =
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)));
        for (HttpResponse<InputStream> response : responses) {
            stubbing = stubbing.thenReturn(response);
        }
        return httpClient;
    }

    private HttpResponse<InputStream> response(int status, Map<String, List<String>> headers, byte[] body) {
        return response(status, headers, new ByteArrayInputStream(body));
    }

    private HttpResponse<InputStream> response(int status, Map<String, List<String>> headers, InputStream body) {
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        org.mockito.Mockito.lenient().when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }

    private HttpClient currentHttpClient() throws Exception {
        Field field = QuestionMediaImportService.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(service);
    }

    private void setHttpClient(HttpClient httpClient) throws Exception {
        Field field = QuestionMediaImportService.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(service, httpClient);
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    }

    private byte[] mp3Bytes() {
        return new byte[]{
                'I', 'D', '3', 3, 0, 0, 0, 0, 0, 10,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    }
}
