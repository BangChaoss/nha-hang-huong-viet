# Domain 16: Spring Cloud
> **Số practices:** 8 | 🔴 2 | 🟠 5 | 🟡 1
> **Trọng số:** ×1

---

## 16.01 Circuit breaker (Resilience4j) cho external calls | 🔴 BẮT BUỘC

### Metadata
- **ID:** `SB-CLOUD-001`
- **Mức độ:** 🔴 BẮT BUỘC
- **Phạm vi:** External API calls, third-party services
- **Công cụ:** Resilience4j Circuit Breaker
- **Liên quan:** 16.07 (Timeout), 16.08 (Fallback)

### Tại sao?

**Vấn đề:**
- Service downstream chậm/lỗi kéo theo cascade failure
- Thread pool cạn kiệt khi retry liên tục
- Không có cơ chế "fail fast" khi service không khả dụng
- Khó khôi phục khi service downstream ổn định lại

**Lợi ích:**
- ✅ Ngăn chặn cascade failure trong microservices
- ✅ Giải phóng tài nguyên khi service downstream lỗi
- ✅ Tự động phát hiện và phục hồi
- ✅ Monitoring qua metrics (opened, half-open, closed states)

**Hệ quả nếu vi phạm:**
- ⚠️ **P0**: Toàn bộ hệ thống sập khi một service lỗi
- ⚠️ **P1**: Thread starvation, OOM errors
- ⚠️ **P2**: Không có visibility về service health

### ✅ Cách đúng

**1. Dependency:**
```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.2.0</version>
</dependency>
```

**2. Configuration:**
```yaml
# application.yml
resilience4j.circuitbreaker:
  configs:
    default:
      registerHealthIndicator: true
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      failureRateThreshold: 50
      slowCallRateThreshold: 50
      slowCallDurationThreshold: 2s
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
      recordExceptions:
        - java.net.ConnectException
        - java.util.concurrent.TimeoutException
      ignoreExceptions:
        - jp.medicalbox.exception.BusinessException

  instances:
    paymentService:
      baseConfig: default
      failureRateThreshold: 60
      waitDurationInOpenState: 30s

    notificationService:
      baseConfig: default
      slidingWindowSize: 20
      minimumNumberOfCalls: 10
```

**3. Service với Circuit Breaker:**
```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final RestClient paymentClient;

  @CircuitBreaker(
    name = "paymentService",
    fallbackMethod = "processPaymentFallback"
  )
  public PaymentResponse processPayment(PaymentRequest request) {
    log.info("Calling payment service for amount: {}", request.amount());

    return paymentClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }

  // Fallback method phải có cùng signature + Throwable
  private PaymentResponse processPaymentFallback(
    PaymentRequest request,
    Throwable throwable
  ) {
    log.error("Payment service unavailable, using fallback. Error: {}",
      throwable.getMessage());

    // Return degraded response
    return new PaymentResponse(
      null,
      "PENDING",
      "Payment queued for processing"
    );
  }
}
```

**4. RestClient với Timeout (kết hợp 16.07):**
```java
package jp.medicalbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestClientConfig {

  @Bean
  public RestClient paymentClient() {
    HttpClient httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
      .responseTimeout(Duration.ofSeconds(10))
      .doOnConnected(conn -> conn
        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS))
      );

    return RestClient.builder()
      .baseUrl("https://payment-api.example.com")
      .requestFactory(new ReactorClientHttpConnector(httpClient))
      .build();
  }
}
```

**5. Monitoring Circuit Breaker Events:**
```java
package jp.medicalbox.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CircuitBreakerMonitoringConfig {

  public CircuitBreakerMonitoringConfig(
    CircuitBreakerRegistry registry
  ) {
    registry.getEventPublisher()
      .onEntryAdded(this::onEntryAdded);
  }

  private void onEntryAdded(
    EntryAddedEvent<CircuitBreaker> event
  ) {
    CircuitBreaker circuitBreaker = event.getAddedEntry();

    circuitBreaker.getEventPublisher()
      .onEvent(this::logEvent);
  }

  private void logEvent(CircuitBreakerEvent event) {
    switch (event.getEventType()) {
      case STATE_TRANSITION -> log.warn(
        "Circuit breaker '{}' changed state to: {}",
        event.getCircuitBreakerName(),
        event
      );
      case ERROR -> log.error(
        "Circuit breaker '{}' recorded error: {}",
        event.getCircuitBreakerName(),
        event
      );
      case SUCCESS -> log.debug(
        "Circuit breaker '{}' recorded success",
        event.getCircuitBreakerName()
      );
      default -> log.trace(
        "Circuit breaker '{}' event: {}",
        event.getCircuitBreakerName(),
        event.getEventType()
      );
    }
  }
}
```

**6. Health Check Integration:**
```yaml
# application.yml
management:
  health:
    circuitbreakers:
      enabled: true
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,metrics,circuitbreakers
```

### ❌ Cách sai

```java
// ❌ SAI: Không có circuit breaker
@Service
public class PaymentService {

  public PaymentResponse processPayment(PaymentRequest request) {
    // Gọi trực tiếp - sẽ retry mãi khi service lỗi
    return restClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }
}

// ❌ SAI: Fallback method sai signature
@CircuitBreaker(name = "payment", fallbackMethod = "fallback")
public PaymentResponse process(PaymentRequest request) {
  return callExternalApi(request);
}

// Thiếu Throwable parameter
private PaymentResponse fallback(PaymentRequest request) {
  return new PaymentResponse();
}

// ❌ SAI: Không configure timeout
resilience4j.circuitbreaker:
  instances:
    payment:
      # Chỉ có circuit breaker, không có timeout
      # => Circuit breaker không mở khi slow calls
      failureRateThreshold: 50

// ❌ SAI: slidingWindowSize quá nhỏ
resilience4j.circuitbreaker:
  instances:
    payment:
      slidingWindowSize: 2  # Quá nhỏ, dễ false positive
      minimumNumberOfCalls: 1

// ❌ SAI: Không monitor events
@CircuitBreaker(name = "payment")
public PaymentResponse process(PaymentRequest request) {
  // Không có logging/metrics => không biết khi nào circuit mở
  return callApi(request);
}

// ❌ SAI: Circuit breaker cho business logic
@CircuitBreaker(name = "validation")
public void validateUser(User user) {
  // Không nên dùng circuit breaker cho validation logic
  // Chỉ dùng cho external calls
  if (user.getAge() < 18) {
    throw new ValidationException();
  }
}
```

### Phát hiện

```bash
# 1. Tìm external API calls không có @CircuitBreaker
rg "RestClient|WebClient|RestTemplate" --type java -A 5 | \
  rg -v "@CircuitBreaker"

# 2. Kiểm tra fallback method signature
rg "fallbackMethod\s*=\s*\"(\w+)\"" --type java -o | \
  sed 's/.*"\(.*\)".*/\1/' | \
  while read method; do
    rg "private.*$method\([^)]*\)" --type java | \
      rg -v "Throwable"
  done

# 3. Tìm config thiếu timeout
rg "resilience4j.circuitbreaker" config/ -A 20 | \
  rg -v "slowCallDurationThreshold"

# 4. Kiểm tra slidingWindowSize quá nhỏ
yq '.resilience4j.circuitbreaker.instances.*.slidingWindowSize' \
  application.yml | \
  awk '$1 < 5 {print "WARNING: slidingWindowSize too small:", $1}'
```

### Checklist

- [ ] Mọi external API call đều có `@CircuitBreaker`
- [ ] Fallback method có đúng signature (+ `Throwable`)
- [ ] Config `slowCallDurationThreshold` và `slowCallRateThreshold`
- [ ] `slidingWindowSize >= 10` và `minimumNumberOfCalls >= 5`
- [ ] `waitDurationInOpenState` phù hợp với SLA (10-60s)
- [ ] `recordExceptions` chỉ gồm network/timeout exceptions
- [ ] `ignoreExceptions` gồm business exceptions
- [ ] Enable health indicator và metrics
- [ ] Monitor circuit breaker state transitions
- [ ] Test circuit breaker behavior (manual test hoặc chaos engineering)

---

## 16.02 Service discovery (Eureka / Consul / K8s DNS) | 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `SB-CLOUD-002`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Phạm vi:** Microservices communication
- **Công cụ:** Spring Cloud LoadBalancer, Kubernetes DNS
- **Liên quan:** 16.03 (API Gateway)

### Tại sao?

**Vấn đề:**
- Hard-coded service URLs không scale
- Khó deploy nhiều instances (manual load balancing)
- Không tự động failover khi instance chết
- Health check thủ công, chậm phát hiện lỗi

