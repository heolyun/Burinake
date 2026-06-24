package com.burinake.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VlmResultRow(
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
