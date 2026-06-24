package com.burinake.domain;

import java.time.OffsetDateTime;

public record SnapshotImageRow(
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
        String rawMetadata,
        OffsetDateTime createdAt
) {
}
