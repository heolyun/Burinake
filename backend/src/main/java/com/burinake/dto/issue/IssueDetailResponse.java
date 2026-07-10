package com.burinake.dto.issue;

import java.util.List;

public record IssueDetailResponse(
        IssueSummaryResponse issue,
        SnapshotImageResponse triggerImage,
        YoloResultResponse yoloResult,
        List<DetectionBoxResponse> detectionBoxes,
        VlmResultResponse latestVlmResult,
        List<SnapshotImageResponse> timeline
) {
}
