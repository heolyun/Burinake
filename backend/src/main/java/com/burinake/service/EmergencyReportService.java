package com.burinake.service;

import com.burinake.domain.EmergencyReportRow;
import com.burinake.domain.IssueRow;
import com.burinake.domain.VlmResultRow;
import com.burinake.dto.report.ReportCreateRequest;
import com.burinake.dto.report.ReportResponse;
import com.burinake.dto.report.ReportStatusUpdateRequest;
import com.burinake.mapper.EmergencyReportMapper;
import com.burinake.mapper.IssueMapper;
import com.burinake.mapper.VlmResultMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmergencyReportService {

    private static final Set<String> REPORT_STATUSES = Set.of(
            "DRAFT",
            "APPROVED",
            "SENT",
            "FAILED",
            "CANCELED"
    );

    private final EmergencyReportMapper reportMapper;
    private final IssueMapper issueMapper;
    private final VlmResultMapper vlmResultMapper;
    private final ObjectMapper objectMapper;

    public EmergencyReportService(
            EmergencyReportMapper reportMapper,
            IssueMapper issueMapper,
            VlmResultMapper vlmResultMapper,
            ObjectMapper objectMapper
    ) {
        this.reportMapper = reportMapper;
        this.issueMapper = issueMapper;
        this.vlmResultMapper = vlmResultMapper;
        this.objectMapper = objectMapper;
    }

    public List<ReportResponse> findRecent(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 200));
        return reportMapper.findRecent(effectiveLimit).stream()
                .map(this::toResponse)
                .toList();
    }

    public ReportResponse findById(Long reportId) {
        EmergencyReportRow report = reportMapper.findById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found");
        }
        return toResponse(report);
    }

    public List<ReportResponse> findByIssueId(Long issueId) {
        if (issueMapper.findById(issueId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }
        return reportMapper.findByIssueId(issueId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReportResponse createDraft(ReportCreateRequest request) {
        if (request == null || request.issueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueId is required");
        }
        IssueRow issue = issueMapper.findById(request.issueId());
        if (issue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
        }
        issueMapper.lockByIdForUpdate(request.issueId());

        EmergencyReportRow existingReport = reportMapper.findLatestNotCanceledByIssueId(request.issueId());
        if (existingReport != null) {
            return toResponse(existingReport);
        }

        VlmResultRow latestVlm = vlmResultMapper.findLatestByIssueId(request.issueId());
        if (latestVlm == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VLM result is required before creating a report");
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmergencyReportRow report = new EmergencyReportRow(
                reportMapper.nextId(),
                request.issueId(),
                latestVlm.vlmResultId(),
                "DRAFT",
                reportMessage(request.reportMessage(), issue, latestVlm),
                blankToDefault(request.receiver(), "119"),
                null,
                null,
                null,
                null,
                null,
                now,
                now
        );
        reportMapper.insert(report);
        return findById(report.reportId());
    }

    public ReportResponse updateStatus(Long reportId, ReportStatusUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        EmergencyReportRow report = reportMapper.findById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found");
        }
        String status = normalizeStatus(request.reportStatus());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime approvedAt = ("APPROVED".equals(status) || "SENT".equals(status)) && report.approvedAt() == null ? now : null;
        OffsetDateTime sentAt = "SENT".equals(status) && report.sentAt() == null ? now : null;
        String responseCode = "SENT".equals(status) ? blankToDefault(request.responseCode(), "LOCAL_SENT") : blankToNull(request.responseCode());
        String responseBody = "SENT".equals(status) ? blankToDefault(request.responseBody(), "Marked as sent from dashboard") : blankToNull(request.responseBody());

        reportMapper.updateStatus(
                reportId,
                status,
                blankToNull(request.reportMessage()),
                blankToNull(request.approvedBy()),
                approvedAt,
                sentAt,
                responseCode,
                responseBody,
                now
        );

        if ("SENT".equals(status)) {
            issueMapper.updateManualStatus(
                    report.issueId(),
                    "REPORTED",
                    true,
                    null,
                    "신고 전송 처리됨",
                    now,
                    now
            );
        }
        return findById(reportId);
    }

    public void deleteDraft(Long reportId) {
        EmergencyReportRow report = reportMapper.findById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found");
        }
        if (!"DRAFT".equals(report.reportStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only DRAFT reports can be deleted");
        }
        reportMapper.deleteDraft(reportId);
    }

    private ReportResponse toResponse(EmergencyReportRow row) {
        return new ReportResponse(
                row.reportId(),
                row.issueId(),
                row.vlmResultId(),
                row.reportStatus(),
                row.reportMessage(),
                row.receiver(),
                row.approvedBy(),
                row.approvedAt(),
                row.sentAt(),
                row.responseCode(),
                row.responseBody(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private String reportMessage(String requestedMessage, IssueRow issue, VlmResultRow latestVlm) {
        String message = blankToNull(requestedMessage);
        if (message != null) {
            return message;
        }

        JsonNode raw = parseRawResponse(latestVlm.rawResponse());
        String originEstimate = firstNonBlank(
                textAt(raw, "fire_location_detail", "precise_zone"),
                textAt(raw, "fire_location_detail", "site_metadata_location"),
                latestVlm.fireStart()
        );
        String causeEstimate = firstNonBlank(
                latestVlm.fireReason(),
                textAt(raw, "visual_cause", "most_likely")
        );
        String causeRationale = textAt(raw, "visual_cause", "confidence_explanation");
        String riskRationale = textAt(raw, "risk_assessment", "rationale");
        String sizeEstimate = textAt(raw, "risk_assessment", "current_fire_size_estimate");
        String peoplePresence = textAt(raw, "risk_assessment", "people_presence");
        String narrative = textAt(raw, "emergency_report_korean_narrative");

        List<String> lines = new ArrayList<>();
        lines.add("화재 신고 검토 요청");
        lines.add("이슈 번호: #" + issue.issueId());
        lines.add("위험 레벨: " + (latestVlm.level() == null ? "미판단" : "Level " + latestVlm.level()));
        lines.add("실제 화재 판단: " + (Boolean.TRUE.equals(latestVlm.isRealFire()) ? "화재 가능성 높음" : "화재 가능성 낮음"));
        lines.add("화재 발원지 추정: " + defaultText(originEstimate));
        lines.add("화재 원인 추정: " + defaultText(causeEstimate));
        addLine(lines, "원인 판단 근거", causeRationale);
        addLine(lines, "위험도 근거", riskRationale);
        addLine(lines, "현재 화재 규모", sizeEstimate);
        addLine(lines, "인명 징후", peoplePresence);
        addLine(lines, "상황 요약", latestVlm.situationSummary());
        addLine(lines, "권장 조치", latestVlm.message());
        addLine(lines, "신고 문안", narrative);

        return String.join("\n", lines);
    }

    private JsonNode parseRawResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawResponse);
        } catch (Exception ex) {
            return null;
        }
    }

    private String textAt(JsonNode root, String... path) {
        JsonNode current = root;
        for (String key : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(key);
        }

        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.isArray()) {
            List<String> values = new ArrayList<>();
            current.forEach(node -> addValue(values, node.asText(null)));
            return values.isEmpty() ? null : String.join(", ", values);
        }
        return blankToNull(current.asText(null));
    }

    private void addLine(List<String> lines, String label, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            lines.add(label + ": " + normalized);
        }
    }

    private void addValue(List<String> values, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String defaultText(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "미상" : normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportStatus is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!REPORT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported reportStatus: " + status);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }
}
