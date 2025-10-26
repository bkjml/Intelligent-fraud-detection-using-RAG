package com.kifiya.hackhaton.fraud_service.service;


import com.kifiya.hackhaton.fraud_service.domain.RuleEntity;
import com.kifiya.hackhaton.fraud_service.domain.enums.RuleType;
import com.kifiya.hackhaton.fraud_service.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mvel2.MVEL; // The key dependency for dynamic rule evaluation!
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * **RuleEngineService: The Fraud Detection Brain **
 *
 * This service is responsible for taking raw transaction/event data (the 'variables' Map)
 * and running it through all our **active fraud rules**. It's the core execution unit.
 *
 * We use the **MVEL** library to dynamically evaluate the simple rules defined
 * by administrators (e.g., 'transactionAmount > 5000 and userCountry == "Nigeria"').
 * This allows for incredibly flexible, non-code-dependent rule creation.
 *
 * It handles two types of rules: **SIMPLE** (single expression) and **COMPOSITE** (AND/OR logic over other rules).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    // Dependency on the repository to fetch the active rules from the database.
    private final RuleRepository ruleRepository;

    /**
     * Public entry point: Evaluate a set of variables against all enabled rules.
     *
     * @param variables A map of data from the transaction/event (e.g., {"amount": 1000, "user_score": 55}).
     * @return A list of the *names* of the rules that were triggered (failed the check).
     */
    public List<String> evaluateRules(Map<String, Object> variables) {
        // 1. Load only the rules that are explicitly enabled by the Super Admin.
        List<RuleEntity> enabledRules = ruleRepository.findByEnabledTrue();
        List<String> triggered = new ArrayList<>();

        log.info("Loaded {} enabled rules", enabledRules.size());

        for (RuleEntity rule : enabledRules) {
            try {
                if (rule.getType() == RuleType.SIMPLE) {
                    // **Crucial Enhancement:** Check if all required data for the rule actually exists.
                    if (isValidForEvaluation(rule.getCondition(), variables)) {
                        if (evaluateCondition(rule.getCondition(), variables)) {
                            triggered.add(rule.getName());
                        }
                    } else {
                        // Log a warning instead of crashing or silently skipping.
                        log.warn("Skipping rule '{}' — missing variables in condition: {}",
                                rule.getName(),
                                extractMissingVariables(rule.getCondition(), variables));
                    }
                } else if (rule.getType() == RuleType.COMPOSITE) {
                    // Evaluate complex rules that combine the results of simpler ones.
                    if (evaluateCompositeRule(rule, variables, enabledRules)) {
                        triggered.add(rule.getName());
                    }
                }
            } catch (Exception e) {
                // Catching errors is essential here to ensure one bad rule doesn't halt all other checks.
                log.warn("Failed to evaluate rule '{}': {}", rule.getName(), e.getMessage());
            }
        }

        return triggered;
    }

    /**
     * Private helper: The actual dynamic evaluation.
     *
     * We use **MVEL** here. It parses the condition string (e.g., 'amount > 1000')
     * and executes it against the provided variable map.
     */
    private boolean evaluateCondition(String condition, Map<String, Object> vars) {
        Object result = MVEL.eval(condition, vars);
        // Ensure the MVEL expression actually returned a boolean result.
        return result instanceof Boolean && (Boolean) result;
    }

    /**
     * Private helper: Uses regex to pull out all the data fields needed from the condition string.
     * This is used for the pre-flight check in `isValidForEvaluation`.
     */
    private Set<String> extractVariablesFromCondition(String condition) {
        Set<String> vars = new HashSet<>();
        // Simple regex to find words that look like variable names
        Pattern pattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
        Matcher matcher = pattern.matcher(condition);
        while (matcher.find()) {
            String token = matcher.group();
            // Skip MVEL reserved words (operators, true/false literals)
            if (!List.of("and", "or", "not", "true", "false").contains(token)) {
                vars.add(token);
            }
        }
        return vars;
    }

    /**
     * Private helper: Checks if all variables required by a rule are present in the input map.
     * Prevents MVEL from throwing an error about an undefined variable.
     */
    private boolean isValidForEvaluation(String condition, Map<String, Object> vars) {
        Set<String> required = extractVariablesFromCondition(condition);
        return vars.keySet().containsAll(required);
    }

    /**
     * Private helper: Gathers the specific variables that are missing, for helpful logging.
     */
    private Set<String> extractMissingVariables(String condition, Map<String, Object> vars) {
        Set<String> required = extractVariablesFromCondition(condition);
        required.removeAll(vars.keySet());
        return required;
    }

    /**
     * Private helper: Evaluates a COMPOSITE rule.
     *
     * This rule type is a meta-rule: it checks the result of other *SIMPLE* rules.
     * It parses the list of sub-rule IDs and combines their results using the
     * `AND` or `OR` operator defined in the rule entity.
     */
    private boolean evaluateCompositeRule(RuleEntity rule, Map<String, Object> vars, List<RuleEntity> allRules) {
        // subRules is stored as a comma-separated list of UUIDs of other rules.
        try {
            // Basic string parsing to extract the IDs (could be improved with a proper JSON parser)
            List<String> parts = Arrays.asList(rule.getSubRules().replace("[", "")
                    .replace("]", "").split(","));

            // 1. Get the evaluation result (true/false) for every sub-rule ID.
            List<Boolean> results = parts.stream()
                    .map(String::trim)
                    .map(id -> {
                        // Find the actual sub-rule entity from the list of loaded rules.
                        Optional<RuleEntity> subRuleOpt = allRules.stream()
                                .filter(r -> r.getId().toString().equals(id))
                                .findFirst();
                        // Evaluate the condition of the found sub-rule.
                        return subRuleOpt.map(subRule -> evaluateCondition(subRule.getCondition(), vars)).orElse(false);
                    }).collect(Collectors.toList());

            // 2. Combine the results based on the composite rule's operator (AND/OR).
            return "AND".equalsIgnoreCase(rule.getOperator())
                    ? results.stream().allMatch(Boolean::booleanValue) // Must ALL be true
                    : results.stream().anyMatch(Boolean::booleanValue); // Must ANY be true

        } catch (Exception e) {
            // A problem in the composite rule definition (e.g., malformed subRules field)
            log.error("Error evaluating composite rule '{}'", rule.getName(), e);
            return false;
        }
    }
}