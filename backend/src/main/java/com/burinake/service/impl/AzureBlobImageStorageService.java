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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class AzureBlobImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobImageStorageService.class);

    private final BlobContainerClient containerClient;

    public AzureBlobImageStorageService(AzureStorageProperties azureStorageProperties) {
        if (!StringUtils.hasText(azureStorageProperties.connectionString())) {
            throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING is required for Azure Blob Storage");
        }
        if (!StringUtils.hasText(azureStorageProperties.blobContainer())) {
            throw new IllegalStateException("AZURE_BLOB_CONTAINER is required for Azure Blob Storage");
        }

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azureStorageProperties.connectionString())
                .buildClient();
        this.containerClient = blobServiceClient.getBlobContainerClient(azureStorageProperties.blobContainer());
    }

    @Override
    public StoredImage storeOriginal(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return storeSnapshot(image, imageId, capturedDate);
    }

    @Override
    public StoredImage storeSnapshot(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return upload(image, StoragePathBuilder.snapshotPath(image, imageId, capturedDate));
    }

    @Override
    public StoredImage storeFireEvent(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return upload(image, StoragePathBuilder.fireEventPath(image, imageId, capturedDate));
    }

    @Override
    public StoredImage storeReport(MultipartFile file, Long reportId, LocalDate createdDate) throws IOException {
        return upload(file, StoragePathBuilder.reportPath(file, reportId, createdDate));
    }

    private StoredImage upload(MultipartFile file, String blobPath) throws IOException {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobPath);
            try (InputStream inputStream = file.getInputStream()) {
                blobClient.upload(inputStream, file.getSize(), true);
            }

            if (StringUtils.hasText(file.getContentType())) {
                blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(file.getContentType()));
            }

            log.info("Uploaded file to Azure Blob Storage. container={}, blobPath={}, size={}",
                    containerClient.getBlobContainerName(), blobPath, file.getSize());
            return new StoredImage(
                    blobPath,
                    blobClient.getBlobUrl(),
                    "AZURE_BLOB",
                    containerClient.getBlobContainerName(),
                    file.getContentType(),
                    file.getSize()
            );
        } catch (RuntimeException ex) {
            log.error("Failed to upload file to Azure Blob Storage. container={}, blobPath={}",
                    containerClient.getBlobContainerName(), blobPath, ex);
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to read file for Azure Blob upload. container={}, blobPath={}",
                    containerClient.getBlobContainerName(), blobPath, ex);
            throw ex;
        }
    }
}
