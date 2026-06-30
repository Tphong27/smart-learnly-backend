package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.hls.config.HlsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final HlsProperties hlsProperties;
    private final CloudflareR2StorageClient r2Client;
    private final StorageProperties storageProperties;

    private Path tempDir;

    @PostConstruct
    public void init() {
        try {
            tempDir = Files.createTempDirectory("hls-processing-");
            log.info("HLS temp directory: {}", tempDir);
        } catch (IOException e) {
            log.error("Failed to create temp directory", e);
            throw new RuntimeException("Cannot create temp directory for video processing", e);
        }
    }

    /**
     * Represents a quality variant for HLS encoding.
     */
    public record QualityVariant(String name, int width, int height, int videoBitrate, int audioBitrate) {
        public static List<QualityVariant> fromConfig(String qualitiesConfig) {
            List<QualityVariant> variants = new ArrayList<>();
            for (String q : qualitiesConfig.split(",")) {
                String trimmed = q.trim();
                switch (trimmed) {
                    case "480p" -> variants.add(new QualityVariant("480p", 854, 480, 1500, 96));
                    case "720p" -> variants.add(new QualityVariant("720p", 1280, 720, 3000, 128));
                    case "1080p" -> variants.add(new QualityVariant("1080p", 1920, 1080, 5000, 192));
                }
            }
            if (variants.isEmpty()) {
                variants.add(new QualityVariant("720p", 1280, 720, 3000, 128));
            }
            return variants;
        }
    }

    /**
     * Result of HLS processing.
     */
    public record HlsProcessingResult(
            UUID lessonId,
            String r2BasePath,
            List<String> generatedQualities,
            String masterPlaylistPath,
            List<String> processedSteps
    ) {}

    /**
     * Callback interface for progress updates.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int percent, String currentStep);
    }

    private void reportProgress(ProgressCallback callback, int percent, String step) {
        if (callback != null) {
            callback.onProgress(percent, step);
        }
        log.info("HLS Progress: {}% - {}", percent, step);
    }

    /**
     * Processes a raw video file and generates HLS segments, then uploads to R2.
     *
     * @param inputVideoPath Staged source video
     * @param lessonId        The lesson UUID
     * @param fileName        Original filename
     * @return Processing result with R2 paths
     */
    public HlsProcessingResult processVideoToHls(Path inputVideoPath, UUID lessonId, String fileName) {
        return processVideoToHls(inputVideoPath, lessonId, fileName, null);
    }

    /**
     * Processes a raw video file and generates HLS segments, then uploads to R2.
     *
     * @param inputVideoPath Staged source video
     * @param lessonId        The lesson UUID
     * @param fileName        Original filename
     * @param callback        Optional progress callback
     * @return Processing result with R2 paths
     */
    public HlsProcessingResult processVideoToHls(
            Path inputVideoPath,
            UUID lessonId,
            String fileName,
            ProgressCallback callback
    ) {
        List<String> processedSteps = new ArrayList<>();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory(tempDir, lessonId.toString() + "-");
            log.info("Processing video for lesson {} in {}", lessonId, workDir);

            // Step 1: Preparing input file
            reportProgress(callback, 5, "Preparing video file...");
            processedSteps.add("Preparing video file");

            Path inputFile = workDir.resolve("input" + getExtension(fileName));
            Files.copy(inputVideoPath, inputFile, StandardCopyOption.REPLACE_EXISTING);

            List<String> generatedQualities = new ArrayList<>();
            StringBuilder masterPlaylist = new StringBuilder();
            masterPlaylist.append("#EXTM3U\n");
            masterPlaylist.append("#EXT-X-VERSION:3\n");

            List<QualityVariant> variants = QualityVariant.fromConfig(hlsProperties.getQualities());
            int variantCount = variants.size();
            int baseProgress = 10;
            int progressPerVariant = 60 / Math.max(variantCount, 1);

            for (int i = 0; i < variants.size(); i++) {
                QualityVariant variant = variants.get(i);
                int variantStartProgress = baseProgress + (i * progressPerVariant);
                log.info("Encoding {} variant for lesson {}", variant.name(), lessonId);

                // Step 2-4: Encoding variants
                String stepName = "Encoding " + variant.name() + "...";
                reportProgress(callback, variantStartProgress, stepName);
                processedSteps.add(stepName);

                Path qualityDir = workDir.resolve(variant.name());
                Files.createDirectories(qualityDir);

                String hlsPath = processVariant(
                        inputFile,
                        qualityDir,
                        variant,
                        variantStartProgress,
                        variantStartProgress + progressPerVariant,
                        callback
                );

                if (hlsPath != null) {
                    generatedQualities.add(variant.name());

                    int bandwidth = (variant.videoBitrate() + variant.audioBitrate()) * 1000;
                    masterPlaylist.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                            .append(bandwidth)
                            .append(",RESOLUTION=")
                            .append(variant.width())
                            .append("x")
                            .append(variant.height())
                            .append(",NAME=\"")
                            .append(variant.name())
                            .append("\"\n");
                    masterPlaylist.append(variant.name()).append("/playlist.m3u8\n");
                }
            }

            if (generatedQualities.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to generate any HLS variants");
            }

            // Step 5: Creating master playlist
            reportProgress(callback, 75, "Creating master playlist...");
            processedSteps.add("Creating master playlist");

            Path masterPath = workDir.resolve("master.m3u8");
            Files.writeString(masterPath, masterPlaylist.toString());

            String r2BasePath = hlsProperties.getR2BasePath() + "/" + lessonId;

            // Step 6: Uploading to R2
            reportProgress(callback, 80, "Uploading to cloud storage...");
            processedSteps.add("Uploading to cloud storage");

            uploadToR2(workDir, r2BasePath);

            // Step 7: Finalizing
            reportProgress(callback, 95, "Finalizing...");
            processedSteps.add("Finalizing");

            log.info("HLS processing complete for lesson {}. Generated qualities: {}", lessonId, generatedQualities);

            reportProgress(callback, 100, "Complete");
            processedSteps.add("Complete");

            return new HlsProcessingResult(
                    lessonId,
                    r2BasePath,
                    generatedQualities,
                    r2BasePath + "/master.m3u8",
                    processedSteps
            );

        } catch (IOException e) {
            log.error("IO error during video processing for lesson {}", lessonId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process video: " + e.getMessage());
        } finally {
            cleanupDirectory(workDir);
        }
    }

    private String processVariant(
            Path inputFile,
            Path outputDir,
            QualityVariant variant,
            int progressStart,
            int progressEnd,
            ProgressCallback callback
    ) {
        try {
            // Progress callback for FFmpeg output parsing
            final int[] lastReportedProgress = {progressStart};
            final int totalProgressRange = progressEnd - progressStart;

            String[] cmd = {
                    "ffmpeg",
                    "-y",
                    "-i", inputFile.toString(),
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", variant.audioBitrate() + "k",
                    "-vf", "scale=" + variant.width() + ":" + variant.height(),
                    "-hls_time", String.valueOf(hlsProperties.getSegmentDuration()),
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", outputDir.resolve("segment_%03d.ts").toString(),
                    "-f", "hls",
                    outputDir.resolve("playlist.m3u8").toString()
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("frame=") || line.startsWith("size=") || line.startsWith("time=")) {
                        log.debug("ffmpeg [{}]: {}", variant.name(), line);

                        // Parse time= to calculate progress
                        if (line.contains("time=")) {
                            int currentProgress = parseTimeProgress(line, progressStart, totalProgressRange);
                            if (currentProgress > lastReportedProgress[0] && currentProgress <= progressEnd) {
                                lastReportedProgress[0] = currentProgress;
                                reportProgress(callback, currentProgress, "Encoding " + variant.name() + "...");
                            }
                        }
                    }
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                log.warn("FFmpeg timeout for variant {}, killing process", variant.name());
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) {
                log.warn("FFmpeg failed for variant {}: exit code {}", variant.name(), process.exitValue());
                log.debug("FFmpeg output: {}", output);
                return null;
            }

            Path playlistPath = outputDir.resolve("playlist.m3u8");
            if (Files.exists(playlistPath)) {
                reportProgress(callback, progressEnd, variant.name() + " encoded");
                return outputDir.toString();
            }

            return null;

        } catch (IOException e) {
            log.error("Failed to run FFmpeg for variant {}", variant.name(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("FFmpeg interrupted for variant {}", variant.name());
            return null;
        }
    }

    /**
     * Parse time= from FFmpeg output to calculate progress percentage.
     * Format: time=00:01:23.45
     */
    private int parseTimeProgress(String line, int progressStart, int totalRange) {
        try {
            int timeIndex = line.indexOf("time=");
            if (timeIndex == -1) return progressStart;

            String timeStr = line.substring(timeIndex + 5).split("\\s")[0];
            String[] parts = timeStr.split(":");

            if (parts.length >= 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                double totalSeconds = hours * 3600 + minutes * 60 + seconds;

                // Estimate total duration (rough estimate based on typical video length)
                // In production, you might want to pass actual duration
                double estimatedTotalSeconds = 300; // 5 minutes default estimate
                double progress = Math.min(totalSeconds / estimatedTotalSeconds, 0.99);

                return progressStart + (int)(progress * totalRange);
            }
        } catch (Exception e) {
            log.debug("Failed to parse time progress: {}", line);
        }
        return progressStart;
    }

    private void uploadToR2(Path localDir, String r2BasePath) {
        String bucket = storageProperties.getLessonMaterialBucket();

        try (var files = Files.walk(localDir)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(this::isHlsOutputFile)
                    .forEach(file -> {
                try {
                    String relativePath = localDir.relativize(file).toString().replace("\\", "/");
                    String r2Key = r2BasePath + "/" + relativePath;

                    String contentType = getContentType(file.toString());
                    byte[] content = Files.readAllBytes(file);

                    r2Client.store(bucket, r2Key, contentType, content);
                    log.debug("Uploaded to R2: {}", r2Key);

                } catch (IOException e) {
                    log.error("Failed to upload file {} to R2", file, e);
                    throw new RuntimeException("Failed to upload to R2: " + e.getMessage(), e);
                }
            });

            log.info("Uploaded all HLS files to R2 bucket {} with base path {}", bucket, r2BasePath);

        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to upload HLS to R2: " + e.getMessage()
            );
        }
    }

    private boolean isHlsOutputFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".m3u8")
                || fileName.endsWith(".ts")
                || fileName.equals("enc.key");
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (fileName.endsWith(".ts")) return "video/MP2T";
        if (fileName.endsWith(".mp4")) return "video/mp4";
        if (fileName.endsWith(".mp3")) return "audio/mpeg";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : ".mp4";
    }

    private void cleanupDirectory(Path dir) {
        if (dir == null) return;
        try (var paths = Files.walk(dir)) {
            paths
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file: {}", p, e);
                        }
                    });
            log.debug("Cleaned up directory: {}", dir);
        } catch (IOException e) {
            log.warn("Failed to cleanup directory: {}", dir, e);
        }
    }

    /**
     * Checks if FFmpeg is available on the system.
     */
    public boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (IOException | InterruptedException e) {
            log.warn("FFmpeg not available: {}", e.getMessage());
            return false;
        }
    }
}
