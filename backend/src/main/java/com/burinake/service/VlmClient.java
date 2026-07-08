package com.burinake.service;

import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import java.time.OffsetDateTime;

public interface VlmClient {
    default VlmResult summarize(
            Long imageId,
            String blobPath,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    ) {
        return summarize(
                imageId,
                blobPath,
                null,
                null,
                null,
                null,
                yoloResult,
                imageBytes,
                contentType,
                originalFilename
        );
    }

    VlmResult summarize(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    );

    default RiskLevel defaultRiskLevel(YoloResult yoloResult) {
        if (yoloResult.detected()) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.LOW;
    }
}
