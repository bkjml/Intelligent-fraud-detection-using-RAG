package com.kifiya.hackhaton.fraud_service.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * **FeatureTransformer: The AI Model Data Bridge**
 *
 * This class is the **crucial link between our raw application data and the external AI/ML model**
 * (like a pre-trained LightGBM or XGBoost).
 *
 * Why is this needed? Machine Learning models often require a *fixed*, highly-specific
 * feature vector. This transformer takes the arbitrary map of transaction attributes
 * and converts it into the **EXACTLY 30 features (Amount, Time, V1-V28)** expected by our model.
 *
 * **Key Hackathon Innovation:** The V-vectors (V1-V28) are synthetically generated
 * by creatively combining several internal 'risk signals' (like email risk, device risk,
 * credit risk) using unique weights. This mimics how real-world ML models use complex,
 * non-intuitive feature combinations.
 */
@Slf4j
@Component
public class FeatureTransformer {

    private static final double MAX_NORMALIZED_TIME = 86400.0; // Seconds in a day (for the 'Time' feature normalization)
    private static final double MAX_NOISE = 0.1;               // Random noise added to V-vectors to prevent perfect linearity
    private static final double V_VECTOR_MAGNITUDE_SCALER = 0.5; // Controls the general impact of risk signals on the V-vectors

    // We use 9 core risk signals (like 'email_risk') to generate the 28 required V-vectors
    private static final int NUM_RISK_SIGNALS = 9;
    // This matrix holds the random weights that blend the 9 signals into the 28 vectors.
    private static final double[][] V_RISK_WEIGHTS = createUniqueWeights(28, NUM_RISK_SIGNALS);

    // The human-readable keys for our internal risk assessment signals
    List<String> riskKeys = Arrays.asList(
            "name_risk", "email_risk", "phone_risk", "age_risk",
            "device_risk", "network_risk", "merchant_risk",
            "loan_activity_risk", "velocity_risk"
    );

