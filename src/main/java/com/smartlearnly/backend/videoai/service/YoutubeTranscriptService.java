package com.smartlearnly.backend.videoai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YoutubeTranscriptService {

    private static final int MAX_PROCESS_LOG_CHARACTERS = 8_000;

    private final VideoAiProperties properties;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public YoutubeTranscriptService(VideoAiProperties properties) {
        this.properties = properties;
    }

    public TranscriptResult fetchYoutubeTranscript(String videoId) {
        validateRuntime(videoId);
        Path output = null;
        Path processLog = null;
        Process process = null;
        try {
            output = Files.createTempFile("youtube-transcript-", ".json");
            processLog = Files.createTempFile("youtube-transcript-", ".log");
            ProcessBuilder builder = new ProcessBuilder(
                    requiredArgument(properties.getPythonCommand(), "Python command"),
                    properties.getTranscriptScriptPath().toAbsolutePath().normalize().toString(),
                    "--video-id",
                    videoId,
                    "--output",
                    output.toString()
            );
            builder.redirectErrorStream(true);
            builder.redirectOutput(processLog.toFile());
            process = builder.start();

            Duration timeout = normalizedTimeout();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw unavailable("Getting the YouTube transcript timed out");
            }

            if (process.exitValue() != 0) {
                String logOutput = readBoundedLog(processLog);
                log.warn("YouTube transcript process failed with exit code {}: {}",
                        process.exitValue(), logOutput);
                throw transcriptError(logOutput);
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw unavailable("YouTube returned no transcript");
            }
            return parseTranscriptWorkerOutput(output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw unavailable("Getting the YouTube transcript was interrupted");
        } catch (IOException exception) {
            log.warn("Unable to run the YouTube transcript script", exception);
            throw unavailable("YouTube transcript runtime is unavailable");
        } finally {
            deleteTemporaryFile(output);
            deleteTemporaryFile(processLog);
        }
    }

    TranscriptResult parseTranscriptWorkerOutput(Path output)
            throws IOException {
        WorkerResult worker = objectMapper.readValue(output.toFile(), WorkerResult.class);
        if (worker == null || worker.language() == null || worker.language().isBlank()
                || worker.segments() == null || worker.segments().isEmpty()) {
            throw unavailable("YouTube returned an invalid transcript");
        }

        StringBuilder transcript = new StringBuilder();
        for (WorkerSegment segment : worker.segments()) {
            if (segment == null || segment.text() == null || segment.text().isBlank()
                    || !Double.isFinite(segment.start()) || !Double.isFinite(segment.duration())
                    || segment.start() < 0 || segment.duration() <= 0) {
                throw unavailable("YouTube returned an invalid transcript segment");
            }
            String text = segment.text().strip();
            if (!transcript.isEmpty()) {
                transcript.append(' ');
            }
            transcript.append(text);
            if (transcript.length() > properties.getMaxTranscriptCharacters()) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "The video transcript is too long to summarize"
                );
            }
        }
        return new TranscriptResult(
                worker.language().strip().toLowerCase(Locale.ROOT),
                transcript.toString()
        );
    }

    private void validateRuntime(String videoId) {
        if (!properties.isEnabled()) {
            throw unavailable("YouTube summary is disabled");
        }
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "YouTube video ID is invalid");
        }
        Path script = properties.getTranscriptScriptPath();
        if (script == null || !Files.isRegularFile(script) || !Files.isReadable(script)) {
            throw unavailable("YouTube transcript script is unavailable");
        }
    }

    private BusinessException transcriptError(String processLog) {
        String value = processLog == null ? "" : processLog.toUpperCase(Locale.ROOT);
        if (value.contains("TRANSCRIPT_DISABLED") || value.contains("TRANSCRIPT_NOT_FOUND")) {
            return new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This YouTube video does not have an available transcript"
            );
        }
        if (value.contains("VIDEO_UNAVAILABLE")) {
            return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "YouTube video was not found");
        }
        if (value.contains("YOUTUBE_BLOCKED")) {
            return unavailable("YouTube blocked the transcript request; try again later");
        }
        return unavailable("Unable to get the YouTube transcript");
    }

    private Duration normalizedTimeout() {
        Duration timeout = properties.getTranscriptTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Duration.ofSeconds(60);
        }
        return timeout.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : timeout;
    }

    private String requiredArgument(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 256
                || normalized.indexOf('\0') >= 0
                || normalized.contains("\r")
                || normalized.contains("\n")) {
            throw unavailable(label + " is invalid");
        }
        return normalized;
    }

    private String readBoundedLog(Path processLog) throws IOException {
        try (var input = Files.newInputStream(processLog)) {
            return new String(
                    input.readNBytes(MAX_PROCESS_LOG_CHARACTERS),
                    StandardCharsets.UTF_8
            ).strip();
        }
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.debug("Could not delete temporary file {}", path, exception);
        }
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    public record TranscriptResult(String language, String text) {
    }

    private record WorkerResult(String language, List<WorkerSegment> segments) {
    }

    private record WorkerSegment(double start, double duration, String text) {
    }
}
