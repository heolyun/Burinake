package com.burinake.domain;

public record SnapshotPersistResult(
        CctvRow cctv,
        SnapshotImageRow snapshotImage,
        IssueRow openIssue
) {
}
