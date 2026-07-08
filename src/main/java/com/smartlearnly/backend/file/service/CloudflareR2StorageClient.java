package com.smartlearnly.backend.file.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.exception.SdkException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.TreeMap;

@Slf4j
@Service
public class CloudflareR2StorageClient implements FileStorageService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StorageProperties storageProperties;
    private S3Client s3Client;
    private final String r2Endpoint;

    public CloudflareR2StorageClient(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.r2Endpoint = storageProperties.getR2Endpoint() != null && !storageProperties.getR2Endpoint().isBlank()
                ? storageProperties.getR2Endpoint()
                : "https://" + storageProperties.getR2AccountId() + ".r2.cloudflarestorage.com";
    }

    private synchronized S3Client getS3Client() {
        if (s3Client == null) {
            s3Client = createS3Client();
        }
        return s3Client;
    }

    private S3Client createS3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageProperties.getR2AccessKeyId(),
                storageProperties.getR2SecretAccessKey()
        );

        String endpoint = storageProperties.getR2Endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://" + storageProperties.getR2AccountId() + ".r2.cloudflarestorage.com";
        }

        return S3Client.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .region(Region.of(storageProperties.getR2Region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Override
    public StoredFile store(String bucket, String objectPath, String contentType, byte[] content) {
        validateConfiguration();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectPath)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();

            PutObjectResponse response = getS3Client().putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(content)
            );

            log.info("R2 upload successful: bucket={}, path={}, etag={}", bucket, objectPath, response.eTag());

        } catch (S3Exception exception) {
            log.warn("R2 storage upload failed for bucket={} path={} status={}",
                    bucket, objectPath, exception.awsErrorDetails().errorCode(), exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "File storage service is unavailable: " + exception.awsErrorDetails().errorMessage()
            );
        } catch (SdkException exception) {
            log.warn("R2 storage upload failed for bucket={} path={} due to network/client error",
                    bucket, objectPath, exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "File storage service is unreachable. Please try again."
            );
        }

        String publicUrl = buildPublicUrl(bucket, objectPath);
        String fileName = objectPath.substring(objectPath.lastIndexOf('/') + 1);
        return new StoredFile(publicUrl, objectPath, fileName, contentType, content.length);
    }

    private void validateConfiguration() {
        if (storageProperties.getR2AccessKeyId() == null || storageProperties.getR2AccessKeyId().isBlank()
                || storageProperties.getR2SecretAccessKey() == null || storageProperties.getR2SecretAccessKey().isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Cloudflare R2 storage is not configured"
            );
        }
    }

    private String buildPublicUrl(String bucket, String objectPath) {
        String baseUrl = resolveBucketPublicUrl(bucket);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Cloudflare R2 public URL is not configured for bucket: " + bucket
            );
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + objectPath;
    }

    private String resolveBucketPublicUrl(String bucket) {
        if (bucket.equals(storageProperties.getCourseThumbnailBucket())) {
            return firstConfigured(storageProperties.getR2CourseThumbnailPublicUrl(), storageProperties.getR2PublicUrl());
        }
        if (bucket.equals(storageProperties.getLessonMaterialBucket())) {
            return firstConfigured(storageProperties.getR2LessonMaterialPublicUrl(), storageProperties.getR2PublicUrl());
        }
        if (bucket.equals(storageProperties.getLessonResourceBucket())) {
            return firstConfigured(storageProperties.getR2LessonResourcePublicUrl(), storageProperties.getR2PublicUrl());
        }
        if (bucket.equals(storageProperties.getQuestionMediaBucket())) {
            return firstConfigured(storageProperties.getR2QuestionMediaPublicUrl(), storageProperties.getR2PublicUrl());
        }

        return storageProperties.getR2PublicUrl();
    }

    private String firstConfigured(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    // ==================== HLS Support Methods ====================

    /**
     * Streams an object to a private R2 bucket without requiring or returning a
     * public URL. The caller retains ownership of the input stream.
     */
    public void putPrivateObject(
            String bucket,
            String key,
            String contentType,
            InputStream content,
            long contentLength
    ) {
        validateConfiguration();
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()
                || content == null || contentLength <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Private R2 upload request is invalid");
        }

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType == null || contentType.isBlank()
                            ? "application/octet-stream"
                            : contentType)
                    .contentLength(contentLength)
                    .build();

            PutObjectResponse response = getS3Client().putObject(
                    request,
                    RequestBody.fromInputStream(content, contentLength)
            );
            log.info("Private R2 upload successful: bucket={}, key={}, etag={}",
                    bucket, key, response.eTag());
        } catch (S3Exception exception) {
            log.warn("Private R2 upload failed for bucket={} key={} status={}",
                    bucket, key, exception.statusCode(), exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Private video storage is unavailable"
            );
        } catch (SdkException exception) {
            log.warn("Private R2 upload failed for bucket={} key={}", bucket, key, exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Private video storage is unreachable"
            );
        }
    }

    /**
     * Gets an object from R2 (server-to-server, no presigning).
     * Used for fetching playlists and encryption keys.
     */
    public byte[] getObject(String bucket, String key) {
        validateConfiguration();

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ResponseInputStream<GetObjectResponse> response = getS3Client().getObject(request)) {
                response.transferTo(buffer);
            }

            log.debug("R2 getObject successful: bucket={}, key={}, size={}",
                    bucket, key, buffer.size());

            return buffer.toByteArray();

        } catch (S3Exception exception) {
            log.warn("R2 getObject failed for bucket={} key={} status={}",
                    bucket, key, exception.awsErrorDetails().errorCode(), exception);
            if ("NoSuchKey".equals(exception.awsErrorDetails().errorCode())) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "HLS content not found"
                );
            }
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Failed to retrieve HLS content: " + exception.awsErrorDetails().errorMessage()
            );
        } catch (SdkException exception) {
            log.warn("R2 getObject failed for bucket={} key={} due to network error",
                    bucket, key, exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Storage service unreachable"
            );
        } catch (IOException exception) {
            log.warn("R2 getObject failed while reading bucket={} key={}",
                    bucket, key, exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Failed to read HLS content"
            );
        }
    }

    /**
     * Generates a presigned URL using Cloudflare R2's signature format.
     * This is compatible with R2's AWS S3-compatible API.
     */
    public String getPresignedUrl(String bucket, String key, int ttlSeconds) {
        validateConfiguration();

        try {
            Instant now = Instant.now();
            String dateTime = now.atOffset(ZoneOffset.UTC).format(DATE_FORMAT);
            String dateOnly = now.atOffset(ZoneOffset.UTC).format(DATE_ONLY_FORMAT);

            // Calculate expiration timestamp
            long expiresTimestamp = now.getEpochSecond() + ttlSeconds;

            // Build canonical request
            String host = storageProperties.getR2AccountId() + ".r2.cloudflarestorage.com";
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");

            // Create credential scope
            String credentialScope = dateOnly + "/auto/default/r2_request";

            // Create string to sign
            String unsignedPayload = "UNSIGNED-PAYLOAD";
            String canonicalRequest = "GET\n"
                    + "/" + key + "\n"
                    + "X-Amz-Algorithm=AWS4-HMAC-SHA256&"
                    + "X-Amz-Credential=" + URLEncoder.encode(credentialScope, StandardCharsets.UTF_8) + "&"
                    + "X-Amz-Date=" + dateTime + "&"
                    + "X-Amz-Expires=" + ttlSeconds + "&"
                    + "X-Amz-SignedHeaders=host\n"
                    + "host\n"
                    + "host\n"
                    + "UNSIGNED-PAYLOAD";

            // Calculate signature
            String signature = calculateR2Signature(
                    "GET",
                    key,
                    "",
                    "host",
                    host,
                    dateTime,
                    dateOnly,
                    canonicalRequest,
                    storageProperties.getR2SecretAccessKey()
            );

            // Build presigned URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(r2Endpoint);
            if (!r2Endpoint.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append(bucket).append("/").append(key);
            urlBuilder.append("?");
            urlBuilder.append("X-Amz-Algorithm=AWS4-HMAC-SHA256");
            urlBuilder.append("&X-Amz-Credential=").append(URLEncoder.encode(credentialScope, StandardCharsets.UTF_8));
            urlBuilder.append("&X-Amz-Date=").append(dateTime);
            urlBuilder.append("&X-Amz-Expires=").append(ttlSeconds);
            urlBuilder.append("&X-Amz-SignedHeaders=host");
            urlBuilder.append("&X-Amz-Signature=").append(signature);

            log.debug("Generated presigned URL: bucket={}, key={}, ttl={}s", bucket, key, ttlSeconds);

            return urlBuilder.toString();

        } catch (Exception exception) {
            log.warn("Failed to generate presigned URL for bucket={} key={}: {}",
                    bucket, key, exception.getMessage(), exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Failed to generate secure URL"
            );
        }
    }

    private String calculateR2Signature(String method, String canonicalUri, String canonicalQueryString,
                                       String signedHeaders, String host, String dateTime, String dateOnly,
                                       String canonicalRequest, String secretKey) throws Exception {

        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateOnly + "/auto/default/r2_request";

        // Create canonical request hash
        String canonicalRequestHash = sha256Hex(canonicalRequest);

        // Create string to sign
        String stringToSign = algorithm + "\n"
                + dateTime + "\n"
                + credentialScope + "\n"
                + canonicalRequestHash;

        // Calculate signature
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateOnly);
        byte[] kRegion = hmacSha256(kDate, "auto");
        byte[] kService = hmacSha256(kRegion, "r2_request");
        byte[] kSigning = hmacSha256(kService, "r2_request");

        byte[] signatureBytes = hmacSha256(kSigning, stringToSign);
        return bytesToHex(signatureBytes);
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Checks if an object exists in R2.
     */
    public boolean objectExists(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            getS3Client().headObject(request);
            return true;
        } catch (S3Exception exception) {
            if ("404".equals(exception.statusCode() + "")) {
                return false;
            }
            throw exception;
        }
    }

    /**
     * Gets the HLS bucket name for video content.
     */
    public String getHlsBucket() {
        return storageProperties.getLessonMaterialBucket();
    }
}

