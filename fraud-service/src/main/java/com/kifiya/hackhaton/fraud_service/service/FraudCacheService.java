package com.kifiya.hackhaton.fraud_service.service;

import com.kifiya.hackhaton.fraud_service.domain.FraudCase;
import com.kifiya.hackhaton.fraud_service.dto.EvaluateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * **FraudCacheService: The Performance Layer 🚀**
 *
 * This simple service handles the **caching layer**, specifically using Redis.
 * Its purpose is to drastically reduce latency and load on the Rule Engine and AI model
 * by quickly returning a previous decision for a repeated request (e.g., the same applicant
 * making multiple requests shortly after one another).
 *
 * We rely on **Spring Data Redis** and inject a generic `RedisTemplate` configured
 * to handle our serializable objects, like `EvaluateResponse`.
 */
@Service
@RequiredArgsConstructor
public class FraudCacheService {

    // The Spring-managed client for interacting with Redis.
    private final RedisTemplate<String, Object> redisTemplate;

    // The Time-To-Live (TTL) for cache entries, configurable via application.properties.
    // Defaults to 3600 seconds (1 hour). This prevents stale data.
    @Value("${fraud.cache-ttl-seconds:3600}")
    private long cacheTtlSeconds;


    /**
     * Attempts to retrieve a previous fraud evaluation result from the cache.
     *
     * @param key The unique key (e.g., 'fraud:eval:<applicantId>').
     * @return The cached EvaluateResponse object, or null if not found/expired.
     */
    public EvaluateResponse getCachedResult(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        // We perform a safe cast check just to be robust, though the template should handle serialization.
        return cached instanceof EvaluateResponse ? (EvaluateResponse) cached : null;
    }

    /**
     * Stores the current fraud evaluation result in the cache.
     *
     * @param key The unique key.
     * @param response The final decision object to be cached.
     */
    public void cacheResult(String key, EvaluateResponse response) {
        // Store the value, ensuring we set the defined TTL to prevent infinite storage.
        redisTemplate.opsForValue().set(key, response, cacheTtlSeconds, TimeUnit.SECONDS);
    }
}