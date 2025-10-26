package com.kifiya.hackhaton.fraud_service.service;

import com.kifiya.hackhaton.fraud_service.client.AiClient;
import com.kifiya.hackhaton.fraud_service.client.RagClient;
import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import com.kifiya.hackhaton.fraud_service.domain.FraudLog;
import com.kifiya.hackhaton.fraud_service.dto.AiScoreResponse;
import com.kifiya.hackhaton.fraud_service.dto.EvaluateRequest;
import com.kifiya.hackhaton.fraud_service.dto.EvaluateResponse;
import com.kifiya.hackhaton.fraud_service.dto.RagResponse;
import com.kifiya.hackhaton.fraud_service.repository.FraudCaseRepository;
import com.kifiya.hackhaton.fraud_service.repository.FraudLogRepository;
import com.kifiya.hackhaton.fraud_service.repository.RuleRepository;
import com.kifiya.hackhaton.fraud_service.transformer.FeatureTransformer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * **FraudOrchestratorService: The Decision Maker **
 *
 * This service is the central control point for a fraud evaluation request.
 * It coordinates all components:
 * 1. **Caching** (Speed up repeat requests).
 * 2. **Rule Engine** (Deterministic, fixed logic).
 * 3. **AI Model** (Statistical, anomaly detection).
 * 4. **RAG Client** (Generative AI for human-readable context/explanation).
 * 5. **Logging & Case Management** (Persistence and alerting).
 *
 * Its main job is to combine the results from these diverse systems into a single, final decision:
 * **APPROVE**, **REVIEW**, or **REJECT**.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudOrchestratorService {

    private final RuleEngineService ruleEngineService; // The database rules
    private final AiClient aiClient;                   // The external scoring model
    private final RagClient ragClient;                 // The external context generator
    private final FraudLogRepository fraudLogRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final AlertService alertService;           // For sending notifications
    private final FraudCacheService fraudCacheService; // Redis/Caffeine-based caching
    private final FeatureTransformer featureTransformer; // Prepares data for the AI model


    // --- Core Thresholds (Hackathon Tuning Parameters) ---
    private static final double AI_REVIEW_THRESHOLD = 0.5; // If score is above this, human review is needed.
    private static final double AI_REJECT_THRESHOLD = 0.8; // If score is very high, reject immediately.

    /**
     * The main business logic method. Executes the entire fraud detection pipeline.
     * Everything here must succeed or fail together, hence the @Transactional annotation.
     *
     * @param request The incoming transaction/event data.
     * @return The final decision and all underlying scores/flags.
     */
    @Transactional
    public EvaluateResponse evaluate(EvaluateRequest request) {

        Map<String, Object> attributes = request.attributes() != null ? request.attributes() : Map.of();
        String cacheKey = "fraud:eval:" + request.applicantId();

        // ---- 0) Check Cache (The First Line of Defense/Optimization) ----
        EvaluateResponse cached = fraudCacheService.getCachedResult(cacheKey);
        if (cached != null) {
            log.info(" Returning cached fraud result for applicant {}", request.applicantId());
            return cached;
        }

        // 1) Run DB rules (The deterministic, hard-coded logic)
        List<String> ruleFlags = ruleEngineService.evaluateRules(attributes);

        // 2) Prepare/Transform features for the AI Model
        Map<String, Double> featurePayload = buildFeaturePayload(attributes);

        // 3) Call AI (The statistical, anomaly detection logic)
        AiScoreResponse aiResp = aiClient.score(featurePayload);
        log.warn("AI score response {}: {}", aiResp.score(), aiResp.result()); // Log the model's output for review
        double aiScore = aiResp != null && aiResp.score() != null ? aiResp.score() : 0.0;

        // Transform the model's 'anomalous feature' vectors back into human-readable features
        Map<String, Double> reverseResponse = featureTransformer.reverseAnomalyVectors(aiResp.result());

        // 4) Call RAG for explanation (Only call expensive LLM if needed for review/reject)
        RagResponse ragResp = null;
        if (aiScore > AI_REVIEW_THRESHOLD || !ruleFlags.isEmpty()){
            // We pass the flags, the raw attributes, and the anomalous features to the RAG system
            // so it can generate a comprehensive, human-readable reason for the flag.
            ragResp = ragClient.explain(attributes, ruleFlags, reverseResponse, request.applicantId(), aiResp.score());
        }

        // 5) Combine outputs into a final decision
        String decision = decide(ruleFlags, aiScore);

        // 6) Persist FraudLog (Audit Trail)
        FraudLog log = FraudLog.builder()
                .applicantId(request.applicantId())
                .decision(decision)
                .aiScore(aiScore)
                .ruleFlags(String.join(",", ruleFlags))
                .ragContext(String.join("\n", ragResp != null ? ragResp.reasoning() : ""))
                .rawPayload(attributes.toString())
                .createdAt(OffsetDateTime.now())
                .build();
        fraudLogRepository.save(log);

        // 7) Create a FraudCase and Alert
        if ("REVIEW".equalsIgnoreCase(decision) || "REJECT".equalsIgnoreCase(decision)) {
            FraudCase fc = FraudCase.builder()
                    .fraudLogId(log.getId())
                    .status("OPEN") // Ready for an analyst to pick up
                    .createdAt(LocalDateTime.now().toString())
                    .build();
            fraudCaseRepository.save(fc);
            alertService.publishAlert(fc); // Notify the fraud team via a messaging system
        }

        // 8) Final result and Cache the outcome
        EvaluateResponse evaluateResponse = new EvaluateResponse(decision, aiScore, ruleFlags, ragResp);
        fraudCacheService.cacheResult(cacheKey, evaluateResponse);

        return evaluateResponse;
    }

    /**
     * Private helper: Transforms the raw attribute map into the numeric feature map
     * expected by the external AI scoring model.
     */
    private Map<String, Double> buildFeaturePayload(Map<String, Object> attributes) {
        // This abstracts the logic for converting raw data (strings, dates, etc.)
        // into the normalized numeric vectors the ML model needs.
        return featureTransformer.transform(attributes);
    }

    /**
     * Private helper: The core policy for combining flags and scores into a decision.
     */
    private String decide(List<String> ruleFlags, double aiScore) {
        // Simple combining policy, tuneable for the hackathon:

        // P1: Explicit rules always override statistical scores for REVIEW.
        if (!ruleFlags.isEmpty()) {
            return "REVIEW";
        }
        // P2: Very high AI score means immediate rejection.
        if (aiScore >= AI_REJECT_THRESHOLD) return "REJECT";

        // P3: Moderately high AI score means human intervention is required.
        if (aiScore >= AI_REVIEW_THRESHOLD) return "REVIEW";

        // P4: All clear.
        return "APPROVE";
    }


    // --- Case Management Methods (for the Admin/Reviewer UI) ---

    /**
     * List all fraud cases in the system (for the Analyst Dashboard).
     */
    public List<FraudCase> listCases() {
        return fraudCaseRepository.findAll();
    }

    /**
     * Assign a fraud case to an analyst or reviewer (Analyst picks up a case).
     */
    @Transactional
    public void assignCase(UUID caseId, String username) {
        Optional<FraudCase> optionalCase = fraudCaseRepository.findById(caseId);
        if (optionalCase.isEmpty()) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }

        FraudCase fraudCase = optionalCase.get();
        fraudCase.setAssignedTo(username);
        fraudCase.setStatus("IN_PROGRESS");
        fraudCase.setUpdatedAt(LocalDateTime.now().toString());

        fraudCaseRepository.save(fraudCase);
    }
}