**Lợi ích:**
- ✅ Dynamic service registration/deregistration
- ✅ Client-side load balancing tự động
- ✅ Health-based routing (chỉ gọi healthy instances)
- ✅ Zero-downtime deployment (rolling update)

**Hệ quả nếu vi phạm:**
- ⚠️ **P1**: Phải restart client khi service URL thay đổi
- ⚠️ **P2**: Manual load balancing, không tối ưu
- ⚠️ **P2**: Downtime khi deploy

### ✅ Cách đúng

**Option 1: Kubernetes DNS (Khuyến nghị cho K8s deployment)**

```yaml
# deployment.yaml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: medicalbox
spec:
  selector:
    app: payment-service
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: medicalbox
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
        - name: payment-service
          image: medicalbox/payment-service:1.0.0
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
```

```java
// Client service (API Gateway hoặc service khác)
package jp.medicalbox.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  @Bean
  @LoadBalanced  // Spring Cloud LoadBalancer tự động resolve DNS
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  public RestClient paymentClient(RestClient.Builder builder) {
    return builder
      .baseUrl("http://payment-service.medicalbox.svc.cluster.local:8080")
      .build();
  }
}
```

**Option 2: Spring Cloud LoadBalancer với custom service registry**

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

```java
package jp.medicalbox.config;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;

@Configuration
public class LoadBalancerConfig {

  @Bean
  public ServiceInstanceListSupplier serviceInstanceListSupplier() {
    return new ServiceInstanceListSupplier() {
      @Override
      public String getServiceId() {
        return "payment-service";
      }

      @Override
      public Flux<List<ServiceInstance>> get() {
        // Lấy từ config server hoặc database
        return Flux.just(List.of(
          new DefaultServiceInstance(
            "payment-1",
            "payment-service",
            "payment-node1.example.com",
            8080,
            false
          ),
          new DefaultServiceInstance(
            "payment-2",
            "payment-service",
            "payment-node2.example.com",
            8080,
            false
          )
        ));
      }
    };
  }
}
```

**Option 3: Consul Service Discovery (cho VM/bare-metal)**

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-consul-discovery</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  application:
    name: payment-service
  cloud:
    consul:
      host: consul.example.com
      port: 8500
      discovery:
        enabled: true
        register: true
        instanceId: ${spring.application.name}:${random.value}
        healthCheckPath: /actuator/health
        healthCheckInterval: 10s
        tags:
          - version=1.0.0
          - zone=asia-southeast1
