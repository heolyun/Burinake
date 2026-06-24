package com.burinake.domain;

import java.time.OffsetDateTime;

public record IssueRow(
        Long issueId,
        Long cctvId,
        Long triggerImageId,
        Long yoloResultId,
        String issueType,
        String issueStatus,
        OffsetDateTime detectedAt,
        OffsetDateTime vlmInputStartTime,
        OffsetDateTime vlmInputEndTime,
        Boolean latestIsRealFire,
        Integer latestLevel,
        String latestMessage,
        OffsetDateTime vlmAnalyzedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
