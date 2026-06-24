package com.burinake.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DetectionBoxRow(
        Long boxId,
        Long yoloResultId,
        Integer boxOrder,
        String detectionType,
        BigDecimal confidence,
        BigDecimal x1,
        BigDecimal y1,
        BigDecimal x2,
        BigDecimal y2,
        BigDecimal x3,
        BigDecimal y3,
        BigDecimal x4,
        BigDecimal y4,
        String coordinateType,
        OffsetDateTime createdAt
) {
}
