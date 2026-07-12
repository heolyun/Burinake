package com.burinake.service;

import com.burinake.domain.CctvRow;
import com.burinake.domain.DetectionBoxRow;
import com.burinake.domain.FireEventPersistCommand;
import com.burinake.domain.FireSnapshotPersistCommand;
import com.burinake.domain.IssueRow;
import com.burinake.domain.IssueSnapshotRow;
import com.burinake.domain.SnapshotImageRow;
import com.burinake.domain.SnapshotPersistResult;
import com.burinake.domain.VlmResultRow;
import com.burinake.domain.YoloResultRow;
import com.burinake.dto.BoundingBox;
import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import com.burinake.mapper.CctvMapper;
import com.burinake.mapper.DetectionBoxMapper;
import com.burinake.mapper.IssueMapper;
import com.burinake.mapper.IssueSnapshotMapper;
import com.burinake.mapper.SnapshotImageMapper;
import com.burinake.mapper.VlmResultMapper;
import com.burinake.mapper.YoloResultMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FireEventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FireEventPersistenceService.class);
    private final CctvMapper cctvMapper;
    private final SnapshotImageMapper snapshotImageMapper;
    private final YoloResultMapper yoloResultMapper;
    private final DetectionBoxMapper detectionBoxMapper;
    private final IssueMapper issueMapper;
    private final IssueSnapshotMapper issueSnapshotMapper;
    private final VlmResultMapper vlmResultMapper;
    private final ObjectMapper objectMapper;

    public FireEventPersistenceService(
            CctvMapper cctvMapper,
            SnapshotImageMapper snapshotImageMapper,
            YoloResultMapper yoloResultMapper,
            DetectionBoxMapper detectionBoxMapper,
            IssueMapper issueMapper,
            IssueSnapshotMapper issueSnapshotMapper,
            VlmResultMapper vlmResultMapper,
            ObjectMapper objectMapper
    ) {
        this.cctvMapper = cctvMapper;
        this.snapshotImageMapper = snapshotImageMapper;
        this.yoloResultMapper = yoloResultMapper;
        this.detectionBoxMapper = detectionBoxMapper;
        this.issueMapper = issueMapper;
        this.issueSnapshotMapper = issueSnapshotMapper;
        this.vlmResultMapper = vlmResultMapper;
        this.objectMapper = objectMapper;
    }

    public Long nextSnapshotImageId() {
        return snapshotImageMapper.nextId();
    }

    @Transactional
    public SnapshotPersistResult persistSnapshot(FireSnapshotPersistCommand command) {
        long startNanos = System.nanoTime();
        CctvRow cctv = resolveCctv(command.cctvName(), command.cctvNum(), command.source());
        OffsetDateTime now = OffsetDateTime.now();

        SnapshotImageRow snapshotImage = new SnapshotImageRow(
                command.imageId(),
                cctv.cctvId(),
                command.storageProvider(),
                command.storageContainer(),
                command.storageKey(),
                command.imageUrl(),
                command.contentType(),
                command.fileSizeBytes(),
                command.widthPx(),
                command.heightPx(),
                command.snapshotTime(),
                buildRawMetadata(command),
                now
        );
        long insertStartNanos = System.nanoTime();
        snapshotImageMapper.insert(snapshotImage);
        log.info("persist-snapshot-image-complete imageId={} cctvId={} elapsedMs={}", command.imageId(), cctv.cctvId(), elapsedMillis(insertStartNanos));

        long openIssueStartNanos = System.nanoTime();
        IssueRow openIssue = issueMapper.findOpenByCctv(cctv.cctvId(), command.snapshotTime());
        log.info("persist-snapshot-open-issue-complete imageId={} cctvId={} found={} elapsedMs={}",
                command.imageId(), cctv.cctvId(), openIssue != null, elapsedMillis(openIssueStartNanos));
        if (openIssue != null) {
            long touchStartNanos = System.nanoTime();
            attachSnapshot(openIssue.issueId(), command.imageId(), command.snapshotTime(), false, now);
            issueMapper.touchSnapshot(openIssue.issueId(), command.snapshotTime(), now);
            log.info("persist-snapshot-touch-complete imageId={} issueId={} elapsedMs={}",
                    command.imageId(), openIssue.issueId(), elapsedMillis(touchStartNanos));
        }

        log.info("persist-snapshot-finish imageId={} elapsedMs={}", command.imageId(), elapsedMillis(startNanos));
        return new SnapshotPersistResult(cctv, snapshotImage, openIssue);
    }

    @Transactional
    public void persistNewAnalysis(SnapshotPersistResult context, YoloResult yoloResult, VlmResult vlmResult) {
        OffsetDateTime now = OffsetDateTime.now();
        SnapshotImageRow snapshotImage = context.snapshotImage();
        Long yoloResultId = persistYoloResult(snapshotImage.imageId(), yoloResult, now);

        if (!yoloResult.detected()) {
            return;
        }

        String issueType = deriveIssueType(yoloResult);
        BigDecimal boxAreaRatio = calculateBoxAreaRatio(yoloResult, snapshotImage);
        Long issueId = issueMapper.nextId();
        IssueRow issue = new IssueRow(
                issueId,
                context.cctv().cctvId(),
                snapshotImage.imageId(),
                yoloResultId,
                issueType,
                "VLM_ANALYZING",
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                null,
                null,
                null,
                null,
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                null,
                null,
                boxAreaRatio,
                boxAreaRatio,
                1,
                now,
                now
        );
        issueMapper.insert(issue);
        attachSnapshot(issueId, snapshotImage.imageId(), snapshotImage.snapshotTime(), true, now);
        persistVlmResult(issueId, 1, yoloResult, vlmResult, now);
        updateIssueWithVlm(issueId, vlmResult, yoloResult, boxAreaRatio, snapshotImage.snapshotTime(), now);
    }

    @Transactional
    public IssueRow createIssueFromDetection(SnapshotPersistResult context, YoloResult yoloResult) {
        OffsetDateTime now = OffsetDateTime.now();
        SnapshotImageRow snapshotImage = context.snapshotImage();
        Long yoloResultId = persistYoloResult(snapshotImage.imageId(), yoloResult, now);

        if (!yoloResult.detected()) {
            return null;
        }

        String issueType = deriveIssueType(yoloResult);
        BigDecimal boxAreaRatio = calculateBoxAreaRatio(yoloResult, snapshotImage);
        Long issueId = issueMapper.nextId();
        IssueRow issue = new IssueRow(
                issueId,
                context.cctv().cctvId(),
                snapshotImage.imageId(),
                yoloResultId,
                issueType,
                "VLM_ANALYZING",
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                null,
                null,
                null,
                null,
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                null,
                null,
                boxAreaRatio,
                boxAreaRatio,
                1,
                now,
                now
        );
        issueMapper.insert(issue);
        attachSnapshot(issueId, snapshotImage.imageId(), snapshotImage.snapshotTime(), true, now);
        return issue;
    }

    @Transactional
    public void persistVlmAnalysis(IssueRow issue, SnapshotImageRow snapshotImage, YoloResult yoloResult, VlmResult vlmResult) {
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal boxAreaRatio = calculateBoxAreaRatio(yoloResult, snapshotImage);
        persistVlmResult(issue.issueId(), vlmResultMapper.nextAnalysisRound(issue.issueId()), yoloResult, vlmResult, now);
        updateIssueWithVlm(issue.issueId(), vlmResult, yoloResult, boxAreaRatio, snapshotImage.snapshotTime(), now);
    }

    @Transactional
    public void persistExistingYoloAnalysis(IssueRow issue, SnapshotImageRow snapshotImage, YoloResult yoloResult) {
        OffsetDateTime now = OffsetDateTime.now();
        persistYoloResult(snapshotImage.imageId(), yoloResult, now);

        if (!yoloResult.detected()) {
            issueMapper.updateYoloTracking(
                    issue.issueId(),
                    issue.issueType(),
                    issue.issueStatus(),
                    snapshotImage.snapshotTime(),
                    snapshotImage.snapshotTime(),
                    BigDecimal.ZERO,
                    now
            );
            return;
        }

        String issueType = maxIssueType(issue.issueType(), deriveIssueType(yoloResult));
        BigDecimal boxAreaRatio = calculateBoxAreaRatio(yoloResult, snapshotImage);
        issueMapper.updateYoloTracking(
                issue.issueId(),
                issueType,
                issue.issueStatus(),
                snapshotImage.snapshotTime(),
                snapshotImage.snapshotTime(),
                boxAreaRatio,
                now
        );
    }

    @Transactional
    public void markVlmAnalysisStarted(Long issueId, OffsetDateTime snapshotTime) {
        issueMapper.markVlmAnalysisStarted(issueId, snapshotTime, OffsetDateTime.now());
    }

    @Transactional
    public void persistExistingAnalysis(IssueRow issue, SnapshotImageRow snapshotImage, YoloResult yoloResult, VlmResult vlmResult) {
        persistExistingYoloAnalysis(issue, snapshotImage, yoloResult);

        if (vlmResult == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal boxAreaRatio = calculateBoxAreaRatio(yoloResult, snapshotImage);
        persistVlmResult(issue.issueId(), vlmResultMapper.nextAnalysisRound(issue.issueId()), yoloResult, vlmResult, now);
        updateIssueWithVlm(issue.issueId(), vlmResult, yoloResult, boxAreaRatio, snapshotImage.snapshotTime(), now);
    }

    @Transactional
    public void persist(FireEventPersistCommand command) {
        CctvRow cctv = resolveCctv(command.cctvName(), command.cctvNum(), command.source());
        OffsetDateTime now = OffsetDateTime.now();

        SnapshotImageRow snapshotImage = new SnapshotImageRow(
                command.imageId(),
                cctv.cctvId(),
                command.storageProvider(),
                command.storageContainer(),
                command.storageKey(),
                command.imageUrl(),
                command.contentType(),
                command.fileSizeBytes(),
                command.widthPx(),
                command.heightPx(),
                command.snapshotTime(),
                buildRawMetadata(command),
                now
        );
        snapshotImageMapper.insert(snapshotImage);

        YoloResult yoloResult = command.yoloResult();
        YoloResultRow yoloResultRow = toYoloResultRow(command.imageId(), yoloResult, now);
        yoloResultMapper.insert(yoloResultRow);

        int boxOrder = 1;
        for (BoundingBox box : yoloResult.boxes()) {
            DetectionBoxRow detectionBox = toDetectionBoxRow(yoloResultRow.yoloResultId(), box, now, boxOrder++);
            detectionBoxMapper.insert(detectionBox);
        }

        if (!yoloResult.detected()) {
            return;
        }

        Long issueId = issueMapper.nextId();
        IssueRow issue = new IssueRow(
                issueId,
                cctv.cctvId(),
                command.imageId(),
                yoloResultRow.yoloResultId(),
                deriveIssueType(yoloResult),
                "VLM_ANALYZING",
                command.snapshotTime(),
                command.snapshotTime(),
                command.snapshotTime(),
                null,
                null,
                null,
                null,
                command.snapshotTime(),
                now,
                null,
                null,
                calculateBoxAreaRatio(yoloResult, snapshotImage),
                calculateBoxAreaRatio(yoloResult, snapshotImage),
                1,
                now,
                now
        );
        issueMapper.insert(issue);

        issueSnapshotMapper.insert(new IssueSnapshotRow(
                issueSnapshotMapper.nextId(),
                issueId,
                command.imageId(),
                1,
                0,
                true,
                now
        ));

        VlmResult vlmResult = command.vlmResult();
        VlmResultRow vlmResultRow = new VlmResultRow(
                vlmResultMapper.nextId(),
                issueId,
                1,
                "VLM",
                null,
                isRealFire(vlmResult),
                null,
                null,
                vlmResult.summary(),
                mapLevel(vlmResult.riskLevel()),
                vlmResult.recommendedAction(),
                asBigDecimal(yoloResult.confidence()),
                toJson(vlmResult),
                now
        );
        vlmResultMapper.insert(vlmResultRow);

        issueMapper.updateLatest(new IssueRow(
                issueId,
                null,
                null,
                null,
                null,
                isRealFire(vlmResult) ? "REAL_FIRE" : "FALSE_ALARM",
                null,
                null,
                null,
                isRealFire(vlmResult),
                mapLevel(vlmResult.riskLevel()),
                vlmResult.recommendedAction(),
                now,
                now,
                now,
                now,
                null,
                calculateBoxAreaRatio(yoloResult, snapshotImage),
                calculateBoxAreaRatio(yoloResult, snapshotImage),
                null,
                null,
                now
        ));
    }

    private Long persistYoloResult(Long imageId, YoloResult yoloResult, OffsetDateTime now) {
        YoloResultRow yoloResultRow = toYoloResultRow(imageId, yoloResult, now);
        yoloResultMapper.insert(yoloResultRow);

        int boxOrder = 1;
        for (BoundingBox box : yoloResult.boxes()) {
            DetectionBoxRow detectionBox = toDetectionBoxRow(yoloResultRow.yoloResultId(), box, now, boxOrder++);
            detectionBoxMapper.insert(detectionBox);
        }

        return yoloResultRow.yoloResultId();
    }

    private YoloResultRow toYoloResultRow(Long imageId, YoloResult yoloResult, OffsetDateTime now) {
        boolean hasFire = hasFire(yoloResult);
        boolean hasSmoke = hasSmoke(yoloResult);

        return new YoloResultRow(
                yoloResultMapper.nextId(),
                imageId,
                "YOLO",
                null,
                1,
                hasFire,
                hasSmoke,
                hasFire ? confidenceForLabel(yoloResult, "fire") : null,
                hasSmoke ? confidenceForLabel(yoloResult, "smoke") : null,
                toJson(yoloResult),
                now
        );
    }

    private void persistVlmResult(Long issueId, Integer analysisRound, YoloResult yoloResult, VlmResult vlmResult, OffsetDateTime now) {
        VlmResultRow vlmResultRow = new VlmResultRow(
                vlmResultMapper.nextId(),
                issueId,
                analysisRound,
                "VLM",
                null,
                isRealFire(vlmResult),
                null,
                null,
                vlmResult.summary(),
                mapLevel(vlmResult.riskLevel()),
                vlmResult.recommendedAction(),
                asBigDecimal(yoloResult.confidence()),
                toJson(vlmResult),
                now
        );
        vlmResultMapper.insert(vlmResultRow);
    }

    private void updateIssueWithVlm(
            Long issueId,
            VlmResult vlmResult,
            YoloResult yoloResult,
            BigDecimal boxAreaRatio,
            OffsetDateTime analyzedSnapshotTime,
            OffsetDateTime now
    ) {
        boolean analysisError = vlmResult.isAnalysisError();
        issueMapper.updateLatest(new IssueRow(
                issueId,
                null,
                null,
                null,
                null,
                analysisError ? "VLM_ANALYZING" : isRealFire(vlmResult) ? "REAL_FIRE" : "FALSE_ALARM",
                null,
                null,
                null,
                analysisError ? null : isRealFire(vlmResult),
                analysisError ? null : mapLevel(vlmResult.riskLevel()),
                vlmResult.recommendedAction(),
                now,
                null,
                analyzedSnapshotTime,
                analyzedSnapshotTime,
                analysisError ? null : now,
                boxAreaRatio,
                boxAreaRatio,
                null,
                null,
                now
        ));
    }

    private void attachSnapshot(Long issueId, Long imageId, OffsetDateTime snapshotTime, boolean triggerImage, OffsetDateTime now) {
        issueMapper.lockByIdForUpdate(issueId);
        Integer sequenceNo = issueSnapshotMapper.nextSequenceNo(issueId);
        issueSnapshotMapper.insert(new IssueSnapshotRow(
                issueSnapshotMapper.nextId(),
                issueId,
                imageId,
                sequenceNo,
                0,
                triggerImage,
                now
        ));
    }

    private CctvRow resolveCctv(String cctvName, String cctvNum, String source) {
        String resolvedName = StringUtils.hasText(cctvName)
                ? cctvName
                : StringUtils.hasText(source)
                        ? source
                        : "UNKNOWN";
        String resolvedNum = StringUtils.hasText(cctvNum) ? cctvNum : "1";

        CctvRow existing = cctvMapper.findByNameAndNum(resolvedName, resolvedNum);
        if (existing != null) {
            return existing;
        }

        Long cctvId = cctvMapper.nextId();
        CctvRow cctv = new CctvRow(cctvId, resolvedName, resolvedNum, null, true, OffsetDateTime.now(), OffsetDateTime.now());
        cctvMapper.insert(cctv);
        return cctv;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private DetectionBoxRow toDetectionBoxRow(Long yoloResultId, BoundingBox box, OffsetDateTime now, int boxOrder) {
        boolean smoke = box.label() != null && box.label().toLowerCase().contains("smoke");
        String detectionType = smoke ? "SMOKE" : "FIRE";
        BigDecimal x1 = BigDecimal.valueOf(box.x());
        BigDecimal y1 = BigDecimal.valueOf(box.y());
        BigDecimal x2 = BigDecimal.valueOf(box.x() + box.width());
        BigDecimal y2 = BigDecimal.valueOf(box.y());
        BigDecimal x3 = BigDecimal.valueOf(box.x() + box.width());
        BigDecimal y3 = BigDecimal.valueOf(box.y() + box.height());
        BigDecimal x4 = BigDecimal.valueOf(box.x());
        BigDecimal y4 = BigDecimal.valueOf(box.y() + box.height());

        return new DetectionBoxRow(
                detectionBoxMapper.nextId(),
                yoloResultId,
                boxOrder,
                detectionType,
                asBigDecimal(box.score()),
                x1,
                y1,
                x2,
                y2,
                x3,
                y3,
                x4,
                y4,
                "PIXEL",
                now
        );
    }

    private BigDecimal calculateBoxAreaRatio(YoloResult yoloResult, SnapshotImageRow snapshotImage) {
        if (snapshotImage.widthPx() == null || snapshotImage.heightPx() == null) {
            return BigDecimal.ZERO;
        }
        double imageArea = (double) snapshotImage.widthPx() * snapshotImage.heightPx();
        if (imageArea <= 0) {
            return BigDecimal.ZERO;
        }
        double maxArea = yoloResult.boxes().stream()
                .mapToDouble(box -> Math.max(0, box.width()) * Math.max(0, box.height()))
                .max()
                .orElse(0);
        return BigDecimal.valueOf(maxArea / imageArea).setScale(6, RoundingMode.HALF_UP);
    }

    private String maxIssueType(String currentType, String nextType) {
        return typeSeverity(nextType) > typeSeverity(currentType) ? nextType : currentType;
    }

    private int typeSeverity(String issueType) {
        if ("FIRE_SMOKE".equals(issueType)) {
            return 3;
        }
        if ("FIRE".equals(issueType)) {
            return 2;
        }
        if ("SMOKE".equals(issueType)) {
            return 1;
        }
        return 0;
    }

    private boolean hasFire(YoloResult yoloResult) {
        return hasLabel(yoloResult, "fire");
    }

    private boolean hasSmoke(YoloResult yoloResult) {
        return hasLabel(yoloResult, "smoke");
    }

    private boolean hasLabel(YoloResult yoloResult, String label) {
        return yoloResult.boxes().stream()
                .anyMatch(box -> box.label() != null && box.label().toLowerCase().contains(label));
    }

    private BigDecimal confidenceForLabel(YoloResult yoloResult, String label) {
        return yoloResult.boxes().stream()
                .filter(box -> box.label() != null && box.label().toLowerCase().contains(label))
                .map(BoundingBox::score)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .map(this::asBigDecimal)
                .orElse(null);
    }

    private String deriveIssueType(YoloResult yoloResult) {
        boolean hasFire = hasFire(yoloResult);
        boolean hasSmoke = hasSmoke(yoloResult);

        if (hasFire && hasSmoke) {
            return "FIRE_SMOKE";
        }
        if (hasFire) {
            return "FIRE";
        }
        if (hasSmoke) {
            return "SMOKE";
        }
        return "FIRE";
    }

    private boolean isRealFire(VlmResult vlmResult) {
        return vlmResult.fireConfirmed();
    }

    private Integer mapLevel(RiskLevel riskLevel) {
        if (riskLevel == null) {
            return 3;
        }
        return switch (riskLevel) {
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 4;
            case UNKNOWN -> 3;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analysis result", ex);
        }
    }

    private String buildRawMetadata(FireEventPersistCommand command) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", command.source());
        metadata.put("originalFilename", command.originalFilename());
        metadata.put("snapshotTime", command.snapshotTime());
        metadata.put("storageProvider", command.storageProvider());
        metadata.put("storageContainer", command.storageContainer());
        metadata.put("storageKey", command.storageKey());
        metadata.put("imageUrl", command.imageUrl());
        metadata.put("contentType", command.contentType());
        metadata.put("fileSizeBytes", command.fileSizeBytes());
        return toJson(metadata);
    }

    private String buildRawMetadata(FireSnapshotPersistCommand command) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", command.source());
        metadata.put("originalFilename", command.originalFilename());
        metadata.put("snapshotTime", command.snapshotTime());
        metadata.put("storageProvider", command.storageProvider());
        metadata.put("storageContainer", command.storageContainer());
        metadata.put("storageKey", command.storageKey());
        metadata.put("imageUrl", command.imageUrl());
        metadata.put("contentType", command.contentType());
        metadata.put("fileSizeBytes", command.fileSizeBytes());
        return toJson(metadata);
    }

    private BigDecimal asBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
