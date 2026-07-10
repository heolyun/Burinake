package com.burinake.dto.report;

public record ReportStatusUpdateRequest(
        String reportStatus,
        String reportMessage,
        String approvedBy,
        String responseCode,
        String responseBody
) {
}
