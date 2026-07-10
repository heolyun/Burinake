package com.burinake.dto.report;

import java.time.OffsetDateTime;

public record ReportResponse(
        Long reportId,
        Long issueId,
        Long vlmResultId,
        String reportStatus,
        String reportMessage,
        String receiver,
        String approvedBy,
        OffsetDateTime approvedAt,
        OffsetDateTime sentAt,
        String responseCode,
        String responseBody,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
