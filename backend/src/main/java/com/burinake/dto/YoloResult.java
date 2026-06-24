package com.burinake.dto;

import java.util.List;

public record YoloResult(
        boolean detected,
        Double confidence,
        List<BoundingBox> boxes
) {
}
