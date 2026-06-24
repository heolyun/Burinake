package com.burinake.domain;

import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import java.time.OffsetDateTime;

public record FireEventPersistCommand(
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
        String originalFilename,
        YoloResult yoloResult,
        VlmResult vlmResult
) {
}
