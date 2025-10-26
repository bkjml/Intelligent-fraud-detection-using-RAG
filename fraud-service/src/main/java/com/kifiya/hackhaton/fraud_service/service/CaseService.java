package com.kifiya.hackhaton.fraud_service.service;

import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import com.kifiya.hackhaton.fraud_service.repository.FraudCaseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final FraudCaseRepository fraudCaseRepository;

    public List<FraudCase> findCasesForAnalyst(String username) {
        // show cases assigned to user OR unassigned alerts
        return fraudCaseRepository.findAll().stream()
                .filter(c -> "ALERT".equalsIgnoreCase(c.getStatus()) ||
                        username.equalsIgnoreCase(c.getAssignedTo()))
                .toList();
    }

    @Transactional
    public void claimCase(UUID id, String username) {
        FraudCase fc = fraudCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));
        fc.setAssignedTo(username);
        fc.setStatus("IN_PROGRESS");
        fc.setUpdatedAt(LocalDateTime.now().toString());
        fraudCaseRepository.save(fc);
    }

    @Transactional
    public void resolveCase(UUID id, String username, String resolution) {
        FraudCase fc = fraudCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));
        fc.setNotes(resolution);
        fc.setStatus("CLOSED");
        fc.setUpdatedAt(LocalDateTime.now().toString());
        fraudCaseRepository.save(fc);
    }
}
