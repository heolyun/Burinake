package com.burinake.dto.issue;

import java.math.BigDecimal;

public record DetectionBoxResponse(
        Long boxId,
        Long yoloResultId,
        Integer boxOrder,
        String detectionType,
        BigDecimal confidence,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        String coordinateType
) {
}
