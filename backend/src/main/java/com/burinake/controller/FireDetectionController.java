package com.burinake.controller;

import com.burinake.dto.FireDetectionResponse;
import com.burinake.dto.ProcessingStatus;
import com.burinake.service.FireDetectionService;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FireDetectionController {

    private final FireDetectionService fireDetectionService;

    public FireDetectionController(FireDetectionService fireDetectionService) {
        this.fireDetectionService = fireDetectionService;
    }

    @PostMapping(value = "/api/v1/fire-detections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FireDetectionResponse> detectFire(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "cctvName", required = false) String cctvName,
            @RequestParam(value = "cctvNum", required = false) String cctvNum,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "capturedAt", required = false) OffsetDateTime capturedAt
    ) {
        FireDetectionResponse response = fireDetectionService.detectFire(image, cctvName, cctvNum, source, capturedAt);

        if (response.status() == ProcessingStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
