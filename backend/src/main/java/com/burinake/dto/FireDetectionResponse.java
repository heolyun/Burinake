package com.burinake.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FireDetectionResponse(
        Long imageId,
        ProcessingStatus status,
        String blobPath,
        boolean fireDetected,
        Double confidence,
        RiskLevel riskLevel,
        YoloResult yoloResult,
        VlmResult vlmResult,
        OffsetDateTime processedAt,
        String errorMessage
) {
    public FireDetectionResponse(
            Long imageId,
            ProcessingStatus status,
            String blobPath,
            boolean fireDetected,
            Double confidence,
            RiskLevel riskLevel,
            YoloResult yoloResult,
            VlmResult vlmResult,
            OffsetDateTime processedAt
    ) {
        this(imageId, status, blobPath, fireDetected, confidence, riskLevel, yoloResult, vlmResult, processedAt, null);
    }

    public static FireDetectionResponse failed(Long imageId, String blobPath, String errorMessage) {
        return new FireDetectionResponse(
                imageId,
                ProcessingStatus.FAILED,
                blobPath,
                false,
                null,
                RiskLevel.UNKNOWN,
                new YoloResult(false, null, List.of()),
                VlmResult.analysisError("파이프라인 처리 실패", errorMessage),
                OffsetDateTime.now(),
                errorMessage
        );
    }
}
