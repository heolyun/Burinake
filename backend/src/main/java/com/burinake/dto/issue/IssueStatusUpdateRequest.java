package com.burinake.dto.issue;

public record IssueStatusUpdateRequest(
        String issueStatus,
        Boolean latestIsRealFire,
        Integer latestLevel,
        String latestMessage
) {
}
