package com.burinake.service.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.burinake.config.AzureStorageProperties;
import com.burinake.service.ImageStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
class AzureBlobImageStorageService implements ImageStorageService {

    private final AzureStorageProperties azureStorageProperties;

    public AzureBlobImageStorageService(AzureStorageProperties azureStorageProperties) {
        if (!StringUtils.hasText(azureStorageProperties.connectionString())) {
            throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING is required");
        }
        if (!StringUtils.hasText(azureStorageProperties.blobContainer())) {
            throw new IllegalStateException("AZURE_BLOB_CONTAINER is required");
        }
        this.azureStorageProperties = azureStorageProperties;
    }

    @Override
    public StoredImage storeOriginal(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        String blobPath = "fire-events/%d/%02d/%02d/%s/original.jpg".formatted(
                capturedDate.getYear(),
                capturedDate.getMonthValue(),
                capturedDate.getDayOfMonth(),
                imageId
        );

        try {
            return uploadToAzure(image, blobPath);
        } catch (Exception ex) {
            return storeLocally(image, blobPath);
        }
    }

    private StoredImage uploadToAzure(MultipartFile image, String blobPath) throws IOException {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azureStorageProperties.connectionString())
                .buildClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(azureStorageProperties.blobContainer());
        containerClient.createIfNotExists();

        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        try (InputStream inputStream = image.getInputStream()) {
            blobClient.upload(inputStream, image.getSize(), true);
        }

        if (StringUtils.hasText(image.getContentType())) {
            blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(image.getContentType()));
        }

        return new StoredImage(
                blobPath,
                blobClient.getBlobUrl(),
                "AZURE_BLOB",
                containerClient.getBlobContainerName(),
                image.getContentType(),
                image.getSize()
        );
    }

    private StoredImage storeLocally(MultipartFile image, String blobPath) throws IOException {
        Path rootPath = Path.of(System.getProperty("java.io.tmpdir"), "burinake-fire-events");
        Path localPath = rootPath.resolve(blobPath);
        Files.createDirectories(localPath.getParent());

        try (InputStream inputStream = image.getInputStream()) {
            Files.copy(inputStream, localPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return new StoredImage(
                blobPath,
                localPath.toUri().toString(),
                "LOCAL_FS",
                "local",
                image.getContentType(),
                Files.size(localPath)
        );
    }
}
