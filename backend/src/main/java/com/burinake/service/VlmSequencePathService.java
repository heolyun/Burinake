package com.burinake.service;

import com.burinake.mapper.IssueSnapshotMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class VlmSequencePathService {

    private final IssueSnapshotMapper issueSnapshotMapper;

    public VlmSequencePathService(IssueSnapshotMapper issueSnapshotMapper) {
        this.issueSnapshotMapper = issueSnapshotMapper;
    }

    public List<String> resolveRecentSequencePaths(Long issueId, OffsetDateTime snapshotTime, int maxFrames) {
        if (issueId == null || snapshotTime == null || maxFrames <= 0) {
            return List.of();
        }

        List<String> recentDesc = issueSnapshotMapper.findRecentStorageKeys(issueId, snapshotTime, maxFrames);
        if (recentDesc == null || recentDesc.isEmpty()) {
            return List.of();
        }

        List<String> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }
}
