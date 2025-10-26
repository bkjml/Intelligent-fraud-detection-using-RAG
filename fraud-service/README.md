# 🚨 Hybrid Fraud Detection Platform

### **Combining Rule-Based Logic, AI Models, and Real-Time Intelligence**
_A hackathon-ready microservice architecture using Spring Boot, Keycloak, Redis, and AI (FastAPI)._

---

## 📘 Table of Contents
1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Core Features](#core-features)
4. [Tech Stack](#tech-stack)
5. [Microservices](#microservices)
6. [Gateway Service](#gateway-service)
7. [Fraud Service](#fraud-service)
8. [Security with Keycloak](#security-with-keycloak)
9. [Redis Integration](#redis-integration)
10. [API Endpoints](#api-endpoints)
11. [Run Locally](#run-locally)
12. [Next Improvements](#next-improvements)

---

## 🧠 Overview

The **Hybrid Fraud Detection Platform** is built to demonstrate how **AI**, **rules**, and **real-time streaming** can work together for intelligent financial fraud prevention.

It includes:
- A **Spring Cloud Gateway** for routing and security.
- A **Fraud Detection Service** combining **rule-based** and **AI-based** analysis.
- **Keycloak** for secure authentication & authorization (OAuth2/JWT).
- **Redis** for caching fraud checks and improving response speed.
- **AI & RAG services** for intelligent fraud detection reasoning.

---

## 🏗️ System Architecture

```
                           ┌───────────────────────────────┐
                           │         Keycloak (Auth)       │
                           │   Realm: Fraud-Detectiom      │
                           └──────────────┬────────────────┘
                                          │
                                          ▼
                             ┌───────────────────────────┐
                             │   Spring Cloud Gateway    │
                             │ - Routes all requests     │
                             │ - Validates JWTs          │
                             │ - Uses Redis for caching  │
                             └──────────────┬────────────┘
                                            │
                                            ▼
       ┌───────────────────────────────────────────────────────────┐
       │                         Backend                           │
       │ ┌──────────────────────────────────────────────────────┐ │
       │ │                Fraud Detection Service               │ │
       │ │  - Rule Engine (MVEL)                                │ │
       │ │  - AI & RAG Clients (WebClient)                      │ │
       │ │  - Fraud Orchestrator                                │ │
       │ │  - Alert Service (SSE)                               │ │
       │ │  - Redis Cache for Results                           │ │
       │ └──────────────────────────────────────────────────────┘ │
       │ ┌──────────────────────────────────────────────────────┐ │
       │ │                  AI / RAG Service                    │ │
       │ │ (Python FastAPI model using Kaggle Fraud Dataset)    │ │
       │ └──────────────────────────────────────────────────────┘ │
       └───────────────────────────────────────────────────────────┘

       ┌─────────────┐     ┌──────────────┐     ┌──────────────┐
       │ PostgreSQL  │     │ Redis Cache  │     │ Frontend UI  │
       │ Fraud Cases │     │ Cached Scores│     │ Live Alerts  │
       └─────────────┘     └──────────────┘     └──────────────┘
```

---

## ⚙️ Core Features

| Feature | Description |
|----------|-------------|
| **Hybrid Detection** | Combines rule-based + AI detection with configurable rules. |
| **Secure Gateway** | JWT-based routing via Keycloak realm. |
| **Real-time Alerts** | SSE streams for fraud analysts. |
| **Caching (Redis)** | Reduces repeated fraud checks, improving latency. |
| **Modular AI Integration** | Connects to external AI & RAG models. |
| **Explainable Decisions** | MVEL + AI results give clear reasoning. |

---

## 🧩 Tech Stack

| Component | Technology |
|------------|-------------|
| **Backend** | Java 21, Spring Boot 3.5.7 |
| **Security** | Keycloak 25 (OAuth2 / JWT) |
| **Gateway** | Spring Cloud Gateway 2023.x |
| **Database** | PostgreSQL |
| **Cache** | Redis |
| **AI Model** | FastAPI (Python 3.11) |
| **Rule Engine** | MVEL 2.5 |
| **Build Tool** | Maven |
| **Alerting** | Server-Sent Events (SSE) |

---

## 🧱 Microservices

### 1️⃣ API Gateway
- Central entry point for all client requests.
- Handles authentication via Keycloak JWT validation.
- Uses Redis for caching token validations & routes.

### 2️⃣ Fraud Service
- Exposes `/fraud/check` and `/fraud/alerts/stream` endpoints.
- Combines **rule**, **AI**, and **RAG** results.
- Persists fraud cases to PostgreSQL.
- Caches previous requests using Redis.

### 3️⃣ AI & RAG Service
- Python FastAPI microservice using trained fraud model.
- Returns probability score and explanation.
- RAG layer uses embeddings for contextual reasoning.

---

## 🧰 Gateway Service

### Dependencies (`pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

### Example `application.yml`
```yaml
server:
  port: 5001

spring:
  redis:
    host: localhost
    port: 6379

  cloud:
    gateway:
      routes:
        - id: fraud-service
          uri: http://localhost:5000
          predicates:
            - Path=/fraud/**
          filters:
            - RemoveRequestHeader=Cookie
      default-filters:
        - TokenRelay

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/Fraud-Detection
```

### Example Security Config
```java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                .pathMatchers("/actuator/**").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt);
        return http.build();
    }
}
```

---

## 🧮 Fraud Service

**Features:**
- `FraudOrchestratorService` integrates RuleEngine + AIClient.
- `AlertService` sends live alerts via SSE.
- `RedisCacheService` stores and retrieves recent fraud checks.

### Redis Integration Example
```java
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final String FRAUD_KEY_PREFIX = "fraud:";
    private final RedisTemplate<String, Object> redisTemplate;

    public void cacheResult(String transactionId, Object result) {
        redisTemplate.opsForValue().set(FRAUD_KEY_PREFIX + transactionId, result);
    }

    public Object getCachedResult(String transactionId) {
        return redisTemplate.opsForValue().get(FRAUD_KEY_PREFIX + transactionId);
    }
}
```

---

## 🔐 Security with Keycloak

**Keycloak Realm:** `Fraud-Detection`  
**Clients:**
- `apigateway-client` → used by API Gateway  

[//]: # (- `fraud-service` → secured resource server  )

JWT validation is performed via:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/Fraud-Detection
```

Users & Roles:
| Role | Access |
|------|---------|
| `fraud_analyst` | View alerts and manage cases |
| `super_admin` | Manage rules and settings |

---

## ⚡ Redis Integration

### In Both Services
- Caches fraud check results for quick lookups.
- Stores session or token data for faster gateway validation.

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**YAML Config:**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

---

## 📡 API Endpoints

| Service | Method | Endpoint | Description |
|----------|---------|-----------|-------------|
| Gateway | GET | `/actuator/health` | Health check |
| Fraud Service | POST | `/fraud/check` | Run fraud detection |
| Fraud Service | GET | `/fraud/alerts/stream` | Real-time fraud alerts |
| AI Service | POST | `/predict` | AI model fraud probability |

---

## 🧪 Run Locally

### 1️⃣ Start Keycloak
```bash
/opt/keycloak/bin/kc.sh start-dev --features=token-exchange
```

### 2️⃣ Start Redis
```bash
redis-server
```

### 3️⃣ Run Backend Services
```bash
# Gateway
cd gateway
mvn spring-boot:run

# Fraud Service
cd fraud-service
mvn spring-boot:run

# AI Service (Python)
cd ai-service
uvicorn app:app --port 8082 --reload
```

Access:
- Keycloak → http://localhost:8080
- Gateway → http://localhost:5001
- Fraud Service → http://localhost:5000

---

## 🚀 Next Improvements

| Area | Plan |
|------|------|
| **Dynamic Rule Engine** | Add admin UI to manage rules in DB |
| **RAG Explainability** | Use vector DB (e.g., Pinecone/FAISS) for reasoning |
| **Caching Enhancements** | Redis TTL per transaction type |
| **Streaming Integration** | Kafka for distributed event processing |
| **Dashboard UI** | Live alert dashboard for analysts |

---

## 👨‍💻 Contributors
**Team:** _Hackathon Team - Eta — Kifiya & Friends_  
- Mathias — Backend & Architecture  
- Yonatan — AI & FastAPI Service  
- Yonas — Frontend & DevOps 

---

## 🏁 Summary

The **Hybrid Fraud Detection Platform** demonstrates how modern financial systems can combine:
- ✅ **Rule-based logic**
- 🤖 **AI intelligence**
- ⚡ **Redis caching**
- 🔐 **Keycloak security**
- 🔔 **Real-time alerting**

to deliver **fast**, **explainable**, and **scalable** fraud prevention.
