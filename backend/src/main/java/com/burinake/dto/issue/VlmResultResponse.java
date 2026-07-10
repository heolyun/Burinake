package com.burinake.dto.issue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VlmResultResponse(
        Long vlmResultId,
        Long issueId,
        Integer analysisRound,
        String modelName,
        String modelVersion,
        Boolean isRealFire,
        String fireStart,
        String fireReason,
        String situationSummary,
        Integer level,
        String message,
        BigDecimal confidence,
        String rawResponse,
        OffsetDateTime analyzedAt
) {
}
