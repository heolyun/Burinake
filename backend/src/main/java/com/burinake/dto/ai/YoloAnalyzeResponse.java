package com.burinake.dto.ai;

import com.burinake.dto.BoundingBox;
import java.util.List;

public record YoloAnalyzeResponse(
        boolean detected,
        Double confidence,
        List<BoundingBox> boxes
) {
}
