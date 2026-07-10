package com.burinake.dto.report;

public record ReportCreateRequest(
        Long issueId,
        String reportMessage,
        String receiver
) {
}