    /**
     * The main transformation method. Converts raw attributes into the 30-feature vector.
     *
     * @param attributes Raw input data (e.g., {"amount": 500, "email": "a@b.com"}).
     * @return A map with 30 keys: {"Amount", "Time", "V1", "V2", ..., "V28"}.
     */
    public Map<String, Double> transform(Map<String, Object> attributes) {
        // LinkedHashMap ensures feature order is maintained, which is often crucial for ML models!
        Map<String, Double> features = new LinkedHashMap<>();

        try {
            // 1️⃣ Base numeric features: Normalized and Log-transformed (standard ML practice)
            double amount = parseDouble(attributes.getOrDefault("amount", 0.0));
            double timeInSeconds = parseDouble(attributes.getOrDefault("time", getCurrentTimeAsSeconds()));

            features.put("Amount", Math.log1p(amount)); // log(1+amount) helps manage skewed financial data
            features.put("Time", Math.min(timeInSeconds / MAX_NORMALIZED_TIME, 1.0)); // Normalize 0-1 range

            // 2️⃣ Compute our internal, human-interpretable anomaly signals
            Map<String, Double> anomalySignals = computeAnomalies(attributes, timeInSeconds);

            // 3️⃣ Synthesize the model-specific vectors (V1–V28)
            createAnomalyVectors(features, anomalySignals);

        } catch (Exception e) {
            log.error("Feature transformation failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }

        // Final check: Critical to ensure the ML model receives the exact number of features!
        if (features.size() != 30) {
            log.error("Final feature count mismatch: Expected 30, got {}", features.size());
            return new HashMap<>();
        }

        return features;
    }

    // ------------------------------
    // Compute anomaly indicators
    // ------------------------------

    /**
     * Calculates the internal, human-interpretable risk signals (0.0=Low Risk to 1.0=High Risk).
     * This logic contains simple, deterministic checks that could also be rules in the Rule Engine.
     */
    private Map<String, Double> computeAnomalies(Map<String, Object> attributes, double timeInSeconds) {
        Map<String, Double> signals = new HashMap<>();

        String firstName = String.valueOf(attributes.getOrDefault("firstName", "N/A")).toLowerCase();
        String lastName = String.valueOf(attributes.getOrDefault("lastName", "N/A")).toLowerCase();
        String email = String.valueOf(attributes.getOrDefault("email", "unknown@example.com"));
        String phone = String.valueOf(attributes.getOrDefault("phoneNumber", "0000000000"));
        double age = parseDouble(attributes.getOrDefault("age", 0));
        String deviceType = String.valueOf(attributes.getOrDefault("deviceType", "UNKNOWN"));
        String ipAddress = String.valueOf(attributes.getOrDefault("ipAddress", "0.0.0.0"));
        String merchant = String.valueOf(attributes.getOrDefault("merchantCategory", "GENERAL"));
        String os = String.valueOf(attributes.getOrDefault("os", "android"));

        double activeLoans = parseDouble(attributes.getOrDefault("activeLoansCount", 0));
        boolean reapplyVelocity = (boolean) attributes.getOrDefault("reapplyVelocityFlag", false);

        // NEW attributes
        double creditScore = parseDouble(attributes.getOrDefault("creditScore", 650)); // 350–850 range
        String employmentStatus = String.valueOf(attributes.getOrDefault("employmentStatus", "unknown")).toLowerCase();
        double existingLoans = parseDouble(attributes.getOrDefault("existingLoans", 0));

        // --- Existing anomaly logic ---
        double nameEntropy = 1.0 - Math.min(uniqueChars(firstName + lastName) / 26.0, 1.0);
        double emailInvalid = !Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$").matcher(email).matches() ? 1.0 : 0.0;
        double freeDomain = email.contains("gmail") || email.contains("yahoo") ? 0.5 : 0.8;
        double emailRisk = emailInvalid > 0 ? 1.0 : (1.0 - freeDomain);
        double phoneInvalid = (phone.length() < 9 || phone.length() > 13) ? 1.0 : 0.0;
        double phonePattern = hashToRange(phone, 0.0, 1.0);
        double ageInvalid = (age < 18 || age > 80) ? 1.0 : 0.0;
        double deviceRisk = deviceType.toLowerCase().contains("emulator") ? 1.0 :
                deviceType.toLowerCase().contains("mobile") ? 0.4 : 0.2;
        double ipEntropy = hashToRange(ipAddress, 0.0, 1.0);
        double osRisk = os.toLowerCase().contains("android") ? 0.6 : 0.3;
        double activeLoanRisk = activeLoans > 2 ? 1.0 : (activeLoans > 0 ? 0.5 : 0.0);
        double velocityRisk = reapplyVelocity ? 1.0 : 0.0;

        // --- New Financial/Behavioral Anomalies ---
        double creditRisk = 1.0 - Math.min(Math.max((creditScore - 350) / 500.0, 0.0), 1.0); // lower score = higher risk
        double employmentRisk = switch (employmentStatus) {
            case "unemployed" -> 1.0; // Highest risk
            case "self-employed" -> 0.7;
            case "employed" -> 0.3; // Lowest risk
            default -> 0.5;
        };
        double existingLoanRisk = existingLoans > 3 ? 1.0 : (existingLoans > 0 ? 0.6 : 0.2);

        // --- Time-Specific Anomaly ---
        double hour = timeInSeconds / 3600.0;
        double nightRisk = (hour >= 0 && hour <= 6) ? 0.8 : 0.1;

        // --- Combine all ---
        signals.put("name_risk", nameEntropy * 0.8);
        signals.put("email_risk", emailRisk);
        signals.put("phone_risk", (phoneInvalid + phonePattern) / 2.0);
        signals.put("age_risk", ageInvalid);
        signals.put("device_risk", (deviceRisk + osRisk) / 2.0);
        signals.put("network_risk", ipEntropy);
        signals.put("merchant_risk", hashToRange(merchant, 0.0, 1.0));
        signals.put("loan_activity_risk", activeLoanRisk);
        signals.put("velocity_risk", velocityRisk);
        signals.put("credit_risk", creditRisk);
        signals.put("employment_risk", employmentRisk);
        signals.put("existing_loan_risk", existingLoanRisk);
        signals.put("time_of_day_risk", nightRisk);

        // Unified composite risk
        double compositeRisk = signals.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
        signals.put("composite_risk", compositeRisk);

        return signals;
    }

    // ------------------------------
    // Build anomaly-weighted vectors
    // ------------------------------

    /**
     * Synthesizes the V1-V28 vectors by combining the anomaly signals using the
     * pre-generated, unique weight matrix. This is how we embed our simple risk logic
     * into the complex format expected by the external ML model.
     */
    private void createAnomalyVectors(Map<String, Double> features, Map<String, Double> anomalies) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double baseRisk = anomalies.getOrDefault("composite_risk", 0.5);

        for (int i = 0; i < V_RISK_WEIGHTS.length; i++) { // Loop 28 times (for V1 to V28)
            double combinedWeightedRisk = 0.0;
            double weightSum = 0.0;

            for (int j = 0; j < riskKeys.size(); j++) { // Loop 9 times (for each risk signal)
                double riskValue = anomalies.getOrDefault(riskKeys.get(j), 0.5);
                double weight = V_RISK_WEIGHTS[i][j]; // Unique weight for this specific V-vector and risk signal

                combinedWeightedRisk += riskValue * weight;
                weightSum += weight;
            }

            double normalizedCombined = combinedWeightedRisk / weightSum;

            // Final Formula: Weighted Risk Component + Global Risk Component + Small Random Noise
            double vector = baseRisk * normalizedCombined * V_VECTOR_MAGNITUDE_SCALER
                    + rand.nextDouble(-MAX_NOISE, MAX_NOISE);

            // Clip the value to a standard range (-1.0 to 1.0)
            features.put("V" + (i + 1), Math.max(-1.0, Math.min(1.0, vector)));
        }
    }

    // ------------------------------
    // Reverse Engineering for RAG
    // ------------------------------

    /**
     * This is an **innovative reverse-engineering step** to support RAG (Explanation AI).
     *
     * The AI Model returns its "anomaly scores" for V1-V28. This method takes those V-vector scores
     * and attempts to **back-calculate** which of our original 9 risk signals contributed the most.
     *
     * It's not a perfect inverse, but it provides the RAG client with the top 3 most anomalous
     * internal signals (e.g., 'credit_risk', 'velocity_risk') to generate a compelling
     * and useful explanation for the user.
     *
     * @param features The V1-V28 scores returned by the external AI model.
     * @return The top 3 original risk keys and their estimated scores.
     */
    public Map<String, Double> reverseAnomalyVectors(Map<String, Double> features) {
        Map<String, Double> anomalies = new HashMap<>();

        // Initialize all risks to 0
        for (String key : riskKeys) {
            anomalies.put(key, 0.0);
        }

        // Reverse contribution
        for (int i = 0; i < V_RISK_WEIGHTS.length; i++) {
            double vValue = features.getOrDefault("V" + (i + 1), 0.0);
            double weightSum = Arrays.stream(V_RISK_WEIGHTS[i]).sum();

            for (int j = 0; j < riskKeys.size(); j++) {
                double weight = V_RISK_WEIGHTS[i][j];
                double contribution = (vValue / V_VECTOR_MAGNITUDE_SCALER) * (weight / weightSum);
                anomalies.merge(riskKeys.get(j), contribution, Double::sum);
            }
        }

        // Normalize 0–1 range
        anomalies.replaceAll((k, v) -> Math.max(0.0, Math.min(1.0, v)));

        // Sort and keep only top 3
        return anomalies.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new // Preserve sorted order for the RAG prompt
                ));
    }

    // ------------------------------
    // Utils
    // ------------------------------

    private static double[][] createUniqueWeights(int rows, int cols) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double[][] weights = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // Generate unique weights between 0.1 and 1.0
                weights[i][j] = rand.nextDouble(0.1, 1.0);
            }
        }
        return weights;
    }

    private double parseDouble(Object val) {
        try {
            if (val instanceof Number) return ((Number) val).doubleValue();
            return Double.parseDouble(String.valueOf(val));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getCurrentTimeAsSeconds() {
        return LocalTime.now().toSecondOfDay();
    }

    private double uniqueChars(String text) {
        if (text == null) return 0.0;
        return text.chars().distinct().count();
    }

    private double hashToRange(String value, double min, double max) {
        int hash = Math.abs(Objects.hashCode(value)) % 10000;
        return min + ((double) hash / 10000.0) * (max - min);
    }
}