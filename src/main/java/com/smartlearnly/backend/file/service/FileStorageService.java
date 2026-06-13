package com.smartlearnly.backend.file.service;

public interface FileStorageService {
    StoredFile store(String bucket, String objectPath, String contentType, byte[] content);

    record StoredFile(String url, String objectPath, String fileName, String contentType, long size) {
    }
}
