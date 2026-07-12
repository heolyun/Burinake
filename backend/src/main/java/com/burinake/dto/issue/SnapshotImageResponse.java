package com.burinake.dto.issue;

import java.time.OffsetDateTime;
import java.util.List;

public record SnapshotImageResponse(
        Long imageId,
        Long cctvId,
        String storageProvider,
        String storageContainer,
        String storageKey,
        String imageUrl,
        String contentType,
        Long fileSizeBytes,
        Integer widthPx,
        Integer heightPx,
        OffsetDateTime snapshotTime,
        OffsetDateTime createdAt,
        YoloResultResponse yoloResult,
        List<DetectionBoxResponse> detectionBoxes
) {
    public SnapshotImageResponse {
        detectionBoxes = detectionBoxes == null ? List.of() : List.copyOf(detectionBoxes);
    }

    public SnapshotImageResponse(
            Long imageId,
            Long cctvId,
            String storageProvider,
            String storageContainer,
            String storageKey,
            String imageUrl,
            String contentType,
            Long fileSizeBytes,
            Integer widthPx,
            Integer heightPx,
            OffsetDateTime snapshotTime,
            OffsetDateTime createdAt
    ) {
        this(
                imageId,
                cctvId,
                storageProvider,
                storageContainer,
                storageKey,
                imageUrl,
                contentType,
                fileSizeBytes,
                widthPx,
                heightPx,
                snapshotTime,
                createdAt,
                null,
                List.of()
        );
    }
}
