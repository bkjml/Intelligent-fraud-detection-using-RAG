package com.kifiya.hackhaton.fraud_service.repository;


import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {
}
