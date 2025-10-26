package com.kifiya.hackhaton.fraud_service.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID fraudLogId;

    private String assignedTo;       // username or analyst id
    private String status;           // OPEN / IN_PROGRESS / CLOSED
    @Column(columnDefinition = "text")
    private String notes;

    private String createdAt;
    private String updatedAt;
}