```

```java
package jp.medicalbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final DiscoveryClient discoveryClient;
  private final RestClient.Builder restClientBuilder;

  public void sendNotification(String message) {
    List<ServiceInstance> instances =
      discoveryClient.getInstances("notification-service");

    if (instances.isEmpty()) {
      throw new IllegalStateException(
        "No instances of notification-service available"
      );
    }

    // Spring Cloud LoadBalancer tự động round-robin
    ServiceInstance instance = instances.get(0);

    restClientBuilder.build()
      .post()
      .uri(instance.getUri() + "/api/notifications")
      .body(new NotificationRequest(message))
      .retrieve()
      .toBodilessEntity();
  }
}
```

**Health Check Configuration:**

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

```java
package jp.medicalbox.health;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ReadinessManager {

  private final ApplicationEventPublisher eventPublisher;

  public ReadinessManager(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void markAsReady() {
    AvailabilityChangeEvent.publish(
      eventPublisher,
      this,
      ReadinessState.ACCEPTING_TRAFFIC
    );
  }

  public void markAsNotReady() {
    AvailabilityChangeEvent.publish(
      eventPublisher,
      this,
      ReadinessState.REFUSING_TRAFFIC
    );
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Hard-coded URL
@Service
public class PaymentService {

  private final RestClient restClient = RestClient.builder()
    .baseUrl("http://192.168.1.100:8080")  // Hard-coded IP
    .build();
}

// ❌ SAI: Không có health check
@Configuration
public class ServiceConfig {

  @Bean
  public ServiceInstanceListSupplier supplier() {
    return new ServiceInstanceListSupplier() {
      @Override
      public Flux<List<ServiceInstance>> get() {
        // Trả về tất cả instances, kể cả unhealthy
        return Flux.just(allInstances);
      }
    };
  }
}

// ❌ SAI: Manual round-robin (không cần thiết)
@Service
public class NotificationService {

  private final List<String> urls = List.of(
    "http://node1:8080",
    "http://node2:8080"
  );
  private int currentIndex = 0;

  public void send(String message) {
    String url = urls.get(currentIndex++ % urls.size());
    // Spring Cloud LoadBalancer đã làm việc này tự động
    restClient.post().uri(url + "/api/notifications")...;
  }
}

// ❌ SAI: Không register với service registry
# application.yml (Consul)
spring:
  cloud:
    consul:
      discovery:
        register: false  # Chỉ discover, không register
        # => Instances mới không được phát hiện

// ❌ SAI: Thiếu instanceId unique
spring:
  cloud:
    consul:
      discovery:
        instanceId: ${spring.application.name}
        # => Conflict khi scale nhiều instances
```

### Phát hiện

```bash
# 1. Tìm hard-coded service URLs
rg "http://[0-9]+\." --type java
rg "baseUrl.*http" --type java | rg -v "localhost|example.com"

# 2. Kiểm tra thiếu @LoadBalanced
rg "@Bean.*RestClient" --type java -A 5 | rg -v "@LoadBalanced"

# 3. Kiểm tra health check configuration
rg "livenessProbe|readinessProbe" k8s/ deployment/
rg "management.health.probes.enabled" --type yaml

# 4. Tìm manual load balancing logic
rg "currentIndex|roundRobin|random.*instance" --type java
```

### Checklist

- [ ] Không có hard-coded service URLs (除外 localhost cho dev)
- [ ] Sử dụng `@LoadBalanced` RestClient.Builder
- [ ] Configure liveness và readiness probes
- [ ] Unique instanceId khi register (dùng `${random.value}`)
- [ ] Health check interval phù hợp (10-30s)
- [ ] Deregister khi shutdown gracefully
- [ ] Test failover khi kill instance
- [ ] Monitor service discovery metrics

---

## 16.03 API Gateway cho routing và cross-cutting concerns | 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `SB-CLOUD-003`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Phạm vi:** Microservices architecture
- **Công cụ:** Spring Cloud Gateway
- **Liên quan:** 16.01 (Circuit Breaker), 16.02 (Service Discovery)

### Tại sao?

**Vấn đề:**
- Mỗi service tự implement authentication/rate limiting (duplicate code)
- Client phải biết URL của từng microservice
- Khó enforce security policies nhất quán
- CORS configuration phân tán

**Lợi ích:**
- ✅ Single entry point cho tất cả services
- ✅ Centralized authentication, authorization, rate limiting
- ✅ Request/response transformation
- ✅ Monitoring và logging tập trung

**Hệ quả nếu vi phạm:**
- ⚠️ **P1**: Duplicate security logic, dễ sót lỗ hổng
- ⚠️ **P2**: Client coupling với service URLs
- ⚠️ **P2**: Khó thay đổi routing logic

### ✅ Cách đúng

**1. Dependency:**

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```

**2. Gateway Configuration:**

```yaml
# application.yml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              replenishRate: 100
              burstCapacity: 200
        - name: CircuitBreaker
          args:
            name: defaultCircuitBreaker
            fallbackUri: forward:/fallback

      routes:
        # User Service
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1
            - name: AuthenticationFilter
            - name: CircuitBreaker
              args:
                name: userServiceCircuit
                fallbackUri: forward:/fallback/users

        # Payment Service
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=1
            - name: AuthenticationFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenishRate: 50
                  burstCapacity: 100

        # Notification Service (Internal only)
        - id: notification-service-internal
          uri: lb://notification-service
          predicates:
            - Path=/internal/notifications/**
            - Header=X-Internal-Request, true
          filters:
            - StripPrefix=1

      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "https://app.medicalbox.jp"
              - "https://admin.medicalbox.jp"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600

resilience4j:
  circuitbreaker:
    instances:
      userServiceCircuit:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

**3. Custom Authentication Filter:**

```java
package jp.medicalbox.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends
  AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

  public AuthenticationFilter() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();

      // Bỏ qua authentication cho public endpoints
      if (isPublicEndpoint(request.getPath().value())) {
        return chain.filter(exchange);
      }

      // Validate JWT token
      String token = extractToken(request);

      if (token == null || !isValidToken(token)) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      // Add user info to request headers
      String userId = extractUserId(token);
      ServerHttpRequest modifiedRequest = request.mutate()
        .header("X-User-Id", userId)
        .header("X-Authenticated", "true")
        .build();

      ServerWebExchange modifiedExchange = exchange.mutate()
        .request(modifiedRequest)
        .build();

      return chain.filter(modifiedExchange);
    };
  }

  private boolean isPublicEndpoint(String path) {
    return path.startsWith("/api/auth/login") ||
           path.startsWith("/api/auth/register") ||
           path.startsWith("/actuator/health");
  }

  private String extractToken(ServerHttpRequest request) {
    String authHeader = request.getHeaders()
      .getFirst("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  private boolean isValidToken(String token) {
    // JWT validation logic (có thể gọi auth service)
    try {
      // Validate signature, expiration, etc.
      return true;
    } catch (Exception e) {
      log.error("Token validation failed", e);
      return false;
    }
  }

  private String extractUserId(String token) {
    // Extract user ID from JWT claims
    return "user123";
  }

  public static class Config {
    // Configuration properties nếu cần
  }
}
```

**4. Request/Response Logging Filter:**

```java
package jp.medicalbox.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(
    ServerWebExchange exchange,
    GatewayFilterChain chain
  ) {
    long startTime = System.currentTimeMillis();
    String requestId = exchange.getRequest().getId();
    String path = exchange.getRequest().getPath().value();
    String method = exchange.getRequest().getMethod().name();

    log.info("Request [{}] {} {}", requestId, method, path);

    return chain.filter(exchange)
      .doFinally(signalType -> {
        long duration = System.currentTimeMillis() - startTime;
        int statusCode = exchange.getResponse().getStatusCode() != null
          ? exchange.getResponse().getStatusCode().value()
          : 0;

        log.info(
          "Response [{}] {} {} - Status: {} - Duration: {}ms",
          requestId,
          method,
          path,
          statusCode,
          duration
        );
      });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
```

**5. Fallback Controller:**

```java
package jp.medicalbox.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

  @GetMapping
  public ResponseEntity<Map<String, String>> defaultFallback() {
    return ResponseEntity
      .status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(Map.of(
        "error", "Service temporarily unavailable",
        "message", "Please try again later"
      ));
  }

  @GetMapping("/users")
  public ResponseEntity<Map<String, String>> userServiceFallback() {
    return ResponseEntity
      .status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(Map.of(
        "error", "User service unavailable",
        "message", "User operations are temporarily disabled"
      ));
  }
}
```

**6. Rate Limiting với Redis:**

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
```

```java
package jp.medicalbox.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> {
      // Rate limit per user
      String userId = exchange.getRequest()
        .getHeaders()
        .getFirst("X-User-Id");

      return Mono.just(userId != null ? userId : "anonymous");
    };
  }

  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange -> {
      // Rate limit per IP
      String ip = exchange.getRequest()
        .getRemoteAddress()
        .getAddress()
        .getHostAddress();

      return Mono.just(ip);
    };
  }
}
```

### ❌ Cách sai

```yaml
# ❌ SAI: Hardcoded service URLs (không dùng service discovery)
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://192.168.1.100:8080  # Hard-coded
          predicates:
            - Path=/api/users/**

# ❌ SAI: Không có circuit breaker
spring:
  cloud:
    gateway:
      routes:
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          # Thiếu CircuitBreaker filter
          # => Cascade failure khi service chậm

# ❌ SAI: Duplicate authentication trong services
# Gateway không có auth filter => mỗi service tự validate JWT
# => Duplicate code, khó maintain
```

```java
// ❌ SAI: Blocking I/O trong filter
@Component
public class BadAuthFilter implements GlobalFilter {

  @Override
  public Mono<Void> filter(
    ServerWebExchange exchange,
    GatewayFilterChain chain
  ) {
    // Blocking call trong reactive stack
    boolean valid = authService.validateToken(token);  // BLOCKING!

    if (!valid) {
      return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
  }
}

// ❌ SAI: Không log request/response
// => Khó debug khi có lỗi

// ❌ SAI: Không có fallback
spring:
  cloud:
    gateway:
      routes:
        - id: critical-service
          uri: lb://critical-service
          predicates:
            - Path=/api/critical/**
          # Thiếu fallbackUri
          # => User thấy 500 error khi service down
```

### Phát hiện

```bash
# 1. Tìm hard-coded URIs trong gateway config
rg "uri:\s*http://[0-9]+" config/ --type yaml

# 2. Kiểm tra routes thiếu CircuitBreaker
yq '.spring.cloud.gateway.routes[] |
  select(.filters | map(select(.name == "CircuitBreaker")) | length == 0) |
  .id' application.yml

# 3. Tìm blocking calls trong filters
rg "implements GlobalFilter" --type java -A 20 | \
  rg "\.get\(|\.post\(|\.call\(" | \
  rg -v "Mono|Flux"

# 4. Kiểm tra thiếu rate limiting
yq '.spring.cloud.gateway.routes[] |
  select(.filters | map(select(.name == "RequestRateLimiter")) | length == 0) |
  .id' application.yml
```

### Checklist

- [ ] Tất cả routes dùng `lb://` (service discovery)
- [ ] Mỗi route có CircuitBreaker filter với fallback
- [ ] Global authentication filter cho protected endpoints
- [ ] Request/response logging filter
- [ ] Rate limiting cho public endpoints
- [ ] CORS configuration tập trung
- [ ] Không có blocking I/O trong filters
- [ ] Health check endpoint (`/actuator/health`)
- [ ] Test failover khi downstream service lỗi

---

## 16.04 Config Server cho centralized configuration | 🟡 NÊN CÓ

### Metadata
- **ID:** `SB-CLOUD-004`
- **Mức độ:** 🟡 NÊN CÓ
- **Phạm vi:** Configuration management
- **Công cụ:** Spring Cloud Config Server
- **Liên quan:** 16.02 (Service Discovery)

### Tại sao?

**Vấn đề:**
- Configuration phân tán trong từng service
- Phải rebuild/restart để thay đổi config
- Khó quản lý config cho nhiều environments (dev/staging/prod)
- Không có version control cho config changes

**Lợi ích:**
- ✅ Centralized configuration cho tất cả services
- ✅ Environment-specific config (dev/staging/prod)
- ✅ Refresh config không cần restart (với `@RefreshScope`)
- ✅ Config versioning với Git

**Hệ quả nếu vi phạm:**
- ⚠️ **P2**: Phải rebuild khi thay đổi config
- ⚠️ **P3**: Config drift giữa các environments

### ✅ Cách đúng

**1. Config Server Setup:**

```xml
<!-- config-server/pom.xml -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-config-server</artifactId>
</dependency>
```

```java
package jp.medicalbox.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConfigServerApplication.class, args);
  }
}
```

```yaml
# config-server/application.yml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/medicalbox/config-repo
          default-label: main
          search-paths:
            - '{application}'
            - '{application}/{profile}'
          clone-on-start: true
          force-pull: true
        # Hoặc dùng local file system cho dev
        # native:
        #   search-locations: file:///opt/config
  security:
    user:
      name: config-admin
      password: ${CONFIG_SERVER_PASSWORD}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**2. Git Repository Structure:**

```
config-repo/
├── application.yml              # Shared config cho tất cả services
├── application-dev.yml          # Dev environment
├── application-staging.yml      # Staging environment
├── application-prod.yml         # Production environment
├── user-service/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
├── payment-service/
│   ├── application.yml
│   └── application-prod.yml
└── api-gateway/
    └── application.yml
```

```yaml
# config-repo/application.yml (shared)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    jp.medicalbox: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

# config-repo/application-prod.yml
logging:
  level:
    jp.medicalbox: WARN

# config-repo/user-service/application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

app:
  features:
    email-verification: true
    sms-notification: false

# config-repo/user-service/application-prod.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10

app:
  features:
    sms-notification: true
```

**3. Client Configuration:**

```xml
<!-- user-service/pom.xml -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# user-service/application.yml
spring:
  application:
    name: user-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: "optional:configserver:http://config-server:8888"
  cloud:
    config:
      username: config-admin
      password: ${CONFIG_SERVER_PASSWORD}
      fail-fast: true
      retry:
        max-attempts: 6
        initial-interval: 1000
        multiplier: 1.5

management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
```

**4. Dynamic Config Refresh:**

```java
package jp.medicalbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope  // Enable dynamic refresh
@ConfigurationProperties(prefix = "app.features")
public class FeatureConfig {

  private boolean emailVerification;
  private boolean smsNotification;
  private boolean paymentGateway;
  private int maxUploadSizeMb;
}
```

```java
package jp.medicalbox.service;

import jp.medicalbox.config.FeatureConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final FeatureConfig featureConfig;

  public void registerUser(UserDto user) {
    // ...registration logic...

    // Dynamic feature toggle
    if (featureConfig.isEmailVerification()) {
      log.info("Sending verification email");
      sendVerificationEmail(user.getEmail());
    }

    if (featureConfig.isSmsNotification()) {
      log.info("Sending SMS notification");
      sendSmsNotification(user.getPhone());
    }
  }
}
```

**5. Refresh Config (không cần restart):**

```bash
# Update config trong Git repo
git commit -m "Enable SMS notification for prod"
git push origin main

