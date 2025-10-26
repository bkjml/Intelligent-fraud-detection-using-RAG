package com.kifiya.hackhaton.fraud_service.client;

import com.kifiya.hackhaton.fraud_service.dto.AiScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiClient {

    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;


    public AiScoreResponse score(Map<String, Double> features) {
        // POST /score { "features": [...] } or a generic feature payload, adapt to your ai-service contract
        var payload = Map.of("features", features);
        log.warn("ai score payload {}", payload);
        try {
            return aiWebClient.post()
                    .uri("/score")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AiScoreResponse.class)
                    .block(); // simple blocking call for hackathon
        } catch (Exception e) {
            // fallback: return neutral score (safe) and log
            log.warn("Error at aiscore payload {}: {}", payload, e.getMessage());
            return new AiScoreResponse( 0.0, null);
        }
    }
}
