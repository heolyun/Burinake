package com.burinake.dto.ai;

public record YoloAnalyzeRequest(
        String imageId,
        String blobPath,
        String imageUrl
) {
}
