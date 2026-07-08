package com.burinake.service;

import com.burinake.domain.CctvRow;
import com.burinake.domain.DetectionBoxRow;
import com.burinake.domain.FireEventPersistCommand;
import com.burinake.domain.IssueRow;
import com.burinake.domain.IssueSnapshotRow;
import com.burinake.domain.SnapshotImageRow;
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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FireEventPersistenceService {

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
        YoloResultRow yoloResultRow = new YoloResultRow(
                yoloResultMapper.nextId(),
                command.imageId(),
                "YOLO",
                null,
                1,
                yoloResult.detected(),
                hasSmoke(yoloResult),
                yoloResult.detected() ? asBigDecimal(yoloResult.confidence()) : null,
                hasSmoke(yoloResult) ? asBigDecimal(yoloResult.confidence()) : null,
                toJson(yoloResult),
                now
        );
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
                null,
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

    private boolean hasSmoke(YoloResult yoloResult) {
        return yoloResult.boxes().stream()
                .anyMatch(box -> box.label() != null && box.label().toLowerCase().contains("smoke"));
    }

    private String deriveIssueType(YoloResult yoloResult) {
        boolean hasFire = yoloResult.boxes().stream()
                .anyMatch(box -> box.label() != null && box.label().toLowerCase().contains("fire"));
        boolean hasSmoke = hasSmoke(yoloResult);

        if (hasFire && hasSmoke) {
            return "FIRE_SMOKE";
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

    private BigDecimal asBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
