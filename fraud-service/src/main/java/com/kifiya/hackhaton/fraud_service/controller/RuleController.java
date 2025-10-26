package com.kifiya.hackhaton.fraud_service.controller;


import com.kifiya.hackhaton.fraud_service.domain.RuleEntity;
import com.kifiya.hackhaton.fraud_service.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * **Rule Management API: The Control Panel for Fraud Checks** 🛡️
 *
 * This class is the REST controller for managing our **Fraud Detection Rules**.
 * Think of it as the admin interface where super-users can define, enable,
 * disable, and delete the actual logic that the service uses to flag suspicious activity.
 *
 * We've secured all write/update/delete operations using `@PreAuthorize`
 * to ensure **only `ROLE_SUPER_ADMIN`** can change the rules, preventing accidental
 * or malicious modification of the core fraud logic.
 */
@RestController
@RequestMapping("/api/fraud/rules")
@RequiredArgsConstructor
public class RuleController {

    // Simple dependency injection via Lombok for the data access layer.
    private final RuleRepository ruleRepository;

    /**
     * GET /api/fraud/rules
     *
     * Lists **all active and inactive fraud rules**. This is primarily for
     * administrative viewing and is not called by the transaction processing logic.
     * The processing service pulls rules directly from the database or cache.
     *
     * @return A list of all existing RuleEntity objects.
     */
    @GetMapping
    public List<RuleEntity> listRules() {
        return ruleRepository.findAll();
    }

    /**
     * POST /api/fraud/rules
     *
     * **Creates a new fraud rule.** This is the core method for deploying new fraud checks.
     * Before saving, we enforce a new ID (in case one was provided) and timestamp it.
     *
     * **SECURITY:** Requires `ROLE_SUPER_ADMIN`.
     *
     * @param rule The RuleEntity object sent in the request body (e.g., "if amount > 1000").
     * @return The newly created and saved RuleEntity.
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<RuleEntity> createRule(@RequestBody RuleEntity rule) {
        rule.setId(null); // Ensure we create a new record, even if a user sends an ID
        rule.setCreatedAt(OffsetDateTime.now());
        RuleEntity saved = ruleRepository.save(rule);
        return ResponseEntity.ok(saved);
    }

    /**
     * PUT /api/fraud/rules/{id}/toggle?enabled={true|false}
     *
     * **Toggles a rule's active state** (enable or disable). This is a crucial
     * administrative function that allows us to instantly turn rules on or off
     * without deleting them, for testing or emergency maintenance.
     *
     * **SECURITY:** Requires `ROLE_SUPER_ADMIN`.
     *
     * @param id The UUID of the rule to toggle.
     * @param enabled The desired state (true for enabled, false for disabled).
     * @return The updated RuleEntity.
     */
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<RuleEntity> toggleRule(@PathVariable UUID id, @RequestParam boolean enabled) {
        // Find the rule or throw a standard Spring exception (404 Not Found)
        RuleEntity rule = ruleRepository.findById(id).orElseThrow();
        rule.setEnabled(enabled);
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    /**
     * DELETE /api/fraud/rules/{id}
     *
     * **Permanently deletes a fraud rule.** Used for cleaning up deprecated or
     * error-prone rules that are no longer needed.
     *
     * **SECURITY:** Requires `ROLE_SUPER_ADMIN`.
     *
     * @param id The UUID of the rule to delete.
     * @return A 204 No Content response.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}