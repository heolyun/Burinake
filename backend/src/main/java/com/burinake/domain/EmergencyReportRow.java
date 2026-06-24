package com.burinake.domain;

import java.time.OffsetDateTime;

public record EmergencyReportRow(
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
