package com.kifiya.hackhaton.fraud_service.dto;

import java.util.Map;

public record AiScoreResponse(Double score, Map<String, Double> result) {}