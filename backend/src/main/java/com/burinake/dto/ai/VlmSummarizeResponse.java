package com.burinake.dto.ai;

import com.burinake.dto.RiskLevel;

public record VlmSummarizeResponse(
        String summary,
        RiskLevel riskLevel,
        String recommendedAction
) {
}
