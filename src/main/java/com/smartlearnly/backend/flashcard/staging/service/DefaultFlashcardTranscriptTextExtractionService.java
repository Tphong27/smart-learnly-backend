package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTranscriptTextExtractionService.TranscriptTextExtractionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultFlashcardTranscriptTextExtractionService implements FlashcardTranscriptTextExtractionService {
    private static final Pattern TIMESTAMP_LINE = Pattern.compile(
            "^(?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3}\\s+-->\\s+(?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3}.*$"
    );
    private static final Pattern CUE_NUMBER = Pattern.compile("^\\d+$");
    private static final Pattern SIMPLE_TAG = Pattern.compile("<[^>]+>");

    @Override
    public TranscriptTextExtractionResult extractRaw(String transcriptText, String sourceName) {
        return new TranscriptTextExtractionResult(normalizeNullable(sourceName), clean(transcriptText));
    }

    @Override
    public TranscriptTextExtractionResult extractFile(MultipartFile file) {
        String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String content = readText(file);
        return new TranscriptTextExtractionResult(fileName, clean(content));
    }

    String clean(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = stripBom(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ');
        List<String> blocks = new ArrayList<>();
        StringBuilder currentBlock = new StringBuilder();
        boolean headerSkipped = false;
        for (String rawLine : normalized.split("\n")) {
            String line = normalizeLine(rawLine);
            if (line.isBlank()) {
                flushBlock(blocks, currentBlock);
                continue;
            }
            if (!headerSkipped && line.toUpperCase(Locale.ROOT).startsWith("WEBVTT")) {
                headerSkipped = true;
                flushBlock(blocks, currentBlock);
                continue;
            }
            headerSkipped = true;
            if (CUE_NUMBER.matcher(line).matches()) {
                flushBlock(blocks, currentBlock);
                continue;
            }
            if (TIMESTAMP_LINE.matcher(line).matches()) {
                flushBlock(blocks, currentBlock);
                continue;
            }
            line = SIMPLE_TAG.matcher(line).replaceAll("");
            line = normalizeLine(line);
            if (line.isBlank()) {
                flushBlock(blocks, currentBlock);
                continue;
            }
            if (!currentBlock.isEmpty()) {
                currentBlock.append(' ');
            }
            currentBlock.append(line);
        }
        flushBlock(blocks, currentBlock);
        return String.join("\n\n", blocks);
    }

    private void flushBlock(List<String> blocks, StringBuilder currentBlock) {
        String block = normalizeLine(currentBlock.toString());
        if (!block.isBlank()) {
            blocks.add(block);
        }
        currentBlock.setLength(0);
    }

    private String readText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file could not be read");
        }
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file name is invalid");
        }
        return fileName;
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
