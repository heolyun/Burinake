package com.burinake.dto;

public record VlmResult(
        String summary,
        RiskLevel riskLevel,
        String recommendedAction
) {
}
