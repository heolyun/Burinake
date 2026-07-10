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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    public EmergencyReportService(
            EmergencyReportMapper reportMapper,
            IssueMapper issueMapper,
            VlmResultMapper vlmResultMapper
    ) {
        this.reportMapper = reportMapper;
        this.issueMapper = issueMapper;
        this.vlmResultMapper = vlmResultMapper;
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

    public ReportResponse createDraft(ReportCreateRequest request) {
        if (request == null || request.issueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueId is required");
        }
        IssueRow issue = issueMapper.findById(request.issueId());
        if (issue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "issue not found");
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
        if (latestVlm.message() != null && !latestVlm.message().isBlank()) {
            return latestVlm.message();
        }
        if (latestVlm.situationSummary() != null && !latestVlm.situationSummary().isBlank()) {
            return latestVlm.situationSummary();
        }
        return "화재 의심 이슈 #" + issue.issueId() + " 신고 검토가 필요합니다.";
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
