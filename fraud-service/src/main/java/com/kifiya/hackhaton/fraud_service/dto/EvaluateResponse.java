package com.kifiya.hackhaton.fraud_service.dto;

import java.util.List;

public record EvaluateResponse(
    String decision,
    Double aiScore,
    List<String> ruleFlags,
    RagResponse ragResponse
) {}
