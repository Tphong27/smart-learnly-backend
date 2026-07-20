package com.smartlearnly.backend.videoai.transcription;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.video-ai.transcription")
public class FasterWhisperProperties {

    private static final Set<String> DEVICES = Set.of("cpu", "cuda");
    private static final Set<String> COMPUTE_TYPES = Set.of("int8", "int8_float16", "float16", "float32");

    private boolean enabled = false;
    private String provider = "faster-whisper";
    private String pythonCommand = "python3";
    private Path scriptPath = Path.of("scripts", "video-ai", "transcribe.py");
    private String model = "small";
    private String device = "cpu";
    private String computeType = "int8";
    private int cpuThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private int beamSize = 5;
    private Duration timeout = Duration.ofHours(2);
    private Path modelCacheDirectory;

    public String normalizedDevice() {
        String value = device == null ? "" : device.trim().toLowerCase(Locale.ROOT);
        return DEVICES.contains(value) ? value : "cpu";
    }

    public String normalizedComputeType() {
        String value = computeType == null ? "" : computeType.trim().toLowerCase(Locale.ROOT);
        if (!COMPUTE_TYPES.contains(value)) {
            return "cpu".equals(normalizedDevice()) ? "int8" : "float16";
        }
        return value;
    }

    public int normalizedCpuThreads() {
        return Math.max(1, Math.min(64, cpuThreads));
    }

    public int normalizedBeamSize() {
        return Math.max(1, Math.min(10, beamSize));
    }

    public Duration normalizedTimeout() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Duration.ofHours(2);
        }
        return timeout.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : timeout;
    }
}
