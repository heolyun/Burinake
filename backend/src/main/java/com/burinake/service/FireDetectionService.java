package com.burinake.service;

import com.burinake.dto.FireDetectionResponse;
import java.time.OffsetDateTime;
import org.springframework.web.multipart.MultipartFile;

public interface FireDetectionService {
    FireDetectionResponse detectFire(
            MultipartFile image,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt
    );
}