# Trigger refresh cho service instance
curl -X POST http://user-service:8080/actuator/refresh \
  -H "Content-Type: application/json"

# Hoặc refresh tất cả instances qua Spring Cloud Bus (nếu có)
curl -X POST http://config-server:8888/actuator/bus-refresh
```

**6. Encrypted Sensitive Config:**

```bash
# Generate encryption key
keytool -genkeypair -alias config-server-key \
  -keyalg RSA -keystore config-server.jks \
  -storepass mypassword
```

```yaml
# config-server/application.yml
encrypt:
  key-store:
    location: classpath:/config-server.jks
    password: mypassword
    alias: config-server-key
```

```yaml
# config-repo/user-service/application-prod.yml
spring:
  datasource:
    password: '{cipher}AQBkP8...'  # Encrypted value

# Encrypt command
curl http://config-server:8888/encrypt -d "mySecretPassword"
```

### ❌ Cách sai

```yaml
# ❌ SAI: Config nằm trong application.yml của service
# user-service/application.yml
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/userdb
    password: prod-password  # Hard-coded, không encrypted
# => Phải rebuild để thay đổi

# ❌ SAI: Không có @RefreshScope
@Component
@ConfigurationProperties(prefix = "app.features")
public class FeatureConfig {
  // Không có @RefreshScope
  // => Phải restart để refresh config
}

# ❌ SAI: fail-fast = false trong prod
spring:
  cloud:
    config:
      fail-fast: false
# => Service start với stale config khi config server down

# ❌ SAI: Sensitive data không encrypted
# config-repo/payment-service/application-prod.yml
payment:
  api-key: "sk_live_xxxxxxxxxxxx"  # Plain text!
  secret: "secret_key_123"
```

```java
// ❌ SAI: Inject config trực tiếp từ @Value (không refresh được)
@Service
public class PaymentService {

  @Value("${payment.api-key}")
  private String apiKey;  // Không refresh được khi config thay đổi

  // Nên dùng @ConfigurationProperties với @RefreshScope
}
```

### Phát hiện

```bash
# 1. Tìm hard-coded passwords
rg "password:\s*['\"].*['\"]" --type yaml config/

# 2. Kiểm tra sensitive config không encrypted
rg "api[-_]?key|secret|password" config-repo/ --type yaml | \
  rg -v '\{cipher\}'

# 3. Tìm @Value injection (không refresh được)
rg "@Value.*\\\$\{" --type java

# 4. Kiểm tra thiếu @RefreshScope
rg "@ConfigurationProperties" --type java -A 5 | \
  rg -v "@RefreshScope"
```

### Checklist

- [ ] Config Server chạy và accessible
- [ ] Git repo có structure rõ ràng (application/profile)
- [ ] Client config có `spring.config.import` pointing to config server
- [ ] `fail-fast: true` trong production
- [ ] Sensitive data được encrypt với `{cipher}`
- [ ] `@RefreshScope` trên các config beans cần dynamic refresh
- [ ] Actuator `/refresh` endpoint enabled
- [ ] Test refresh config không cần restart

---

## 16.05 Bulkhead pattern tách resource pools | 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `SB-CLOUD-005`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Phạm vi:** Resource isolation
- **Công cụ:** Resilience4j Bulkhead
- **Liên quan:** 16.01 (Circuit Breaker)

### Tại sao?

**Vấn đề:**
- Slow API call chiếm toàn bộ thread pool
- Một dependency chậm làm chậm toàn bộ ứng dụng
- Không isolate critical vs non-critical operations
- Thread starvation khi có spike traffic

**Lợi ích:**
- ✅ Isolate thread pools cho từng dependency
- ✅ Critical operations không bị ảnh hưởng bởi non-critical
- ✅ Prevent cascading failures
- ✅ Better resource utilization

**Hệ quả nếu vi phạm:**
- ⚠️ **P1**: Slow dependency làm chậm toàn bộ app
- ⚠️ **P1**: Thread pool exhaustion
- ⚠️ **P2**: Không thể prioritize critical operations

### ✅ Cách đúng

**1. Dependency:**

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.2.0</version>
</dependency>
```

**2. Bulkhead Configuration:**

```yaml
# application.yml
resilience4j.bulkhead:
  configs:
    default:
      maxConcurrentCalls: 10
      maxWaitDuration: 1000ms

  instances:
    paymentService:
      baseConfig: default
      maxConcurrentCalls: 5  # Giới hạn 5 concurrent calls
      maxWaitDuration: 2000ms

    notificationService:
      baseConfig: default
      maxConcurrentCalls: 20  # Non-critical, cho phép nhiều hơn
      maxWaitDuration: 500ms

    reportingService:
      baseConfig: default
      maxConcurrentCalls: 3  # CPU-intensive, giới hạn thấp
      maxWaitDuration: 5000ms

# Thread pool bulkhead (cho async operations)
resilience4j.thread-pool-bulkhead:
  configs:
    default:
      maxThreadPoolSize: 10
      coreThreadPoolSize: 5
      queueCapacity: 20
      keepAliveDuration: 20ms

  instances:
    asyncPaymentService:
      maxThreadPoolSize: 8
      coreThreadPoolSize: 4
      queueCapacity: 50

management:
  metrics:
    tags:
      application: ${spring.application.name}
  endpoint:
    health:
      show-details: always
```

**3. Semaphore Bulkhead (synchronous):**

```java
package jp.medicalbox.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final RestClient paymentClient;

  @Bulkhead(
    name = "paymentService",
    type = Bulkhead.Type.SEMAPHORE,
    fallbackMethod = "processPaymentFallback"
  )
  @CircuitBreaker(name = "paymentService")
  public PaymentResponse processPayment(PaymentRequest request) {
    log.info("Processing payment for amount: {}", request.amount());

    return paymentClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }

  private PaymentResponse processPaymentFallback(
    PaymentRequest request,
    Throwable throwable
  ) {
    log.error(
      "Payment bulkhead full or circuit open: {}",
      throwable.getMessage()
    );

    return new PaymentResponse(
      null,
      "QUEUED",
      "Payment request queued due to high load"
    );
  }
}
```

**4. Thread Pool Bulkhead (asynchronous):**

```java
package jp.medicalbox.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final RestClient notificationClient;

  @Bulkhead(
    name = "asyncNotificationService",
    type = Bulkhead.Type.THREADPOOL,
    fallbackMethod = "sendNotificationFallback"
  )
  public CompletableFuture<NotificationResponse> sendNotificationAsync(
    NotificationRequest request
  ) {
    log.info("Sending async notification: {}", request.type());

    return CompletableFuture.supplyAsync(() ->
      notificationClient.post()
        .uri("/api/notifications")
        .body(request)
        .retrieve()
        .body(NotificationResponse.class)
    );
  }

  private CompletableFuture<NotificationResponse> sendNotificationFallback(
    NotificationRequest request,
    Throwable throwable
  ) {
    log.warn(
      "Notification thread pool full: {}",
      throwable.getMessage()
    );

    return CompletableFuture.completedFuture(
      new NotificationResponse(
        "QUEUED",
        "Notification queued for later delivery"
      )
    );
  }
}
```

**5. Custom Thread Pool Configuration:**

```java
package jp.medicalbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

  @Bean(name = "reportingExecutor")
  public Executor reportingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("reporting-");
    executor.setRejectedExecutionHandler(
      new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );
    executor.initialize();
    return executor;
  }

  @Bean(name = "emailExecutor")
  public Executor emailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("email-");
    executor.setRejectedExecutionHandler(
      new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );
    executor.initialize();
    return executor;
  }
}
```

```java
package jp.medicalbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

  @Async("reportingExecutor")  // Use dedicated thread pool
  public CompletableFuture<ReportData> generateReport(
    String reportId
  ) {
    log.info("Generating report: {}", reportId);

    // CPU-intensive operation isolated in separate pool
    ReportData data = performHeavyCalculation(reportId);

    return CompletableFuture.completedFuture(data);
  }

  private ReportData performHeavyCalculation(String reportId) {
    // Simulate heavy computation
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return new ReportData(reportId, "Sample data");
  }
}
```

**6. Monitoring Bulkhead Metrics:**

