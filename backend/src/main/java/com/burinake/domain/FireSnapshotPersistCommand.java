package com.burinake.domain;

import java.time.OffsetDateTime;

public record FireSnapshotPersistCommand(
        String cctvName,
        String cctvNum,
        Long imageId,
        String storageProvider,
        String storageContainer,
        String storageKey,
        String imageUrl,
        String contentType,
        Long fileSizeBytes,
        Integer widthPx,
        Integer heightPx,
        OffsetDateTime snapshotTime,
        String source,
        String originalFilename
) {
}
