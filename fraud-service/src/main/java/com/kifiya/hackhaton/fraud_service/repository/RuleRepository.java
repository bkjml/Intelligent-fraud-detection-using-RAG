package com.kifiya.hackhaton.fraud_service.repository;


import com.kifiya.hackhaton.fraud_service.domain.RuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {
    List<RuleEntity> findByEnabledTrue();
}