```java
package jp.medicalbox.monitoring;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BulkheadMonitoringConfig {

  public BulkheadMonitoringConfig(BulkheadRegistry registry) {
    registry.getAllBulkheads().forEach(bulkhead -> {
      bulkhead.getEventPublisher()
        .onEvent(event -> logBulkheadEvent(bulkhead, event));
    });
  }

  private void logBulkheadEvent(
    Bulkhead bulkhead,
    BulkheadEvent event
  ) {
    switch (event.getEventType()) {
      case CALL_PERMITTED -> log.debug(
        "Bulkhead '{}' call permitted. Available: {}/{}",
        bulkhead.getName(),
        bulkhead.getMetrics().getAvailableConcurrentCalls(),
        bulkhead.getBulkheadConfig().getMaxConcurrentCalls()
      );
      case CALL_REJECTED -> log.warn(
        "Bulkhead '{}' call REJECTED. Queue full!",
        bulkhead.getName()
      );
      case CALL_FINISHED -> log.debug(
        "Bulkhead '{}' call finished",
        bulkhead.getName()
      );
    }
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Dùng chung thread pool cho tất cả external calls
@Service
public class IntegrationService {

  private final RestClient restClient;

  public PaymentResponse callPaymentService() {
    // Dùng default thread pool
    return restClient.post()...;
  }

  public void sendEmail() {
    // Cùng thread pool => email slow làm payment slow
    return restClient.post()...;
  }
}

// ❌ SAI: maxConcurrentCalls quá cao
resilience4j.bulkhead:
  instances:
    paymentService:
      maxConcurrentCalls: 1000  # Quá cao, không limit được
      # => Vẫn bị thread starvation

// ❌ SAI: maxWaitDuration quá lâu
resilience4j.bulkhead:
  instances:
    notificationService:
      maxWaitDuration: 60000ms  # 60s quá lâu
      # => User chờ lâu, bad UX

// ❌ SAI: Không có fallback
@Bulkhead(name = "payment")
public PaymentResponse process(PaymentRequest req) {
  // Không có fallbackMethod
  // => User thấy 429 Too Many Requests
  return callApi(req);
}

// ❌ SAI: CPU-intensive task không isolate
@Service
public class ReportService {

  @Async  // Dùng default thread pool
  public void generateHeavyReport() {
    // CPU-intensive task chiếm hết threads
    // => Ảnh hưởng các operations khác
    heavyCalculation();
  }
}
```

### Phát hiện

```bash
# 1. Tìm external calls không có @Bulkhead
rg "RestClient|WebClient|RestTemplate" --type java -A 10 | \
  rg -v "@Bulkhead"

# 2. Kiểm tra maxConcurrentCalls quá cao
yq '.resilience4j.bulkhead.instances.*.maxConcurrentCalls' \
  application.yml | \
  awk '$1 > 100 {print "WARNING: maxConcurrentCalls too high:", $1}'

# 3. Tìm @Async không chỉ định executor
rg "@Async\s*$" --type java

# 4. Kiểm tra fallback methods
rg "@Bulkhead.*fallbackMethod" --type java -o | \
  sed 's/.*fallbackMethod\s*=\s*"\(.*\)".*/\1/' | \
  while read method; do
    rg "private.*$method" --type java || echo "Missing fallback: $method"
  done
```

### Checklist

- [ ] Mỗi external dependency có riêng bulkhead instance
- [ ] `maxConcurrentCalls` hợp lý (5-20 cho hầu hết cases)
- [ ] `maxWaitDuration` ngắn (1-5s) để fail fast
- [ ] CPU-intensive tasks có dedicated thread pool
- [ ] Mọi `@Bulkhead` có `fallbackMethod`
- [ ] Monitor bulkhead metrics (available calls, queue size)
- [ ] Test behavior khi bulkhead full
- [ ] Critical operations có priority cao (smaller pool, faster fail)

---

## 16.06 Retry với exponential backoff + jitter | 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `SB-CLOUD-006`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Phạm vi:** Transient failure handling
- **Công cụ:** Resilience4j Retry
- **Liên quan:** 16.01 (Circuit Breaker), 16.07 (Timeout)

### Tại sao?

**Vấn đề:**
- Transient failures (network blip, temporary service hiccup) gây request fail
- Immediate retry gây "thundering herd" khi service phục hồi
- Fixed delay retry không tối ưu (waste time hoặc overwhelm service)
- Retry mãi mà không có giới hạn

**Lợi ích:**
- ✅ Tự động recover từ transient failures
- ✅ Exponential backoff giảm load lên downstream service
- ✅ Jitter tránh synchronized retries (thundering herd)
- ✅ Cải thiện success rate mà không cần manual intervention

**Hệ quả nếu vi phạm:**
- ⚠️ **P2**: Transient errors gây false alarms
- ⚠️ **P2**: Thundering herd khi service recovery
- ⚠️ **P3**: Tăng latency không cần thiết

### ✅ Cách đúng

**1. Configuration:**

```yaml
# application.yml
resilience4j.retry:
  configs:
    default:
      maxAttempts: 3
      waitDuration: 1000ms
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2
      enableRandomizedWait: true  # Jitter
      randomizedWaitFactor: 0.5
      retryExceptions:
        - java.net.ConnectException
        - java.util.concurrent.TimeoutException
        - org.springframework.web.client.ResourceAccessException
      ignoreExceptions:
        - jp.medicalbox.exception.BusinessException
        - java.lang.IllegalArgumentException

  instances:
    paymentService:
      baseConfig: default
      maxAttempts: 5
      waitDuration: 500ms
      exponentialBackoffMultiplier: 1.5

    notificationService:
      baseConfig: default
      maxAttempts: 4
      waitDuration: 2000ms

    externalApi:
      maxAttempts: 3
      waitDuration: 1000ms
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2
      enableRandomizedWait: true
      randomizedWaitFactor: 0.3
      retryOnResultPredicate: jp.medicalbox.config.RetryOnServerError
```

**2. Service với Retry:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final RestClient paymentClient;

  @Retry(
    name = "paymentService",
    fallbackMethod = "processPaymentFallback"
  )
  @CircuitBreaker(name = "paymentService")
  public PaymentResponse processPayment(PaymentRequest request) {
    log.info("Calling payment service (attempt)");

    return paymentClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }

  private PaymentResponse processPaymentFallback(
    PaymentRequest request,
    Throwable throwable
  ) {
    log.error(
      "All payment retries exhausted: {}",
      throwable.getMessage()
    );

    return new PaymentResponse(
      null,
      "FAILED",
      "Payment processing failed after retries"
    );
  }
}
```

**3. Custom Retry Predicate:**

```java
package jp.medicalbox.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.function.Predicate;

public class RetryOnServerError implements Predicate<Object> {

  @Override
  public boolean test(Object response) {
    // Retry on 5xx server errors
    if (response instanceof HttpStatusCodeException ex) {
      HttpStatus status = (HttpStatus) ex.getStatusCode();
      return status.is5xxServerError();
    }
    return false;
  }
}
```

**4. Retry với Custom Exception Handling:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiService {

  private final RetryRegistry retryRegistry;
  private final RestClient externalClient;

  public ApiResponse callExternalApi(ApiRequest request) {
    Retry retry = retryRegistry.retry("externalApi");

    // Programmatic retry with custom logic
    Supplier<ApiResponse> supplier = Retry.decorateSupplier(
      retry,
      () -> {
        log.info("Calling external API");
        return externalClient.post()
          .uri("/api/data")
          .body(request)
          .retrieve()
          .body(ApiResponse.class);
      }
    );

    try {
      return supplier.get();
    } catch (Exception e) {
      log.error("External API call failed after retries", e);
      throw new ExternalApiException("API unavailable", e);
    }
  }
}
```

**5. Monitoring Retry Events:**

```java
package jp.medicalbox.monitoring;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RetryMonitoringConfig {

  public RetryMonitoringConfig(RetryRegistry registry) {
    registry.getAllRetries().forEach(retry -> {
      retry.getEventPublisher()
        .onEvent(event -> logRetryEvent(retry, event));
    });
  }

  private void logRetryEvent(Retry retry, RetryEvent event) {
    switch (event.getEventType()) {
      case RETRY -> log.warn(
        "Retry '{}' attempt {}/{}. Wait: {}ms. Error: {}",
        retry.getName(),
        event.getNumberOfRetryAttempts(),
        retry.getRetryConfig().getMaxAttempts(),
        retry.getRetryConfig().getIntervalFunction()
          .apply(event.getNumberOfRetryAttempts()),
        event.getLastThrowable().getMessage()
      );
      case SUCCESS -> log.info(
        "Retry '{}' succeeded after {} attempts",
        retry.getName(),
        event.getNumberOfRetryAttempts()
      );
      case ERROR -> log.error(
        "Retry '{}' exhausted after {} attempts",
        retry.getName(),
        event.getNumberOfRetryAttempts()
      );
    }
  }
}
```

