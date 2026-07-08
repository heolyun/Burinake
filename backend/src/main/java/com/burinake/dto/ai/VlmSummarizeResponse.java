package com.burinake.dto.ai;

import com.burinake.dto.RiskLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.util.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VlmSummarizeResponse(
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
    public VlmSummarizeResponse {
        timelineSummary = timelineSummary == null ? List.of() : List.copyOf(timelineSummary);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }

    public String summary() {
        if (StringUtils.hasText(emergencyReportKoreanNarrative)) {
            return emergencyReportKoreanNarrative;
        }
        if (StringUtils.hasText(notes)) {
            return notes;
        }
        return "";
    }

    public RiskLevel riskLevel() {
        if (riskAssessment == null || !StringUtils.hasText(riskAssessment.level())) {
            return fireConfirmed ? RiskLevel.HIGH : RiskLevel.UNKNOWN;
        }

        String normalized = riskAssessment.level().trim().toUpperCase();
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

    public String recommendedAction() {
        if (recommendedActions.isEmpty()) {
            return "";
        }
        return String.join("\n", recommendedActions);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DetectedBbox(
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
    public record TimelineSummaryItem(
            Integer frameIndex,
            Integer timeOffsetS,
            String observation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VisualCause(
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
    public record FireLocationDetail(
            String cctvId,
            String siteMetadataLocation,
            String capturedAt,
            String preciseZone
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RiskAssessment(
            String level,
            String rationale,
            String currentFireSizeEstimate,
            String peoplePresence
    ) {
    }
}
