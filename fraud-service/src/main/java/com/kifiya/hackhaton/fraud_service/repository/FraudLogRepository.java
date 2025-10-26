package com.kifiya.hackhaton.fraud_service.repository;


import com.kifiya.hackhaton.fraud_service.domain.FraudLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudLogRepository extends JpaRepository<FraudLog, UUID> {
    // Add custom queries if needed later
}
