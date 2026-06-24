package com.burinake.domain;

import java.time.OffsetDateTime;

public record IssueSnapshotRow(
        Long issueSnapshotId,
        Long issueId,
        Long imageId,
        Integer sequenceNo,
        Integer relativeSeconds,
        Boolean isTriggerImage,
        OffsetDateTime createdAt
) {
}
