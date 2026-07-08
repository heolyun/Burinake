package com.burinake.dto.ai;

import com.burinake.dto.BoundingBox;
import java.util.List;

public record YoloDetectResponse(
        YoloDetectResult result,
        YoloDetectMetadata metadata
) {
    public record YoloDetectResult(
            boolean detected,
            List<BoundingBox> boxes
    ) {
    }

    public record YoloDetectMetadata(
            String imageId,
            String capturedAt
    ) {
    }
}
