package com.kifiya.hackhaton.fraud_service.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String applicantId;           // business identifier
    private String decision;              // APPROVE/REVIEW/REJECT
    private Double aiScore;               // raw score from AI model
    private String ruleFlags;             // comma-separated rule names (quick)
    @Column(columnDefinition = "text")
    private String ragContext;            // explanation / retrieved docs summary (json or plaintext)
    @Column(columnDefinition = "text")
    private String rawPayload;            // optional: store anonymized input JSON
    private OffsetDateTime createdAt;
}
