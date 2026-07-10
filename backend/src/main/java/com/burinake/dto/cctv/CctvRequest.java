package com.burinake.dto.cctv;

public record CctvRequest(
        String cctvName,
        String cctvNum,
        String location,
        Boolean isActive
) {
}
