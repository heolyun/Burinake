package com.burinake.service;

import com.burinake.dto.YoloResult;
import java.time.OffsetDateTime;

public interface YoloClient {
    YoloResult analyze(
            Long imageId,
            String blobPath,
            OffsetDateTime capturedAt,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    );
}
