package com.kifiya.hackhaton.fraud_service.service;
//
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentLinkedQueue;
//
@Service
@RequiredArgsConstructor
public class AlertService implements MessageListener {


    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;
    private final RedisMessageListenerContainer container;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // connected subscribers
    private final ConcurrentLinkedQueue<FluxSink<FraudCase>> subscribers = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void subscribe() {
        container.addMessageListener(this, topic);
    }

    // 🔹 Publish alert to Redis
    public void publishAlert(FraudCase fraudCase) {
        redisTemplate.convertAndSend(topic.getTopic(), fraudCase);
    }

    // 🔹 Create reactive Flux stream backed by Redis subscription
    public Flux<FraudCase> getAlerts() {
        return Flux.create(sink -> {
            subscribers.add(sink);
            sink.onDispose(() -> subscribers.remove(sink));
        });
    }

    // 🔹 Redis MessageListener callback
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            FraudCase fraudCase = objectMapper.readValue(message.getBody(), FraudCase.class);
            // emit to all connected subscribers
            subscribers.forEach(sub -> sub.next(fraudCase));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
