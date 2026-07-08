package com.burinake.service.impl;

import com.burinake.domain.FireSnapshotPersistCommand;
import com.burinake.domain.IssueRow;
import com.burinake.domain.SnapshotPersistResult;
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
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DefaultFireDetectionService implements FireDetectionService {

    private static final Duration OPEN_ISSUE_YOLO_INTERVAL = Duration.ofSeconds(10);
    private static final Duration OPEN_ISSUE_VLM_INTERVAL = Duration.ofSeconds(30);
    private static final BigDecimal BOX_AREA_ESCALATION_MULTIPLIER = BigDecimal.valueOf(2.0);
    private static final BigDecimal BOX_AREA_ABSOLUTE_TRIGGER = BigDecimal.valueOf(0.10);

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
        OffsetDateTime snapshotTime = capturedAt != null ? capturedAt : OffsetDateTime.now();
        LocalDate capturedDate = snapshotTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

        try {
            byte[] imageBytes = image.getBytes();
            String contentType = StringUtils.hasText(image.getContentType())
                    ? image.getContentType()
                    : "application/octet-stream";
            String originalFilename = StringUtils.hasText(image.getOriginalFilename())
                    ? image.getOriginalFilename()
                    : "image";
            ImageDimensions imageDimensions = readImageDimensions(imageBytes);

            ImageStorageService.StoredImage storedImage = imageStorageService.storeOriginal(image, imageId, capturedDate);
            SnapshotPersistResult snapshotContext = fireEventPersistenceService.persistSnapshot(new FireSnapshotPersistCommand(
                    cctvName,
                    cctvNum,
                    imageId,
                    storedImage.storageProvider(),
                    storedImage.storageContainer(),
                    storedImage.blobPath(),
                    storedImage.blobUrl(),
                    storedImage.contentType(),
                    storedImage.fileSize(),
                    imageDimensions.width(),
                    imageDimensions.height(),
                    snapshotTime,
                    source,
                    originalFilename
            ));

            IssueRow openIssue = snapshotContext.openIssue();
            if (openIssue != null && !shouldAnalyzeYolo(openIssue, snapshotTime)) {
                return new FireDetectionResponse(
                        imageId,
                        ProcessingStatus.UPLOADED,
                        storedImage.blobPath(),
                        false,
                        null,
                        RiskLevel.UNKNOWN,
                        new YoloResult(false, null, java.util.List.of()),
                        new VlmResult("", RiskLevel.UNKNOWN, ""),
                        OffsetDateTime.now()
                );
            }

            YoloResult yoloResult = yoloClient.analyze(
                    imageId,
                    storedImage.blobPath(),
                    snapshotTime,
                    imageBytes,
                    contentType,
                    originalFilename
            );
            VlmResult vlmResult = null;

            if (openIssue == null) {
                if (yoloResult.detected()) {
                    vlmResult = summarize(imageId, storedImage, yoloResult, imageBytes, contentType, originalFilename);
                } else {
                    vlmResult = new VlmResult("", RiskLevel.LOW, "");
                }
                fireEventPersistenceService.persistNewAnalysis(snapshotContext, yoloResult, vlmResult);
            } else {
                boolean shouldAnalyzeVlm = yoloResult.detected() && shouldAnalyzeVlm(openIssue, yoloResult, imageDimensions, snapshotTime);
                if (shouldAnalyzeVlm) {
                    vlmResult = summarize(imageId, storedImage, yoloResult, imageBytes, contentType, originalFilename);
                }
                fireEventPersistenceService.persistExistingAnalysis(openIssue, snapshotContext.snapshotImage(), yoloResult, vlmResult);
            }

            return new FireDetectionResponse(
                    imageId,
                    ProcessingStatus.COMPLETED,
                    storedImage.blobPath(),
                    yoloResult.detected(),
                    yoloResult.confidence(),
                    vlmResult != null ? vlmResult.riskLevel() : RiskLevel.UNKNOWN,
                    yoloResult,
                    vlmResult != null ? vlmResult : new VlmResult("", RiskLevel.UNKNOWN, ""),
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

    private VlmResult summarize(
            Long imageId,
            ImageStorageService.StoredImage storedImage,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    ) {
        return vlmClient.summarize(
                imageId,
                storedImage.blobPath(),
                yoloResult,
                imageBytes,
                contentType,
                originalFilename
        );
    }

    private boolean shouldAnalyzeYolo(IssueRow openIssue, OffsetDateTime snapshotTime) {
        return openIssue.lastYoloAnalyzedAt() == null
                || Duration.between(openIssue.lastYoloAnalyzedAt(), snapshotTime).compareTo(OPEN_ISSUE_YOLO_INTERVAL) >= 0;
    }

    private boolean shouldAnalyzeVlm(IssueRow openIssue, YoloResult yoloResult, ImageDimensions imageDimensions, OffsetDateTime snapshotTime) {
        BigDecimal currentAreaRatio = calculateBoxAreaRatio(yoloResult, imageDimensions);
        return isIssueTypeEscalated(openIssue.issueType(), deriveIssueType(yoloResult))
                || isBoxAreaEscalated(openIssue.maxBoxAreaRatio(), currentAreaRatio)
                || openIssue.lastVlmAnalyzedAt() == null
                || Duration.between(openIssue.lastVlmAnalyzedAt(), snapshotTime).compareTo(OPEN_ISSUE_VLM_INTERVAL) >= 0;
    }

    private boolean isIssueTypeEscalated(String currentType, String nextType) {
        return typeSeverity(nextType) > typeSeverity(currentType);
    }

    private boolean isBoxAreaEscalated(BigDecimal previousMaxAreaRatio, BigDecimal currentAreaRatio) {
        if (currentAreaRatio.compareTo(BOX_AREA_ABSOLUTE_TRIGGER) >= 0) {
            return true;
        }
        if (previousMaxAreaRatio == null || previousMaxAreaRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return currentAreaRatio.compareTo(previousMaxAreaRatio.multiply(BOX_AREA_ESCALATION_MULTIPLIER)) >= 0;
    }

    private BigDecimal calculateBoxAreaRatio(YoloResult yoloResult, ImageDimensions imageDimensions) {
        if (imageDimensions.width() == null || imageDimensions.height() == null) {
            return BigDecimal.ZERO;
        }
        double imageArea = (double) imageDimensions.width() * imageDimensions.height();
        if (imageArea <= 0) {
            return BigDecimal.ZERO;
        }
        double maxArea = yoloResult.boxes().stream()
                .mapToDouble(box -> Math.max(0, box.width()) * Math.max(0, box.height()))
                .max()
                .orElse(0);
        return BigDecimal.valueOf(maxArea / imageArea).setScale(6, RoundingMode.HALF_UP);
    }

    private String deriveIssueType(YoloResult yoloResult) {
        boolean hasFire = yoloResult.boxes().stream()
                .anyMatch(box -> box.label() != null && box.label().toLowerCase().contains("fire"));
        boolean hasSmoke = yoloResult.boxes().stream()
                .anyMatch(box -> box.label() != null && box.label().toLowerCase().contains("smoke"));

        if (hasFire && hasSmoke) {
            return "FIRE_SMOKE";
        }
        if (hasSmoke) {
            return "SMOKE";
        }
        if (hasFire) {
            return "FIRE";
        }
        return "NONE";
    }

    private int typeSeverity(String issueType) {
        if ("FIRE_SMOKE".equals(issueType)) {
            return 3;
        }
        if ("FIRE".equals(issueType)) {
            return 2;
        }
        if ("SMOKE".equals(issueType)) {
            return 1;
        }
        return 0;
    }

    private ImageDimensions readImageDimensions(byte[] imageBytes) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                return new ImageDimensions(null, null);
            }
            return new ImageDimensions(bufferedImage.getWidth(), bufferedImage.getHeight());
        } catch (IOException ex) {
            return new ImageDimensions(null, null);
        }
    }

    private record ImageDimensions(Integer width, Integer height) {
    }
}
