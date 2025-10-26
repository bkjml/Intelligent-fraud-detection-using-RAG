package com.kifiya.hackhaton.fraud_service.controller;


import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import com.kifiya.hackhaton.fraud_service.dto.EvaluateRequest;
import com.kifiya.hackhaton.fraud_service.dto.EvaluateResponse;
import com.kifiya.hackhaton.fraud_service.service.FraudOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudOrchestratorService orchestrator;

    @Operation(summary = "evaluate fraud")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fraud evaluation result"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateResponse> evaluate(@RequestBody EvaluateRequest request) {
        EvaluateResponse resp = orchestrator.evaluate(request);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/cases/list")
    public ResponseEntity<List<FraudCase>> listCases() {
        List<FraudCase> all = orchestrator.listCases(); // add method in orchestrator or use repository directly
        return ResponseEntity.ok(all);
    }

    @PostMapping("/cases/{id}/assign")
    public ResponseEntity<Void> assignCase(@PathVariable UUID id, @RequestParam String user) {
        orchestrator.assignCase(id, user);
        return ResponseEntity.ok().build();
    }

}
