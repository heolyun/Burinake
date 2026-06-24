package com.burinake.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record YoloResultRow(
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
