package com.burinake.dto.issue;

import java.time.OffsetDateTime;

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
        OffsetDateTime createdAt
) {
}
