package com.burinake.controller;

import com.burinake.domain.SnapshotImageRow;
import com.burinake.mapper.SnapshotImageMapper;
import com.burinake.service.ImageStorageService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SnapshotImageController {

    private final SnapshotImageMapper snapshotImageMapper;
    private final ImageStorageService imageStorageService;

    public SnapshotImageController(SnapshotImageMapper snapshotImageMapper, ImageStorageService imageStorageService) {
        this.snapshotImageMapper = snapshotImageMapper;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping("/api/v1/snapshot-images/{imageId}/content")
    public ResponseEntity<byte[]> findContent(@PathVariable Long imageId) {
        SnapshotImageRow image = snapshotImageMapper.findById(imageId);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "snapshot image not found");
        }
        try {
            byte[] body = imageStorageService.read(image.storageKey());
            MediaType mediaType = image.contentType() == null || image.contentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(image.contentType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(body);
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "snapshot image content not found", ex);
        }
    }
}
