package com.burinake.service;

import com.burinake.dto.YoloResult;

public interface YoloClient {
    YoloResult analyze(Long imageId, String blobPath, byte[] imageBytes, String contentType, String originalFilename);
}
