package com.kifiya.hackhaton.fraud_service.controller;

import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
//import com.kifiya.hackhaton.fraud_service.service.AlertService;
import com.kifiya.hackhaton.fraud_service.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fraud/alerts")
public class AlertController {

    private final AlertService alertService;
    @GetMapping("/stream")
    public Flux<ServerSentEvent<FraudCase>> streamAlerts() {
        return alertService.getAlerts()
                .map(alert -> ServerSentEvent.builder(alert).event("fraud-alert").build());
    }

}