**6. Conditional Retry (idempotent operations only):**

```java
package jp.medicalbox.service;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private final RestClient inventoryClient;

  // ✅ ĐÚNG: Idempotent operation (GET)
  @Retry(name = "inventoryService")
  public InventoryStatus checkInventory(String productId) {
    return inventoryClient.get()
      .uri("/api/inventory/{id}", productId)
      .retrieve()
      .body(InventoryStatus.class);
  }

  // ⚠️ CẢNH BÁO: Non-idempotent operation
  // Cần idempotency key hoặc server-side deduplication
  @Retry(name = "orderService")
  public OrderResponse createOrder(OrderRequest request) {
    // Include idempotency key in request
    String idempotencyKey = generateIdempotencyKey(request);

    return inventoryClient.post()
      .uri("/api/orders")
      .header("Idempotency-Key", idempotencyKey)
      .body(request)
      .retrieve()
      .body(OrderResponse.class);
  }

  private String generateIdempotencyKey(OrderRequest request) {
    return request.getUserId() + "-" +
           request.getTimestamp() + "-" +
           request.hashCode();
  }
}
```

### ❌ Cách sai

```yaml
# ❌ SAI: Retry mọi exception
resilience4j.retry:
  instances:
    paymentService:
      maxAttempts: 3
      # Không chỉ định retryExceptions
      # => Retry cả business exceptions (400 Bad Request)

# ❌ SAI: Fixed delay, không có exponential backoff
resilience4j.retry:
  instances:
    payment:
      maxAttempts: 10
      waitDuration: 1000ms
      enableExponentialBackoff: false
      # => 10 retries với 1s delay = waste 10s
      # và overwhelm downstream khi nó recovery

# ❌ SAI: Không có jitter
resilience4j.retry:
  instances:
    payment:
      enableRandomizedWait: false
      # => Tất cả clients retry đồng thời
      # => Thundering herd problem

# ❌ SAI: maxAttempts quá cao
resilience4j.retry:
  instances:
    payment:
      maxAttempts: 100
      # Quá nhiều, làm tăng latency vô ích
```

```java
// ❌ SAI: Retry non-idempotent operation không có idempotency key
@Retry(name = "payment")
public void chargeCard(PaymentRequest request) {
  // POST operation, không idempotent
  // Retry có thể charge nhiều lần!
  paymentClient.post().uri("/api/charge").body(request)...;
}

// ❌ SAI: Retry với Circuit Breaker nhưng không có fallback
@Retry(name = "payment")
@CircuitBreaker(name = "payment")
public PaymentResponse process(PaymentRequest req) {
  // Không có fallbackMethod
  // => User thấy error sau khi retry hết
  return callApi(req);
}

// ❌ SAI: Retry tất cả exceptions
@Retry(name = "payment")
public void process(PaymentRequest req) {
  if (req.getAmount() < 0) {
    throw new IllegalArgumentException("Invalid amount");
  }
  // Retry IllegalArgumentException vô nghĩa
  callApi(req);
}
```

### Phát hiện

```bash
# 1. Kiểm tra retry config thiếu exponential backoff
rg "resilience4j.retry" config/ -A 10 | \
  rg -v "enableExponentialBackoff: true"

# 2. Tìm retry không có jitter
yq '.resilience4j.retry.instances.* |
  select(.enableRandomizedWait == false or .enableRandomizedWait == null)' \
  application.yml

# 3. Kiểm tra maxAttempts quá cao
yq '.resilience4j.retry.instances.*.maxAttempts' application.yml | \
  awk '$1 > 5 {print "WARNING: maxAttempts too high:", $1}'

# 4. Tìm @Retry trên non-idempotent methods
rg "@Retry" --type java -B 5 | \
  rg "\.post\(|\.put\(|\.delete\(" | \
  rg -v "Idempotency-Key"
```

### Checklist

- [ ] `enableExponentialBackoff: true` cho tất cả retry instances
- [ ] `enableRandomizedWait: true` (jitter)
- [ ] `maxAttempts` hợp lý (3-5 cho hầu hết cases)
- [ ] `retryExceptions` chỉ gồm transient errors
- [ ] `ignoreExceptions` gồm business/validation exceptions
- [ ] Non-idempotent operations có idempotency key
- [ ] Kết hợp với Circuit Breaker và Timeout
- [ ] Monitor retry metrics (attempt count, success rate)
- [ ] Có fallback khi retries exhausted

---

## 16.07 Timeout configuration cho mọi remote call | 🔴 BẮT BUỘC

### Metadata
- **ID:** `SB-CLOUD-007`
- **Mức độ:** 🔴 BẮT BUỘC
- **Phạm vi:** All external communications
- **Công cụ:** RestClient, WebClient, Feign
- **Liên quan:** 16.01 (Circuit Breaker), 16.06 (Retry)

### Tại sao?

**Vấn đề:**
- Remote call không timeout => thread bị block mãi mãi
- Slow dependency làm cascade timeout toàn hệ thống
- Không control được max latency cho operations
- Thread pool exhaustion khi nhiều calls bị hang

**Lợi ích:**
- ✅ Prevent thread starvation
- ✅ Fail fast khi dependency slow
- ✅ Predictable latency SLA
- ✅ Better resource utilization

**Hệ quả nếu vi phạm:**
- ⚠️ **P0**: Thread pool exhaustion, toàn bộ app hang
- ⚠️ **P0**: Cascading timeouts từ downstream
- ⚠️ **P1**: Unpredictable response times

### ✅ Cách đúng

**1. RestClient với Timeout:**

```java
package jp.medicalbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestClientConfig {

  @Bean
  public RestClient paymentClient() {
    HttpClient httpClient = HttpClient.create()
      // Connection timeout
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
      // Response timeout (toàn bộ request-response cycle)
      .responseTimeout(Duration.ofSeconds(10))
      // Read/Write timeouts
      .doOnConnected(conn -> conn
        .addHandlerLast(
          new ReadTimeoutHandler(10, TimeUnit.SECONDS)
        )
        .addHandlerLast(
          new WriteTimeoutHandler(5, TimeUnit.SECONDS)
        )
      );

    return RestClient.builder()
      .baseUrl("https://payment-api.example.com")
      .requestFactory(new ReactorClientHttpConnector(httpClient))
      .build();
  }

  @Bean
  public RestClient notificationClient() {
    HttpClient httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
      .responseTimeout(Duration.ofSeconds(5))
      .doOnConnected(conn -> conn
        .addHandlerLast(
          new ReadTimeoutHandler(5, TimeUnit.SECONDS)
        )
        .addHandlerLast(
          new WriteTimeoutHandler(3, TimeUnit.SECONDS)
        )
      );

    return RestClient.builder()
      .baseUrl("https://notification-api.example.com")
      .requestFactory(new ReactorClientHttpConnector(httpClient))
      .build();
  }
}
```

**2. WebClient với Timeout:**

```java
package jp.medicalbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient externalApiClient() {
    HttpClient httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
      .responseTimeout(Duration.ofSeconds(15))
      .doOnConnected(conn ->
        conn.addHandlerLast(
          new ReadTimeoutHandler(15, TimeUnit.SECONDS)
        )
      );

    return WebClient.builder()
      .baseUrl("https://external-api.example.com")
      .clientConnector(new ReactorClientHttpConnector(httpClient))
      .build();
  }
}
```

**3. Service-Specific Timeout Configuration:**

```yaml
# application.yml
app:
  clients:
    payment:
      connect-timeout: 5000
      read-timeout: 10000
      write-timeout: 5000
    notification:
      connect-timeout: 3000
      read-timeout: 5000
      write-timeout: 3000
    reporting:
      connect-timeout: 5000
      read-timeout: 30000  # Reporting API cần timeout dài hơn
      write-timeout: 5000
```

```java
package jp.medicalbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class DynamicRestClientConfig {

  @Bean
  @ConfigurationProperties(prefix = "app.clients.payment")
  public ClientConfig paymentClientConfig() {
    return new ClientConfig();
  }

  @Bean
  public RestClient paymentClient(ClientConfig paymentClientConfig) {
    return createRestClient(
      "https://payment-api.example.com",
      paymentClientConfig
    );
  }

  private RestClient createRestClient(
    String baseUrl,
    ClientConfig config
  ) {
    HttpClient httpClient = HttpClient.create()
      .option(
        ChannelOption.CONNECT_TIMEOUT_MILLIS,
        config.getConnectTimeout()
      )
      .responseTimeout(
        Duration.ofMillis(config.getReadTimeout())
      )
      .doOnConnected(conn -> conn
        .addHandlerLast(new ReadTimeoutHandler(
          config.getReadTimeout(),
          TimeUnit.MILLISECONDS
        ))
        .addHandlerLast(new WriteTimeoutHandler(
          config.getWriteTimeout(),
          TimeUnit.MILLISECONDS
        ))
      );

    return RestClient.builder()
      .baseUrl(baseUrl)
      .requestFactory(new ReactorClientHttpConnector(httpClient))
      .build();
  }

  @Data
  public static class ClientConfig {
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
    private int writeTimeout = 5000;
  }
}
```

