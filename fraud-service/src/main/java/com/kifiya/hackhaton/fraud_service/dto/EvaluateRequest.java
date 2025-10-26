package com.kifiya.hackhaton.fraud_service.dto;

import java.util.Map;

public record EvaluateRequest(
    String applicantId,
    Map<String, Object> attributes   // flexible structure for applicant info: hasActiveLoan, reapplyCount, phone, tin, etc
) {}
