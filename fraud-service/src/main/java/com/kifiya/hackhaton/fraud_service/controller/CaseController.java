package com.kifiya.hackhaton.fraud_service.controller;

import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import com.kifiya.hackhaton.fraud_service.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    // 1. Analysts see only their cases or unassigned alerts
    @GetMapping
    public ResponseEntity<List<FraudCase>> getMyCases(){  //@AuthenticationPrincipal Jwt jwt) {
//        String username = jwt.getClaimAsString("preferred_username");
        String username = "fraudanalyst@gmail.com";
        return ResponseEntity.ok(caseService.findCasesForAnalyst(username));
    }


    // 2. Analyst claims an alert (takes ownership)
    @PostMapping("/{id}/claim")
    public ResponseEntity<Void> claimCase(@PathVariable UUID id ){ //, @AuthenticationPrincipal Jwt jwt) {
//        String username = jwt.getClaimAsString("preferred_username");
        String username = "fraudanalyst@gmail.com";
        caseService.claimCase(id, username);
        return ResponseEntity.ok().build();
    }

    // 3. Analyst resolves a case
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveCase(@PathVariable UUID id,
                                            @RequestParam String resolution){
//                                            @AuthenticationPrincipal Jwt jwt) {
//        String username = jwt.getClaimAsString("preferred_username");
        String username = "fraudanalyst@gmail.com";
        caseService.resolveCase(id, username, resolution);
        return ResponseEntity.ok().build();
    }
}
