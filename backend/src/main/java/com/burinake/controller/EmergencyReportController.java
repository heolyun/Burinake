package com.burinake.controller;

import com.burinake.dto.report.ReportCreateRequest;
import com.burinake.dto.report.ReportResponse;
import com.burinake.dto.report.ReportStatusUpdateRequest;
import com.burinake.service.EmergencyReportService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmergencyReportController {

    private final EmergencyReportService emergencyReportService;

    public EmergencyReportController(EmergencyReportService emergencyReportService) {
        this.emergencyReportService = emergencyReportService;
    }

    @GetMapping("/api/v1/reports")
    public List<ReportResponse> findReports(
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit
    ) {
        return emergencyReportService.findRecent(limit);
    }

    @GetMapping("/api/v1/reports/{reportId}")
    public ReportResponse findReport(@PathVariable Long reportId) {
        return emergencyReportService.findById(reportId);
    }

    @GetMapping("/api/v1/issues/{issueId}/reports")
    public List<ReportResponse> findIssueReports(@PathVariable Long issueId) {
        return emergencyReportService.findByIssueId(issueId);
    }

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(@RequestBody ReportCreateRequest request) {
        return emergencyReportService.createDraft(request);
    }

    @PatchMapping("/api/v1/reports/{reportId}/status")
    public ReportResponse updateStatus(
            @PathVariable Long reportId,
            @RequestBody ReportStatusUpdateRequest request
    ) {
        return emergencyReportService.updateStatus(reportId, request);
    }

    @DeleteMapping("/api/v1/reports/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable Long reportId) {
        emergencyReportService.deleteDraft(reportId);
    }
}
