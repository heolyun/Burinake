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
                StringUtils.hasText(recommendedAction) ? List.of(recommendedAction) : List.of(),
                StringUtils.hasText(summary) ? summary : "",
                StringUtils.hasText(summary) ? summary : ""
        );
    }

    public static VlmResult analysisError(String title, String detail) {
        String normalizedTitle = StringUtils.hasText(title) ? title : "VLM analysis error";
        String normalizedDetail = StringUtils.hasText(detail) ? detail : "No detailed error message was provided.";
        String message = "[ANALYSIS_ERROR] %s: %s".formatted(normalizedTitle, normalizedDetail);
        return new VlmResult(
                false,
                null,
                new DetectedBbox(0.0, 0.0, 0.0, 0.0, "error", "analysis_error"),
                List.of(),
                new VisualCause(normalizedTitle, List.of(), normalizedDetail),
                new FireLocationDetail("", "", "", ""),
                new RiskAssessment(RiskLevel.UNKNOWN.name(), message, "", ""),
                List.of("Check the analysis error and retry with the same image if needed."),
                message,
                ""
        );
    }

    public static VlmResult notAnalyzed(String reason) {
        String normalizedReason = StringUtils.hasText(reason) ? reason : "VLM analysis was not executed.";
        return new VlmResult(
                false,
                null,
                new DetectedBbox(0.0, 0.0, 0.0, 0.0, "not_analyzed", "not_analyzed"),
                List.of(),
                new VisualCause("Not analyzed", List.of(), normalizedReason),
                new FireLocationDetail("", "", "", ""),
                new RiskAssessment(RiskLevel.UNKNOWN.name(), normalizedReason, "", ""),
                List.of(),
                normalizedReason,
                ""
        );
    }

    public boolean isAnalysisError() {
        return StringUtils.hasText(notes) && notes.startsWith("[ANALYSIS_ERROR]");
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
