package com.burinake.service;

import com.burinake.domain.CctvRow;
import com.burinake.domain.DetectionBoxRow;
import com.burinake.domain.IssueRow;
import com.burinake.domain.SnapshotImageRow;
import com.burinake.domain.VlmResultRow;
import com.burinake.domain.YoloResultRow;
import com.burinake.dto.issue.DetectionBoxResponse;
import com.burinake.dto.issue.IssueDetailResponse;
import com.burinake.dto.issue.IssueStatusUpdateRequest;
import com.burinake.dto.issue.IssueSummaryResponse;
import com.burinake.dto.issue.SnapshotImageResponse;
import com.burinake.dto.issue.VlmResultResponse;
import com.burinake.dto.issue.YoloResultResponse;
import com.burinake.mapper.CctvMapper;
import com.burinake.mapper.DetectionBoxMapper;
import com.burinake.mapper.IssueMapper;
import com.burinake.mapper.SnapshotImageMapper;
import com.burinake.mapper.VlmResultMapper;
import com.burinake.mapper.YoloResultMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IssueQueryService {

    private static final Set<String> ISSUE_STATUSES = Set.of(
            "CANDIDATE",
            "VLM_ANALYZING",
            "REAL_FIRE",
            "FALSE_ALARM",
            "REPORTED",
            "CLOSED"
    );

    private final IssueMapper issueMapper;
    private final CctvMapper cctvMapper;
    private final SnapshotImageMapper snapshotImageMapper;
    private final YoloResultMapper yoloResultMapper;
    private final DetectionBoxMapper detectionBoxMapper;
    private final VlmResultMapper vlmResultMapper;

    public IssueQueryService(
            IssueMapper issueMapper,
            CctvMapper cctvMapper,
            SnapshotImageMapper snapshotImageMapper,
            YoloResultMapper yoloResultMapper,
            DetectionBoxMapper detectionBoxMapper,
            VlmResultMapper vlmResultMapper
    ) {
        this.issueMapper = issueMapper;
        this.cctvMapper = cctvMapper;
        this.snapshotImageMapper = snapshotImageMapper;
        this.yoloResultMapper = yoloResultMapper;
        this.detectionBoxMapper = detectionBoxMapper;
        this.vlmResultMapper = vlmResultMapper;
    }

    public List<IssueSummaryResponse> findRecent(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 200));
        return issueMapper.findRecent(effectiveLimit).stream()
                .map(this::toSummary)
                .toList();
    }

    public IssueDetailResponse findDetail(Long issueId) {
        IssueRow issue = issueMapper.findById(issueId);
        if (issue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }

        SnapshotImageRow triggerImage = issue.triggerImageId() == null ? null : snapshotImageMapper.findById(issue.triggerImageId());
        YoloResultRow yoloResult = issue.yoloResultId() == null ? null : yoloResultMapper.findById(issue.yoloResultId());
        List<DetectionBoxResponse> detectionBoxes = issue.yoloResultId() == null
                ? List.of()
                : detectionBoxMapper.findByYoloResultId(issue.yoloResultId()).stream().map(this::toDetectionBox).toList();
        VlmResultRow latestVlmResult = vlmResultMapper.findLatestByIssueId(issue.issueId());
        List<SnapshotImageResponse> timeline = snapshotImageMapper.findByIssueId(issue.issueId()).stream()
                .map(this::toSnapshot)
                .toList();

        return new IssueDetailResponse(
                toSummary(issue),
                toSnapshot(triggerImage),
                toYolo(yoloResult),
                detectionBoxes,
                toVlm(latestVlmResult),
                timeline
        );
    }

    public List<VlmResultResponse> findVlmHistory(Long issueId) {
        if (issueMapper.findById(issueId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }
        return vlmResultMapper.findByIssueId(issueId).stream()
                .map(this::toVlm)
                .toList();
    }

    public IssueDetailResponse updateStatus(Long issueId, IssueStatusUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        IssueRow issue = issueMapper.findById(issueId);
        if (issue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }
        String status = normalizeStatus(request.issueStatus());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime notifiedAt = "REPORTED".equals(status) ? now : null;
        int updated = issueMapper.updateManualStatus(
                issueId,
                status,
                request.latestIsRealFire(),
                request.latestLevel(),
                blankToNull(request.latestMessage()),
                notifiedAt,
                now
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }
        return findDetail(issueId);
    }

    private IssueSummaryResponse toSummary(IssueRow issue) {
        CctvRow cctv = issue.cctvId() == null ? null : cctvMapper.findById(issue.cctvId());
        return new IssueSummaryResponse(
                issue.issueId(),
                issue.cctvId(),
                cctv != null ? cctv.cctvName() : null,
                cctv != null ? cctv.cctvNum() : null,
                cctv != null ? cctv.location() : null,
                issue.triggerImageId(),
                issue.issueType(),
                issue.issueStatus(),
                issue.detectedAt(),
                issue.lastDetectedAt(),
                issue.lastYoloAnalyzedAt(),
                issue.lastVlmAnalyzedAt(),
                issue.latestIsRealFire(),
                issue.latestLevel(),
                issue.latestMessage(),
                issue.maxBoxAreaRatio(),
                issue.lastBoxAreaRatio(),
                issue.snapshotCount(),
                issue.updatedAt()
        );
    }

    private SnapshotImageResponse toSnapshot(SnapshotImageRow image) {
        if (image == null) {
            return null;
        }
        return new SnapshotImageResponse(
                image.imageId(),
                image.cctvId(),
                image.storageProvider(),
                image.storageContainer(),
                image.storageKey(),
                image.imageUrl(),
                image.contentType(),
                image.fileSizeBytes(),
                image.widthPx(),
                image.heightPx(),
                image.snapshotTime(),
                image.createdAt()
        );
    }

    private YoloResultResponse toYolo(YoloResultRow row) {
        if (row == null) {
            return null;
        }
        return new YoloResultResponse(
                row.yoloResultId(),
                row.imageId(),
                row.modelName(),
                row.modelVersion(),
                row.analysisRound(),
                row.isFire(),
                row.isSmoke(),
                row.fireConfidence(),
                row.smokeConfidence(),
                row.rawResponse(),
                row.analyzedAt()
        );
    }

    private DetectionBoxResponse toDetectionBox(DetectionBoxRow row) {
        BigDecimal x = row.x1();
        BigDecimal y = row.y1();
        BigDecimal width = row.x2() != null && row.x1() != null ? row.x2().subtract(row.x1()) : null;
        BigDecimal height = row.y3() != null && row.y1() != null ? row.y3().subtract(row.y1()) : null;
        return new DetectionBoxResponse(
                row.boxId(),
                row.yoloResultId(),
                row.boxOrder(),
                row.detectionType(),
                row.confidence(),
                x,
                y,
                width,
                height,
                row.coordinateType()
        );
    }

    private VlmResultResponse toVlm(VlmResultRow row) {
        if (row == null) {
            return null;
        }
        return new VlmResultResponse(
                row.vlmResultId(),
                row.issueId(),
                row.analysisRound(),
                row.modelName(),
                row.modelVersion(),
                row.isRealFire(),
                row.fireStart(),
                row.fireReason(),
                row.situationSummary(),
                row.level(),
                row.message(),
                row.confidence(),
                row.rawResponse(),
                row.analyzedAt()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueStatus is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!ISSUE_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported issueStatus: " + status);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
