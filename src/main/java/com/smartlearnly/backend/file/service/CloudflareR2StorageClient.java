package com.smartlearnly.backend.file.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.exception.SdkException;

@Slf4j
@Service
public class CloudflareR2StorageClient implements FileStorageService {

    private final StorageProperties storageProperties;
    private final S3Client s3Client;

    public CloudflareR2StorageClient(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.s3Client = createS3Client();
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
                        // R2 hoạt động ổn định với path-style access:
                        // https://<account>.r2.cloudflarestorage.com/<bucket>/<key>
                        // Virtual-hosted style (bucket ghép vào hostname) dễ gây
                        // connection reset giữa chừng khi upload tới R2.
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

            PutObjectResponse response = s3Client.putObject(
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
            // Lỗi tầng client/mạng (vd kết nối bị ngắt, timeout) - không phải lỗi nghiệp vụ S3.
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
        String baseUrl = storageProperties.getR2PublicUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fallback: extract account ID from endpoint
            String endpoint = storageProperties.getR2Endpoint();
            if (endpoint != null && endpoint.contains(".r2.cloudflarestorage.com")) {
                baseUrl = endpoint.replace(".cloudflarestorage.com", ".r2.dev");
            } else {
                baseUrl = String.format("https://%s.r2.dev", storageProperties.getR2AccountId());
            }
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        // R2 public URL format: baseUrl + objectPath (without bucket name)
        return baseUrl + objectPath;
    }
}
