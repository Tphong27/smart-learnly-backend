package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultFlashcardDocumentTextExtractionService implements FlashcardDocumentTextExtractionService {
    private static final String SOURCE_TYPE_DOCX = "DOCX";
    private static final String SOURCE_TYPE_PDF = "PDF";

    @Override
    public DocumentTextExtractionResult extract(MultipartFile file) {
        String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(fileName);
        byte[] content = readBytes(file);
        String text = switch (extension) {
            case "pdf" -> extractPdfText(content);
            case "docx" -> extractDocxText(content);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported flashcard source file type");
        };
        String sourceType = extension.toUpperCase(Locale.ROOT);
        return new DocumentTextExtractionResult(sourceType, fileName, normalizeExtractedText(text));
    }

    private String extractPdfText(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page += 1) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalizeBlock(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add(text);
                }
            }
            return String.join("\n\n", pages);
        }
        catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "PDF text could not be extracted");
        }
    }

    private String extractDocxText(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> blocks = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                addBlock(blocks, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                addTableBlocks(blocks, table);
            }
            return String.join("\n\n", blocks);
        }
        catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "DOCX text could not be extracted");
        }
    }

    private void addTableBlocks(List<String> blocks, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = normalizeBlock(cell.getText());
                if (!text.isBlank()) {
                    cells.add(text);
                }
                for (XWPFTable nestedTable : cell.getTables()) {
                    addTableBlocks(blocks, nestedTable);
                }
            }
            if (!cells.isEmpty()) {
                blocks.add(String.join(" | ", cells));
            }
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file could not be read");
        }
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is invalid");
        }
        return fileName;
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file must be a DOCX or PDF file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void addBlock(List<String> blocks, String value) {
        String normalized = normalizeBlock(value);
        if (!normalized.isBlank()) {
            blocks.add(normalized);
        }
    }

    private String normalizeExtractedText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            addBlock(blocks, paragraph);
        }
        return String.join("\n\n", blocks);
    }

    private String normalizeBlock(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }
}
