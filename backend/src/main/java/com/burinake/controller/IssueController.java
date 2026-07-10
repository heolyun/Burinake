package com.burinake.controller;

import com.burinake.dto.issue.IssueDetailResponse;
import com.burinake.dto.issue.IssueStatusUpdateRequest;
import com.burinake.dto.issue.IssueSummaryResponse;
import com.burinake.dto.issue.VlmResultResponse;
import com.burinake.service.IssueQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueController {

    private final IssueQueryService issueQueryService;

    public IssueController(IssueQueryService issueQueryService) {
        this.issueQueryService = issueQueryService;
    }

    @GetMapping("/api/v1/issues")
    public List<IssueSummaryResponse> findIssues(
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit
    ) {
        return issueQueryService.findRecent(limit);
    }

    @GetMapping("/api/v1/issues/{issueId}")
    public IssueDetailResponse findIssue(@PathVariable Long issueId) {
        return issueQueryService.findDetail(issueId);
    }

    @GetMapping("/api/v1/issues/{issueId}/vlm-results")
    public List<VlmResultResponse> findVlmResults(@PathVariable Long issueId) {
        return issueQueryService.findVlmHistory(issueId);
    }

    @PatchMapping("/api/v1/issues/{issueId}/status")
    public IssueDetailResponse updateStatus(
            @PathVariable Long issueId,
            @RequestBody IssueStatusUpdateRequest request
    ) {
        return issueQueryService.updateStatus(issueId, request);
    }
}
