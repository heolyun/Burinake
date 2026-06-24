package com.burinake.service.impl;

import com.burinake.domain.FireEventPersistCommand;
import com.burinake.dto.FireDetectionResponse;
import com.burinake.dto.ProcessingStatus;
import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import com.burinake.service.FireDetectionService;
import com.burinake.service.FireEventPersistenceService;
import com.burinake.service.ImageStorageService;
import com.burinake.service.VlmClient;
import com.burinake.service.YoloClient;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DefaultFireDetectionService implements FireDetectionService {

    private final FireEventPersistenceService fireEventPersistenceService;
    private final ImageStorageService imageStorageService;
    private final YoloClient yoloClient;
    private final VlmClient vlmClient;

    public DefaultFireDetectionService(
            FireEventPersistenceService fireEventPersistenceService,
            ImageStorageService imageStorageService,
            YoloClient yoloClient,
            VlmClient vlmClient
    ) {
        this.fireEventPersistenceService = fireEventPersistenceService;
        this.imageStorageService = imageStorageService;
        this.yoloClient = yoloClient;
        this.vlmClient = vlmClient;
    }

    @Override
    public FireDetectionResponse detectFire(
            MultipartFile image,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt
    ) {
        validateImage(image);

        Long imageId = fireEventPersistenceService.nextSnapshotImageId();
        LocalDate capturedDate = capturedAt != null
                ? capturedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        try {
            byte[] imageBytes = image.getBytes();
            String contentType = StringUtils.hasText(image.getContentType())
                    ? image.getContentType()
                    : "application/octet-stream";
            String originalFilename = StringUtils.hasText(image.getOriginalFilename())
                    ? image.getOriginalFilename()
                    : "image";

            ImageStorageService.StoredImage storedImage = imageStorageService.storeOriginal(image, imageId, capturedDate);
            YoloResult yoloResult = yoloClient.analyze(imageId, storedImage.blobPath(), imageBytes, contentType, originalFilename);
            VlmResult vlmResult = vlmClient.summarize(
                    imageId,
                    storedImage.blobPath(),
                    yoloResult,
                    imageBytes,
                    contentType,
                    originalFilename
            );

            fireEventPersistenceService.persist(new FireEventPersistCommand(
                    cctvName,
                    cctvNum,
                    imageId,
                    storedImage.storageProvider(),
                    storedImage.storageContainer(),
                    storedImage.blobPath(),
                    storedImage.blobUrl(),
                    storedImage.contentType(),
                    storedImage.fileSize(),
                    null,
                    null,
                    capturedAt != null ? capturedAt : OffsetDateTime.now(),
                    source,
                    originalFilename,
                    yoloResult,
                    vlmResult
            ));

            return new FireDetectionResponse(
                    imageId,
                    ProcessingStatus.COMPLETED,
                    storedImage.blobPath(),
                    yoloResult.detected(),
                    yoloResult.confidence(),
                    vlmResult.riskLevel(),
                    yoloResult,
                    vlmResult,
                    OffsetDateTime.now()
            );
        } catch (IOException ex) {
            return FireDetectionResponse.failed(imageId, buildBlobPath(imageId, capturedDate));
        } catch (Exception ex) {
            return FireDetectionResponse.failed(imageId, buildBlobPath(imageId, capturedDate));
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is required");
        }
    }

    private String buildBlobPath(Long imageId, LocalDate capturedDate) {
        return "fire-events/%d/%02d/%02d/%s/original.jpg".formatted(
                capturedDate.getYear(),
                capturedDate.getMonthValue(),
                capturedDate.getDayOfMonth(),
                imageId
        );
    }
}