package com.burinake.domain;

import java.time.OffsetDateTime;

public record CctvRow(
        Long cctvId,
        String cctvName,
        String cctvNum,
        String location,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
