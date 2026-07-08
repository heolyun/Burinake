package com.burinake.service.impl;

import com.burinake.service.ImageStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class LocalImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorageService.class);

    private final Path rootPath;

    public LocalImageStorageService(Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public StoredImage storeOriginal(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return storeSnapshot(image, imageId, capturedDate);
    }

    @Override
    public StoredImage storeSnapshot(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return store(image, StoragePathBuilder.snapshotPath(image, imageId, capturedDate));
    }

    @Override
    public StoredImage storeFireEvent(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException {
        return store(image, StoragePathBuilder.fireEventPath(image, imageId, capturedDate));
    }

    @Override
    public StoredImage storeReport(MultipartFile file, Long reportId, LocalDate createdDate) throws IOException {
        return store(file, StoragePathBuilder.reportPath(file, reportId, createdDate));
    }

    private StoredImage store(MultipartFile file, String blobPath) throws IOException {
        Path localPath = rootPath.resolve(blobPath);
        try {
            Files.createDirectories(localPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, localPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Stored file locally. blobPath={}, localPath={}, size={}", blobPath, localPath, Files.size(localPath));
            return new StoredImage(
                    blobPath,
                    localPath.toUri().toString(),
                    "LOCAL_FS",
                    "local",
                    file.getContentType(),
                    Files.size(localPath)
            );
        } catch (IOException ex) {
            log.error("Failed to store file locally. blobPath={}, localPath={}", blobPath, localPath, ex);
            throw ex;
        }
    }
}
