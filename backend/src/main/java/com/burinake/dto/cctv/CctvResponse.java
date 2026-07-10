package com.burinake.dto.cctv;

import java.time.OffsetDateTime;

public record CctvResponse(
        Long cctvId,
        String cctvName,
        String cctvNum,
        String location,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
