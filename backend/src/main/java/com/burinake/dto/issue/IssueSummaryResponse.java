package com.burinake.dto.issue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IssueSummaryResponse(
        Long issueId,
        Long cctvId,
        String cctvName,
        String cctvNum,
        String location,
        Long triggerImageId,
        String issueType,
        String issueStatus,
        OffsetDateTime detectedAt,
        OffsetDateTime lastDetectedAt,
        OffsetDateTime lastYoloAnalyzedAt,
        OffsetDateTime lastVlmAnalyzedAt,
        Boolean latestIsRealFire,
        Integer latestLevel,
        String latestMessage,
        BigDecimal maxBoxAreaRatio,
        BigDecimal lastBoxAreaRatio,
        Integer snapshotCount,
        OffsetDateTime updatedAt
) {
}
