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
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
class AzureBlobImageStorageService implements ImageStorageService {

    private final BlobContainerClient containerClient;

    public AzureBlobImageStorageService(AzureStorageProperties azureStorageProperties) {
        if (!StringUtils.hasText(azureStorageProperties.connectionString())) {
            throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING is required");
        }
        if (!StringUtils.hasText(azureStorageProperties.blobContainer())) {
            throw new IllegalStateException("AZURE_BLOB_CONTAINER is required");
        }

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azureStorageProperties.connectionString())
                .buildClient();
        this.containerClient = blobServiceClient.getBlobContainerClient(azureStorageProperties.blobContainer());
        this.containerClient.createIfNotExists();
    }

    @Override
    public StoredImage storeOriginal(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        String blobPath = "fire-events/%d/%02d/%02d/%s/original.jpg".formatted(
                capturedDate.getYear(),
                capturedDate.getMonthValue(),
                capturedDate.getDayOfMonth(),
                imageId
        );

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
}