**4. Database Connection Timeout:**

```yaml
# application.yml
spring:
  datasource:
    hikari:
      connection-timeout: 5000  # 5s để lấy connection từ pool
      validation-timeout: 3000  # 3s để validate connection
      idle-timeout: 600000      # 10 phút idle trước khi close
      max-lifetime: 1800000     # 30 phút max lifetime

  jpa:
    properties:
      hibernate:
        query.timeout: 10000  # 10s query timeout
```

**5. Redis Timeout:**

```yaml
# application.yml
spring:
  data:
    redis:
      host: redis.example.com
      port: 6379
      timeout: 2000ms       # 2s command timeout
      connect-timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
        shutdown-timeout: 200ms
```

**6. Timeout với @Async Operations:**

```java
package jp.medicalbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

  @Async("reportingExecutor")
  public CompletableFuture<ReportData> generateReport(
    String reportId
  ) {
    return CompletableFuture.supplyAsync(() -> {
      log.info("Generating report: {}", reportId);
      return performHeavyCalculation(reportId);
    }).orTimeout(30, TimeUnit.SECONDS)  // Timeout sau 30s
      .exceptionally(throwable -> {
        if (throwable instanceof TimeoutException) {
          log.error(
            "Report generation timed out: {}",
            reportId
          );
        }
        return null;
      });
  }

  private ReportData performHeavyCalculation(String reportId) {
    // Heavy computation
    return new ReportData(reportId, "data");
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không configure timeout
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient paymentClient() {
    return RestClient.builder()
      .baseUrl("https://payment-api.example.com")
      .build();
    // Không có timeout => có thể block mãi mãi
  }
}

// ❌ SAI: Timeout quá dài
HttpClient httpClient = HttpClient.create()
  .responseTimeout(Duration.ofMinutes(10));  // 10 phút!
// User không thể chờ lâu vậy

// ❌ SAI: Chỉ có connect timeout, không có read timeout
HttpClient httpClient = HttpClient.create()
  .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
  // Thiếu responseTimeout
  // => Kết nối được nhưng response chậm vẫn block

// ❌ SAI: Database query không có timeout
spring:
  jpa:
    properties:
      # Thiếu hibernate.query.timeout
      # => Slow query block connection pool

// ❌ SAI: Timeout không nhất quán
# Service A timeout 5s, Service B timeout 60s gọi Service A
# => Service B vẫn chờ 60s dù Service A đã timeout
```

```java
// ❌ SAI: Catch timeout nhưng không xử lý
@Service
public class PaymentService {

  public PaymentResponse process(PaymentRequest req) {
    try {
      return paymentClient.post()...;
    } catch (TimeoutException e) {
      // Catch nhưng không làm gì
      // Nên: log, fallback, hoặc throw custom exception
    }
    return null;
  }
}
```

### Phát hiện

```bash
# 1. Tìm RestClient/WebClient bean không có timeout config
rg "@Bean.*RestClient|@Bean.*WebClient" --type java -A 15 | \
  rg -v "CONNECT_TIMEOUT|responseTimeout|ReadTimeoutHandler"

# 2. Kiểm tra database connection config thiếu timeout
rg "spring.datasource" config/ -A 10 | \
  rg -v "connection-timeout|validation-timeout"

# 3. Kiểm tra Redis config thiếu timeout
rg "spring.data.redis" config/ -A 5 | \
  rg -v "timeout|connect-timeout"

# 4. Tìm @Async operations không có timeout
rg "@Async" --type java -A 10 | \
  rg "CompletableFuture" | \
  rg -v "orTimeout|completeOnTimeout"
```

### Checklist

- [ ] Mọi RestClient/WebClient có connect, read, write timeout
- [ ] Timeout values hợp lý (connect: 3-5s, read: 5-30s)
- [ ] Database connection pool có `connection-timeout`
- [ ] Hibernate có `query.timeout`
- [ ] Redis có `timeout` và `connect-timeout`
- [ ] @Async operations có `.orTimeout()` cho long-running tasks
- [ ] Timeout cascade: parent timeout > child timeout
- [ ] Monitor timeout metrics (frequency, which endpoints)
- [ ] Test timeout behavior (mock slow responses)

---

## 16.08 Fallback method cho degraded service | 🟠 KHUYẾN NGHỊ

### Metadata
- **ID:** `SB-CLOUD-008`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Phạm vi:** Resilience patterns
- **Công cụ:** Resilience4j fallback methods
- **Liên quan:** 16.01 (Circuit Breaker), 16.06 (Retry)

### Tại sao?

**Vấn đề:**
- Circuit open hoặc retries exhausted => user thấy 5xx error
- Toàn bộ functionality bị disable khi một dependency lỗi
- Không có graceful degradation
- Bad user experience khi service unavailable

**Lợi ích:**
- ✅ Graceful degradation khi dependency lỗi
- ✅ Better UX (cached data, default values thay vì errors)
- ✅ Partial functionality thay vì complete failure
- ✅ Tăng availability của hệ thống

**Hệ quả nếu vi phạm:**
- ⚠️ **P2**: User thấy errors thay vì degraded experience
- ⚠️ **P2**: Complete feature outage khi dependency down
- ⚠️ **P3**: Không tận dụng caching/defaults

### ✅ Cách đúng

**1. Fallback với Cached Data:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

  private final RestClient productClient;
  private final ProductCacheService cacheService;

  @CircuitBreaker(
    name = "productService",
    fallbackMethod = "getProductsFallback"
  )
  @Cacheable(value = "products", key = "#categoryId")
  public List<Product> getProducts(String categoryId) {
    log.info("Fetching products from API: {}", categoryId);

    return productClient.get()
      .uri("/api/products?category={id}", categoryId)
      .retrieve()
      .body(new ParameterizedTypeReference<List<Product>>() {});
  }

  private List<Product> getProductsFallback(
    String categoryId,
    Throwable throwable
  ) {
    log.warn(
      "Product service unavailable, using cached data: {}",
      throwable.getMessage()
    );

    // Return cached data (stale nhưng vẫn hơn error)
    List<Product> cachedProducts =
      cacheService.getCachedProducts(categoryId);

    if (cachedProducts != null && !cachedProducts.isEmpty()) {
      log.info("Returning {} cached products", cachedProducts.size());
      return cachedProducts;
    }

    // Nếu không có cache, return empty list với warning message
    log.warn("No cached products available");
    return List.of();
  }
}
```

**2. Fallback với Default Values:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

  private final RestClient configClient;

  @CircuitBreaker(
    name = "configService",
    fallbackMethod = "getFeatureConfigFallback"
  )
  public FeatureConfig getFeatureConfig() {
    return configClient.get()
      .uri("/api/config/features")
      .retrieve()
      .body(FeatureConfig.class);
  }

  private FeatureConfig getFeatureConfigFallback(Throwable throwable) {
    log.warn(
      "Config service unavailable, using defaults: {}",
      throwable.getMessage()
    );

    // Return safe default values
    return FeatureConfig.builder()
      .emailVerificationEnabled(true)
      .smsNotificationEnabled(false)  // Conservative default
      .maxUploadSizeMb(10)
      .sessionTimeoutMinutes(30)
      .build();
  }
}
```

