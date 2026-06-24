package com.burinake.service;

import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;

public interface VlmClient {
    VlmResult summarize(
            Long imageId,
            String blobPath,
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
