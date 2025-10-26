package com.kifiya.hackhaton.fraud_service.client;

import com.kifiya.hackhaton.fraud_service.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RagClient {

    @Qualifier("ragWebClient")
    private final WebClient ragWebClient;

    public RagResponse explain(Map<String, Object> attributes,
                               List<String> ruleFlags,
                               Map<String, Double> aiExplanation,
                               String applicantId,
                               Double aiScore) {
//        var payload = Map.of("text", text);
        var payload = Map.of(
                "applicantId", applicantId,
                "attributes", attributes,
                "ruleFlags", ruleFlags,
                "topFeatures", aiExplanation,
                "aiScore", aiScore
        );
        try {
            return ragWebClient.post()
                    .uri("/explain")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(RagResponse.class)
                    .block();
        } catch (Exception e) {
            return new RagResponse("RAG reasoning unavailable", "UNKNOWN", 0.0);
        }
    }
}
