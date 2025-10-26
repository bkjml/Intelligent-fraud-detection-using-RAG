package com.kifiya.hackhaton.fraud_service.dto;

import java.util.List;

public record RagResponse(String reasoning, String riskCategory, Double confidence) {}
