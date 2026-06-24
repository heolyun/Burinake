package com.burinake.service;

import java.io.IOException;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    StoredImage storeOriginal(MultipartFile image, Long imageId, LocalDate capturedDate) throws IOException;

    record StoredImage(
            String blobPath,
            String blobUrl,
            String storageProvider,
            String storageContainer,
            String contentType,
            long fileSize
    ) {
    }
}