**3. Fallback với Queue/Async Processing:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final RestClient notificationClient;
  private final NotificationQueueService queueService;

  @CircuitBreaker(
    name = "notificationService",
    fallbackMethod = "sendNotificationFallback"
  )
  public NotificationResponse sendNotification(
    NotificationRequest request
  ) {
    log.info("Sending notification: {}", request.type());

    return notificationClient.post()
      .uri("/api/notifications")
      .body(request)
      .retrieve()
      .body(NotificationResponse.class);
  }

  private NotificationResponse sendNotificationFallback(
    NotificationRequest request,
    Throwable throwable
  ) {
    log.warn(
      "Notification service unavailable, queuing for later: {}",
      throwable.getMessage()
    );

    // Queue notification for later delivery
    queueService.enqueue(request);

    return new NotificationResponse(
      "QUEUED",
      "Notification queued for delivery when service recovers"
    );
  }
}
```

**4. Fallback với Alternative Service:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final RestClient primaryPaymentClient;
  private final RestClient backupPaymentClient;

  @CircuitBreaker(
    name = "primaryPayment",
    fallbackMethod = "processPaymentWithBackup"
  )
  public PaymentResponse processPayment(PaymentRequest request) {
    log.info("Processing payment with primary provider");

    return primaryPaymentClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }

  @CircuitBreaker(
    name = "backupPayment",
    fallbackMethod = "processPaymentFinalFallback"
  )
  private PaymentResponse processPaymentWithBackup(
    PaymentRequest request,
    Throwable throwable
  ) {
    log.warn(
      "Primary payment provider unavailable, using backup: {}",
      throwable.getMessage()
    );

    return backupPaymentClient.post()
      .uri("/api/payments")
      .body(request)
      .retrieve()
      .body(PaymentResponse.class);
  }

  private PaymentResponse processPaymentFinalFallback(
    PaymentRequest request,
    Throwable throwable
  ) {
    log.error(
      "All payment providers unavailable: {}",
      throwable.getMessage()
    );

    // Queue for manual processing
    return new PaymentResponse(
      null,
      "PENDING_MANUAL",
      "Payment queued for manual processing"
    );
  }
}
```

**5. Fallback với Partial Response:**

```java
package jp.medicalbox.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final UserService userService;
  private final OrderService orderService;
  private final AnalyticsService analyticsService;

  public DashboardData getDashboard(String userId) {
    // Fetch multiple data sources
    UserProfile user = fetchUserSafely(userId);
    List<Order> orders = fetchOrdersSafely(userId);
    AnalyticsData analytics = fetchAnalyticsSafely(userId);

    return DashboardData.builder()
      .userProfile(user)
      .recentOrders(orders)
      .analytics(analytics)
      .build();
  }

  @CircuitBreaker(
    name = "userService",
    fallbackMethod = "fetchUserFallback"
  )
  private UserProfile fetchUserSafely(String userId) {
    return userService.getProfile(userId);
  }

  private UserProfile fetchUserFallback(
    String userId,
    Throwable throwable
  ) {
    log.warn("User service unavailable, using minimal profile");
    return UserProfile.minimal(userId);
  }

  @CircuitBreaker(
    name = "orderService",
    fallbackMethod = "fetchOrdersFallback"
  )
  private List<Order> fetchOrdersSafely(String userId) {
    return orderService.getRecentOrders(userId);
  }

  private List<Order> fetchOrdersFallback(
    String userId,
    Throwable throwable
  ) {
    log.warn("Order service unavailable, hiding orders section");
    return List.of();  // Empty list, UI will hide section
  }

  @CircuitBreaker(
    name = "analyticsService",
    fallbackMethod = "fetchAnalyticsFallback"
  )
  private AnalyticsData fetchAnalyticsSafely(String userId) {
    return analyticsService.getAnalytics(userId);
  }

  private AnalyticsData fetchAnalyticsFallback(
    String userId,
    Throwable throwable
  ) {
    log.warn("Analytics service unavailable, using defaults");
    return AnalyticsData.defaultValues();
  }
}
```

**6. Cache Service Implementation:**

```java
package jp.medicalbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCacheService {

  private final RedisTemplate<String, List<Product>> redisTemplate;

  public void cacheProducts(
    String categoryId,
    List<Product> products
  ) {
    String key = "products:category:" + categoryId;
    redisTemplate.opsForValue().set(
      key,
      products,
      Duration.ofHours(24)  // Cache 24h
    );
  }

  public List<Product> getCachedProducts(String categoryId) {
    String key = "products:category:" + categoryId;
    return redisTemplate.opsForValue().get(key);
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không có fallback
@CircuitBreaker(name = "payment")
public PaymentResponse process(PaymentRequest req) {
  // Không có fallbackMethod
  // => User thấy 5xx error khi circuit open
  return callApi(req);
}

// ❌ SAI: Fallback throw exception
@CircuitBreaker(name = "payment", fallbackMethod = "fallback")
public PaymentResponse process(PaymentRequest req) {
  return callApi(req);
}

private PaymentResponse fallback(
  PaymentRequest req,
  Throwable t
) {
  // Fallback không nên throw exception
  throw new RuntimeException("Payment failed");
}

// ❌ SAI: Fallback gọi chính service bị lỗi
@CircuitBreaker(name = "payment", fallbackMethod = "fallback")
public PaymentResponse process(PaymentRequest req) {
  return primaryApi.call(req);
}

private PaymentResponse fallback(
  PaymentRequest req,
  Throwable t
) {
  // Gọi lại chính service bị lỗi => vô hạn loop
  return process(req);
}

// ❌ SAI: Fallback phức tạp, có thể fail
private PaymentResponse fallback(
  PaymentRequest req,
  Throwable t
) {
  // Fallback không nên có complex logic
  // Nên đơn giản, safe
  ComplexObject obj = heavyComputation();
  ExternalService.call(obj);  // Có thể fail!
  return response;
}

// ❌ SAI: Fallback không log
private List<Product> fallback(String id, Throwable t) {
  // Không log => không biết khi nào dùng fallback
  return List.of();
}
```

### Phát hiện

```bash
# 1. Tìm @CircuitBreaker không có fallbackMethod
rg "@CircuitBreaker" --type java | \
  rg -v "fallbackMethod"

# 2. Tìm fallback methods throw exception
rg "private.*Fallback.*\(" --type java -A 10 | \
  rg "throw new"

# 3. Kiểm tra fallback không log
rg "private.*Fallback.*\(" --type java -A 10 | \
  rg -v "log\.(warn|error|info)"

# 4. Tìm fallback gọi external services
rg "private.*Fallback.*\(" --type java -A 20 | \
  rg "RestClient|WebClient|\.post\(|\.get\("
```

### Checklist

- [ ] Mọi `@CircuitBreaker` có `fallbackMethod`
- [ ] Fallback method có đúng signature (+ `Throwable`)
- [ ] Fallback KHÔNG throw exceptions
- [ ] Fallback đơn giản, không có external dependencies
- [ ] Fallback log warning với error details
- [ ] Fallback return cached/default/partial data (không return null)
- [ ] Test fallback behavior (manually open circuit)
- [ ] Monitor fallback invocation rate
- [ ] Document fallback behavior cho users (degraded mode)

---

## Tổng kết Domain 16

### Checklist tổng hợp

**Circuit Breaker & Resilience:**
- [ ] Mọi external API call có `@CircuitBreaker`
- [ ] Configure `slowCallDurationThreshold` và timeout
- [ ] Monitor circuit breaker state transitions
- [ ] Test circuit behavior khi service lỗi

**Service Discovery:**
- [ ] Sử dụng `lb://` URIs với LoadBalancer
- [ ] Configure health checks (liveness + readiness)
- [ ] Test failover khi kill instance

**API Gateway:**
- [ ] Single entry point với Spring Cloud Gateway
- [ ] Centralized authentication filter
- [ ] Circuit breaker cho mọi routes
- [ ] Request/response logging

**Config Server:**
- [ ] Centralized config trong Git repo
- [ ] `@RefreshScope` cho dynamic config
- [ ] Encrypt sensitive values với `{cipher}`

**Bulkhead:**
- [ ] Isolate thread pools cho dependencies
- [ ] `maxConcurrentCalls` hợp lý (5-20)
- [ ] CPU-intensive tasks có dedicated pools

**Retry:**
- [ ] `enableExponentialBackoff: true`
- [ ] `enableRandomizedWait: true` (jitter)
- [ ] Chỉ retry transient errors
- [ ] Non-idempotent operations có idempotency key

**Timeout:**
- [ ] Mọi RestClient/WebClient có timeout
- [ ] Database connection pool timeout
- [ ] Timeout cascade: parent > child

**Fallback:**
- [ ] Mọi resilience pattern có fallback
- [ ] Fallback đơn giản, safe, không throw exceptions
- [ ] Return cached/default data thay vì errors

### Metrics cần monitor

```yaml
# Resilience4j metrics
- resilience4j.circuitbreaker.state
- resilience4j.circuitbreaker.failure.rate
- resilience4j.bulkhead.available.concurrent.calls
- resilience4j.retry.calls
- http.client.requests (duration, status)
```

---

**Lưu ý quan trọng:**
1. **Luôn kết hợp** Circuit Breaker + Timeout + Retry + Fallback
2. **Test resilience** bằng chaos engineering (kill services, inject latency)
3. **Monitor metrics** để tune thresholds
4. **Document degraded behavior** cho users
5. **Critical operations** prioritize hơn non-critical (smaller pools, faster timeout)
