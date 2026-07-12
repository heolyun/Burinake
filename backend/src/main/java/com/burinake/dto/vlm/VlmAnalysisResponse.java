package com.burinake.dto.vlm;

import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VlmAnalysisResponse(
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
    public VlmAnalysisResponse {
        timelineSummary = timelineSummary == null ? List.of() : List.copyOf(timelineSummary);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }

    public VlmResult toVlmResult() {
        return new VlmResult(
                fireConfirmed,
                confidence != null ? confidence : 0.0,
                detectedBbox == null
                        ? new VlmResult.DetectedBbox(0.0, 0.0, 0.0, 0.0, "unknown", "vlm_json")
                        : new VlmResult.DetectedBbox(
                                detectedBbox.x(),
                                detectedBbox.y(),
                                detectedBbox.width(),
                                detectedBbox.height(),
                                detectedBbox.label(),
                                detectedBbox.source()
                        ),
                timelineSummary == null
                        ? List.of()
                        : timelineSummary.stream()
                                .map(item -> new VlmResult.TimelineSummaryItem(
                                        item.frameIndex(),
                                        item.timeOffsetS(),
                                        item.observation()
                                ))
                                .toList(),
                visualCause == null
                        ? new VlmResult.VisualCause("", List.of(), "")
                        : new VlmResult.VisualCause(
                                visualCause.mostLikely(),
                                visualCause.likelyIgnitionMechanisms(),
                                visualCause.confidenceExplanation()
                        ),
                fireLocationDetail == null
                        ? new VlmResult.FireLocationDetail("", "", "", "")
                        : new VlmResult.FireLocationDetail(
                                fireLocationDetail.cctvId(),
                                fireLocationDetail.siteMetadataLocation(),
                                fireLocationDetail.capturedAt(),
                                fireLocationDetail.preciseZone()
                        ),
                riskAssessment == null
                        ? new VlmResult.RiskAssessment(RiskLevel.UNKNOWN.name(), "", "", "")
                        : new VlmResult.RiskAssessment(
                                riskAssessment.level(),
                                riskAssessment.rationale(),
                                riskAssessment.currentFireSizeEstimate(),
                                riskAssessment.peoplePresence()
                        ),
                recommendedActions,
                notes == null ? "" : notes,
                emergencyReportKoreanNarrative == null ? "" : emergencyReportKoreanNarrative
        );
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
