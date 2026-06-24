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
        OffsetDateTime processedAt
) {
    public static FireDetectionResponse failed(Long imageId, String blobPath) {
        return new FireDetectionResponse(
                imageId,
                ProcessingStatus.FAILED,
                blobPath,
                false,
                null,
                RiskLevel.UNKNOWN,
                new YoloResult(false, null, List.of()),
                new VlmResult("", RiskLevel.UNKNOWN, ""),
                OffsetDateTime.now()
        );
    }
}
