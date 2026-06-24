package com.burinake.dto.ai;

import com.burinake.dto.YoloResult;

public record VlmSummarizeRequest(
        String imageId,
        String blobPath,
        YoloResult yoloResult
) {
}
