package com.burinake.dto.vlm;

import com.burinake.dto.BoundingBox;
import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.util.StringUtils;

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

    public static VlmAnalysisResponse fallback(YoloResult yoloResult, VlmCctvMetadata cctvMetadata) {
        BoundingBox box = selectPrimaryBox(yoloResult);
        boolean detected = yoloResult != null && yoloResult.detected();
        Double confidence = yoloResult != null && yoloResult.confidence() != null
                ? yoloResult.confidence()
                : box != null && box.score() != null
                        ? box.score()
                        : detected ? 0.5 : 0.0;

        return new VlmAnalysisResponse(
                detected,
                confidence,
                box == null
                        ? new DetectedBbox(0.0, 0.0, 0.0, 0.0, "unknown", "fallback")
                        : new DetectedBbox(
                                (double) box.x(),
                                (double) box.y(),
                                (double) box.width(),
                                (double) box.height(),
                                StringUtils.hasText(box.label()) ? box.label() : "unknown",
                                "yolo_fallback"
                        ),
                List.of(),
                new VisualCause(
                        detected ? "Possible fire or smoke" : "No clear fire signs",
                        detected ? List.of("YOLO-detected area requires VLM review") : List.of(),
                        detected ? "Fallback response based on YOLO result" : "Fallback response based on no detection"
                ),
                new FireLocationDetail(
                        cctvMetadata != null ? cctvMetadata.cctvId() : "",
                        cctvMetadata != null ? cctvMetadata.location() : "",
                        cctvMetadata != null ? cctvMetadata.capturedAt() : "",
                        ""
                ),
                new RiskAssessment(
                        detected ? "HIGH" : "LOW",
                        detected ? "YOLO fallback indicates a potentially dangerous scene" : "No obvious fire scene",
                        detected ? "Initial stage" : "None",
                        detected ? "Check immediately" : "No people detected in fallback"
                ),
                detected
                        ? List.of("Inspect the scene immediately", "Call emergency services if needed")
                        : List.of("Continue monitoring"),
                detected
                        ? "Fallback response generated from YOLO detection."
                        : "Fallback response generated from no fire detection.",
                detected
                        ? "Auto-detection fallback: YOLO indicated possible fire/smoke."
                        : ""
        );
    }

    private static BoundingBox selectPrimaryBox(YoloResult yoloResult) {
        if (yoloResult == null || yoloResult.boxes() == null || yoloResult.boxes().isEmpty()) {
            return null;
        }
        BoundingBox best = yoloResult.boxes().get(0);
        for (BoundingBox box : yoloResult.boxes()) {
            if (box != null && box.score() != null && (best == null || best.score() == null || box.score() > best.score())) {
                best = box;
            }
        }
        return best;
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
