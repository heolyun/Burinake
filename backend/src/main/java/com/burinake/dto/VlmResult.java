package com.burinake.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VlmResult(
        boolean fireConfirmed,
        Double confidence,
        DetectedBbox detectedBbox,
        List<TimelineSummaryItem> timelineSummary,
        VisualCause visualCause,
        FireLocationDetail fireLocationDetail,
        RiskAssessment riskAssessment,
        List<String> recommendedActions,
        String notes,
        String emergencyReportKoreanNarrative
) {
    public VlmResult {
        timelineSummary = timelineSummary == null ? List.of() : List.copyOf(timelineSummary);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }

    public VlmResult(String summary, RiskLevel riskLevel, String recommendedAction) {
        this(
                riskLevel == RiskLevel.HIGH,
                defaultConfidence(riskLevel),
                new DetectedBbox(0.0, 0.0, 0.0, 0.0, "unknown", "legacy compatibility"),
                List.of(),
                new VisualCause(
                        StringUtils.hasText(summary) ? summary : "",
                        List.of(),
                        StringUtils.hasText(summary) ? summary : ""
                ),
                new FireLocationDetail("", "", "", ""),
                new RiskAssessment(
                        riskLevel == null ? RiskLevel.UNKNOWN.name() : riskLevel.name(),
                        StringUtils.hasText(summary) ? summary : "",
                        StringUtils.hasText(summary) ? summary : "",
                        StringUtils.hasText(summary) ? summary : ""
                ),
                StringUtils.hasText(recommendedAction)
                        ? List.of(recommendedAction)
                        : List.of(),
                StringUtils.hasText(summary) ? summary : "",
                StringUtils.hasText(summary) ? summary : ""
        );
    }

    public RiskLevel derivedRiskLevel() {
        if (riskAssessment == null || !StringUtils.hasText(riskAssessment.level())) {
            return fireConfirmed ? RiskLevel.HIGH : RiskLevel.UNKNOWN;
        }

        String normalized = riskAssessment.level().trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("HIGH")) {
            return RiskLevel.HIGH;
        }
        if (normalized.contains("MEDIUM")) {
            return RiskLevel.MEDIUM;
        }
        if (normalized.contains("LOW")) {
            return RiskLevel.LOW;
        }
        return fireConfirmed ? RiskLevel.HIGH : RiskLevel.UNKNOWN;
    }

    public String primaryNarrative() {
        if (StringUtils.hasText(emergencyReportKoreanNarrative)) {
            return emergencyReportKoreanNarrative;
        }
        if (StringUtils.hasText(notes)) {
            return notes;
        }
        return "";
    }

    public String joinedRecommendedActions() {
        if (recommendedActions.isEmpty()) {
            return "";
        }
        return String.join("\n", recommendedActions);
    }

    public String summary() {
        return primaryNarrative();
    }

    public RiskLevel riskLevel() {
        return derivedRiskLevel();
    }

    public String recommendedAction() {
        return joinedRecommendedActions();
    }

    public static VlmResult fallback(YoloResult yoloResult) {
        BoundingBox primaryBox = selectPrimaryBox(yoloResult);
        boolean fireDetected = yoloResult != null && yoloResult.detected();
        double confidence = determineConfidence(yoloResult, primaryBox, fireDetected);

        return new VlmResult(
                fireDetected,
                confidence,
                primaryBox == null
                        ? new DetectedBbox(0.0, 0.0, 0.0, 0.0, "unknown", "YOLO fallback")
                        : new DetectedBbox(
                                (double) primaryBox.x(),
                                (double) primaryBox.y(),
                                (double) primaryBox.width(),
                                (double) primaryBox.height(),
                                StringUtils.hasText(primaryBox.label()) ? primaryBox.label() : "unknown",
                                "1st-stage YOLO fallback"
                        ),
                List.of(),
                new VisualCause(
                        fireDetected ? "화재 또는 연기 가능성" : "화재 징후 없음",
                        fireDetected
                                ? List.of("YOLO가 감지한 영역을 기준으로 추가 영상 검증 필요")
                                : List.of(),
                        fireDetected
                                ? "모델 응답 실패 시 YOLO 감지 결과를 기준으로 보수적으로 판단"
                                : "모델 응답 실패 시 안전한 기본값으로 false alarm 처리"
                ),
                new FireLocationDetail("", "", "", ""),
                new RiskAssessment(
                        fireDetected ? "HIGH" : "LOW",
                        fireDetected ? "YOLO 감지 결과를 기반으로 위험도가 높다고 가정" : "화재 징후가 확인되지 않음",
                        fireDetected ? "초기 화점 가능성" : "해당 없음",
                        fireDetected ? "현장 인원 확인 필요" : "인명 노출 징후 없음"
                ),
                fireDetected
                        ? List.of("즉시 현장 확인", "필요 시 119 신고")
                        : List.of("추가 모니터링"),
                fireDetected
                        ? "YOLO 감지 결과를 기반으로 생성된 안전 기본값입니다."
                        : "YOLO 감지 결과상 화재 징후가 없어 기본값을 반환합니다.",
                fireDetected
                        ? "신고자(자동감지시스템) 보고: YOLO가 화재 또는 연기 가능성을 감지했습니다. 현장 추가 확인과 즉시 대응이 필요합니다."
                        : ""
        );
    }

    private static BoundingBox selectPrimaryBox(YoloResult yoloResult) {
        if (yoloResult == null || yoloResult.boxes() == null || yoloResult.boxes().isEmpty()) {
            return null;
        }
        return yoloResult.boxes().stream()
                .filter(box -> box != null)
                .reduce((left, right) -> right)
                .orElse(yoloResult.boxes().get(0));
    }

    private static double determineConfidence(YoloResult yoloResult, BoundingBox primaryBox, boolean fireDetected) {
        if (yoloResult != null && yoloResult.confidence() != null) {
            return yoloResult.confidence();
        }
        if (primaryBox != null && primaryBox.score() != null) {
            return primaryBox.score();
        }
        return fireDetected ? 0.5 : 0.0;
    }

    private static double defaultConfidence(RiskLevel riskLevel) {
        if (riskLevel == null) {
            return 0.0;
        }
        return switch (riskLevel) {
            case HIGH -> 0.9;
            case MEDIUM -> 0.6;
            case LOW -> 0.2;
            case UNKNOWN -> 0.0;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static record DetectedBbox(
            Double x,
            Double y,
            Double width,
            Double height,
            String label,
            String source
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static record TimelineSummaryItem(
            Integer frameIndex,
            Integer timeOffsetS,
            String observation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static record VisualCause(
            String mostLikely,
            List<String> likelyIgnitionMechanisms,
            String confidenceExplanation
    ) {
        public VisualCause {
            likelyIgnitionMechanisms = likelyIgnitionMechanisms == null ? List.of() : List.copyOf(likelyIgnitionMechanisms);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static record FireLocationDetail(
            String cctvId,
            String siteMetadataLocation,
            String capturedAt,
            String preciseZone
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static record RiskAssessment(
            String level,
            String rationale,
            String currentFireSizeEstimate,
            String peoplePresence
    ) {
    }
}
