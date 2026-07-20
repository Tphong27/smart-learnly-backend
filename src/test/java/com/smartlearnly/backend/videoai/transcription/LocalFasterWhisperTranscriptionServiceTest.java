package com.smartlearnly.backend.videoai.transcription;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFasterWhisperTranscriptionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void buildCommandKeepsPathsAsSingleArgumentsWithoutShellExpansion() throws Exception {
        FasterWhisperProperties properties = properties();
        Path script = tempDirectory.resolve("transcribe.py");
        Files.writeString(script, "# test");
        properties.setScriptPath(script);
        LocalFasterWhisperTranscriptionService service = service(properties);
        Path input = tempDirectory.resolve("lesson; echo injected.mp3");
        Path output = tempDirectory.resolve("result $(unsafe).json");

        List<String> command = service.buildCommand(input, output, "vi");

        assertThat(command).containsSequence("--input", input.toString());
        assertThat(command).containsSequence("--output", output.toString());
        assertThat(command).containsSequence("--language", "vi");
        assertThat(command).doesNotContain("cmd", "/c", "sh", "-c");
    }

    @Test
    void parseResultMapsSecondsToValidatedMilliseconds() throws Exception {
        FasterWhisperProperties properties = properties();
        LocalFasterWhisperTranscriptionService service = service(properties);
        Path output = tempDirectory.resolve("result.json");
        Files.writeString(output, """
                {
                  "language":"vi",
                  "languageProbability":0.987,
                  "duration":3.75,
                  "segments":[
                    {"index":0,"start":0.125,"end":1.5,"text":" Xin chào "},
                    {"index":1,"start":1.5,"end":3.75,"text":"bài học"}
                  ]
                }
                """);

        VideoTranscriptionService.TranscriptionResult result = service.parseResult(output);

        assertThat(result.language()).isEqualTo("vi");
        assertThat(result.durationMs()).isEqualTo(3_750);
        assertThat(result.segments()).containsExactly(
                new VideoTranscriptionService.TranscriptionSegment(0, 125, 1_500, "Xin chào"),
                new VideoTranscriptionService.TranscriptionSegment(1, 1_500, 3_750, "bài học")
        );
        assertThat(result.fullText()).isEqualTo("Xin chào bài học");
    }

    private FasterWhisperProperties properties() {
        FasterWhisperProperties properties = new FasterWhisperProperties();
        properties.setEnabled(true);
        properties.setPythonCommand("python3");
        properties.setModel("small");
        return properties;
    }

    private LocalFasterWhisperTranscriptionService service(FasterWhisperProperties properties) {
        return new LocalFasterWhisperTranscriptionService(properties, new ObjectMapper());
    }
}
