package com.burinake.dto.issue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record YoloResultResponse(
        Long yoloResultId,
        Long imageId,
        String modelName,
        String modelVersion,
        Integer analysisRound,
        Boolean isFire,
        Boolean isSmoke,
        BigDecimal fireConfidence,
        BigDecimal smokeConfidence,
        String rawResponse,
        OffsetDateTime analyzedAt
) {
}
