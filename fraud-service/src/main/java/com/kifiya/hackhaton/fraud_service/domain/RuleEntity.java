package com.kifiya.hackhaton.fraud_service.domain;

import jakarta.persistence.*;
import lombok.*;


import java.time.OffsetDateTime;
import java.util.UUID;
import com.kifiya.hackhaton.fraud_service.domain.enums.RuleType;

@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;               // e.g. "Multiple Applications Rule"

    @Enumerated(EnumType.STRING)
    private RuleType type;             // SIMPLE, COMPOSITE

    @Column(columnDefinition = "text")
    private String condition;          // e.g. "reapplyCount > 2"

    private String operator;           // for COMPOSITE: AND / OR (optional)

    @Column(columnDefinition = "text")
    private String subRules;           // JSON list of subrule IDs or expressions

    private boolean enabled;

    private String createdBy;

    private OffsetDateTime createdAt;
}
