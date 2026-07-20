package com.smartlearnly.backend.videoai.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFasterWhisperTranscriptionService implements VideoTranscriptionService {

    private static final Set<String> LANGUAGES = Set.of("auto", "vi", "en");
    private static final int MAX_PROCESS_LOG_CHARS = 32_000;

    private final FasterWhisperProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public TranscriptionResult transcribe(Path audioFile, String sourceLanguage) {
        validateRuntime(audioFile);
        String language = normalizeLanguage(sourceLanguage);
        Path output = null;
        Path processLogFile = null;
        Process process = null;
        try {
            output = Files.createTempFile("video-ai-transcript-", ".json");
            processLogFile = Files.createTempFile("video-ai-transcript-", ".log");
            List<String> command = buildCommand(audioFile.toRealPath(), output, language);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(processLogFile.toFile());
            process = builder.start();
            Duration timeout = properties.normalizedTimeout();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                throw unavailable("Local transcription timed out");
            }
            if (process.exitValue() != 0) {
                log.warn("faster-whisper process failed with exit code {}: {}",
                        process.exitValue(), readBoundedProcessLog(processLogFile));
                throw unavailable("Local transcription failed");
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw unavailable("Local transcription returned no result");
            }
            return parseResult(output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw unavailable("Local transcription was interrupted");
        } catch (IOException exception) {
            log.warn("Unable to run local faster-whisper", exception);
            throw unavailable("Local transcription runtime is unavailable");
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException exception) {
                    log.debug("Could not delete temporary transcript {}", output, exception);
                }
            }
            if (processLogFile != null) {
                try {
                    Files.deleteIfExists(processLogFile);
                } catch (IOException exception) {
                    log.debug("Could not delete temporary transcription log {}", processLogFile, exception);
                }
            }
        }
    }

    List<String> buildCommand(Path audioFile, Path output, String language) {
        List<String> command = new ArrayList<>();
        command.add(requiredSingleArgument(properties.getPythonCommand(), "Python command"));
        command.add(properties.getScriptPath().toAbsolutePath().normalize().toString());
        command.add("--input");
        command.add(audioFile.toString());
        command.add("--output");
        command.add(output.toString());
        command.add("--model");
        command.add(requiredSingleArgument(properties.getModel(), "Whisper model"));
        command.add("--language");
        command.add(language);
        command.add("--device");
        command.add(properties.normalizedDevice());
        command.add("--compute-type");
        command.add(properties.normalizedComputeType());
        command.add("--cpu-threads");
        command.add(Integer.toString(properties.normalizedCpuThreads()));
        command.add("--beam-size");
        command.add(Integer.toString(properties.normalizedBeamSize()));
        if (properties.getModelCacheDirectory() != null
                && !properties.getModelCacheDirectory().toString().isBlank()) {
            command.add("--download-root");
            command.add(properties.getModelCacheDirectory().toAbsolutePath().normalize().toString());
        }
        return List.copyOf(command);
    }

    private void validateRuntime(Path audioFile) {
        if (!properties.isEnabled() || !"faster-whisper".equalsIgnoreCase(properties.getProvider())) {
            throw unavailable("Local transcription is disabled");
        }
        if (audioFile == null || !Files.isRegularFile(audioFile) || !Files.isReadable(audioFile)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Audio derivative is unavailable");
        }
        Path script = properties.getScriptPath();
        if (script == null || !Files.isRegularFile(script) || !Files.isReadable(script)) {
            throw unavailable("faster-whisper script is unavailable");
        }
    }

    private String normalizeLanguage(String value) {
        String normalized = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Supported languages are auto, vi, and en");
        }
        return normalized;
    }

    private String requiredSingleArgument(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 256
                || normalized.indexOf('\0') >= 0 || normalized.contains("\r") || normalized.contains("\n")) {
            throw unavailable(label + " is invalid");
        }
        return normalized;
    }

    private String readBoundedProcessLog(Path processLogFile) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(processLogFile)) {
            bytes = input.readNBytes(MAX_PROCESS_LOG_CHARS);
        }
        String captured = new String(bytes, StandardCharsets.UTF_8);
        return captured.strip();
    }

    TranscriptionResult parseResult(Path output) throws IOException {
        WorkerResult worker = objectMapper.readValue(output.toFile(), WorkerResult.class);
        return validateAndMap(worker);
    }

    private TranscriptionResult validateAndMap(WorkerResult worker) {
        if (worker == null || worker.language() == null || worker.duration() < 0
                || !Double.isFinite(worker.duration()) || worker.segments() == null) {
            throw unavailable("Local transcription returned an invalid result");
        }
        List<TranscriptionSegment> segments = new ArrayList<>(worker.segments().size());
        long previousStart = -1;
        int mappedIndex = 0;
        for (WorkerSegment segment : worker.segments()) {
            if (segment == null || segment.text() == null || segment.text().isBlank()
                    || !Double.isFinite(segment.start()) || !Double.isFinite(segment.end())
                    || segment.start() < 0 || segment.end() <= segment.start()) {
                throw unavailable("Local transcription returned an invalid segment");
            }
            long startMs = Math.round(segment.start() * 1000);
            long endMs = Math.round(segment.end() * 1000);
            if (startMs < previousStart || endMs <= startMs) {
                throw unavailable("Local transcription segments are out of order");
            }
            segments.add(new TranscriptionSegment(mappedIndex++, startMs, endMs, segment.text().strip()));
            previousStart = startMs;
        }
        double languageProbability = Double.isFinite(worker.languageProbability())
                ? Math.max(0, Math.min(1, worker.languageProbability()))
                : 0;
        return new TranscriptionResult(
                worker.language().strip().toLowerCase(Locale.ROOT),
                languageProbability,
                Math.round(worker.duration() * 1000),
                List.copyOf(segments)
        );
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    private record WorkerResult(
            String language,
            double languageProbability,
            double duration,
            List<WorkerSegment> segments
    ) {
    }

    private record WorkerSegment(int index, double start, double end, String text) {
    }
}
