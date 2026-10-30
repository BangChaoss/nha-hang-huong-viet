# Domain 15: Deployment & DevOps
> **Số practices:** 9 | 🔴 3 | 🟠 5 | 🟡 1
> **Trọng số:** ×1

---

## 15.01 - Dockerfile multi-stage build (JRE only, không JDK)

### 📋 Metadata
- **ID:** `BP-15.01`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Scope:** Dockerfile, Container image
- **Lý do:** Giảm 50-70% kích thước image, tăng security (loại bỏ build tools)

### 🎯 Tại sao?

**Vấn đề:**
- JDK (~400MB) chứa compiler, debugger không cần thiết ở runtime
- Image lớn → deploy chậm, tốn bandwidth, tăng attack surface
- Mixing build artifacts với runtime environment

**Lợi ích:**
- ✅ Image nhỏ hơn 50-70% (OpenJDK JRE ~200MB vs JDK ~400MB)
- ✅ Security: loại bỏ javac, jdb, jar tools khỏi production
- ✅ Build cache tách biệt dependencies vs source code
- ✅ Faster deployment, lower bandwidth costs

**Khi nào bỏ qua:**
- Development image cần debugging tools
- Môi trường yêu cầu runtime compilation (hiếm gặp)

### ✅ Cách đúng

```dockerfile
# ========== Stage 1: Build ==========
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy dependency files first (cache layer)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build application (skip tests - run in CI)
RUN ./mvnw clean package -DskipTests

# ========== Stage 2: Runtime ==========
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy only JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

**Gradle variant:**

```dockerfile
# ========== Stage 1: Build ==========
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# ========== Stage 2: Runtime ==========
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

**Optimized với layer caching:**

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Layer 1: Dependencies (thay đổi ít nhất)
COPY pom.xml .
RUN ./mvnw dependency:go-offline || true

# Layer 2: Source code (thay đổi thường xuyên)
COPY src ./src

# Layer 3: Build
RUN ./mvnw clean package -DskipTests

# Extract layers cho Spring Boot
RUN java -Djarmode=layertools -jar target/*.jar extract

# ========== Runtime ==========
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy layers in order (dependencies first)
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### ❌ Cách sai

```dockerfile
# ❌ SAI: Dùng JDK ở runtime
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/*.jar app.jar

# Vấn đề:
# - Image size: ~600MB (vs ~250MB với JRE)
# - Chứa javac, jdb → security risk
# - Không tận dụng Docker layer caching

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```dockerfile
# ❌ SAI: Build trong CI, copy JAR vào base image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Vấn đề: Mất reproducibility
# - Build environment khác Docker environment
# - Khó debug "works on my machine"
COPY app.jar .

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```dockerfile
# ❌ SAI: Không tách layer dependencies
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy tất cả cùng lúc → cache invalidated khi code thay đổi
COPY . .

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

COPY --from=builder /app/target/*.jar app.jar

# Vấn đề: Mỗi lần code change → rebuild dependencies
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Tìm Dockerfile dùng JDK ở runtime
grep -E "^FROM.*jdk.*(?!AS builder)" Dockerfile
```

**Checklist:**

```bash
# Check multi-stage build
grep -c "^FROM" Dockerfile  # Phải ≥2

# Check runtime image dùng JRE
grep "^FROM.*jre" Dockerfile | grep -v "AS builder"

# Check layer optimization
grep "COPY.*pom.xml\|build.gradle" Dockerfile
```

### ✓ Checklist tự kiểm tra

- [ ] Dockerfile có ít nhất 2 stage (builder + runtime)
- [ ] Runtime stage dùng JRE (không phải JDK)
- [ ] Dependencies được copy riêng trước source code
- [ ] `COPY --from=builder` chỉ copy JAR/layers cần thiết
- [ ] Non-root user được tạo và sử dụng
- [ ] `.dockerignore` loại bỏ `target/`, `build/`, `.git/`
- [ ] Build cache được tối ưu (dependencies layer riêng)
- [ ] Image size < 300MB (kiểm tra với `docker images`)

---

## 15.02 - Health check endpoint (/actuator/health)

### 📋 Metadata
- **ID:** `BP-15.02`
- **Mức độ:** 🔴 BẮT BUỘC
- **Scope:** Spring Boot Actuator, K8s probes
- **Lý do:** Load balancer/orchestrator cần biết service healthy hay không

### 🎯 Tại sao?

**Vấn đề:**
- Orchestrator (K8s, ECS) không biết container "healthy" hay chỉ "running"
- Process còn chạy nhưng DB connection fail → service không dùng được
- Load balancer gửi traffic đến instance đang khởi động

**Lợi ích:**
- ✅ K8s tự động restart unhealthy pods
- ✅ Load balancer loại bỏ instance lỗi khỏi pool
- ✅ Monitoring alert khi service degraded
- ✅ Zero-downtime deployment với readiness check

**Khi nào bỏ qua:**
- Không bao giờ (bắt buộc cho production)

### ✅ Cách đúng

**1. Cấu hình Actuator:**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
      base-path: /actuator
  endpoint:
    health:
      enabled: true
      show-details: when-authorized  # never | when-authorized | always
      probes:
        enabled: true  # Enable /actuator/health/liveness, /readiness
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
    # Custom health indicators
    db:
      enabled: true
    redis:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10GB

# Security cho actuator endpoints
spring:
  security:
    user:
      name: actuator
      password: ${ACTUATOR_PASSWORD}
```

**2. Custom health indicator:**

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

  private final DataSource dataSource;

  public DatabaseHealthIndicator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Health health() {
    try (var conn = dataSource.getConnection()) {
      var stmt = conn.createStatement();
      stmt.execute("SELECT 1");
      return Health.up()
        .withDetail("database", "PostgreSQL")
        .withDetail("validationQuery", "SELECT 1")
        .build();
    } catch (SQLException e) {
      return Health.down()
        .withDetail("error", e.getMessage())
        .build();
    }
  }
}
```

**3. External service health:**

```java
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {

  private final WebClient webClient;
  private final String apiUrl;

  public ExternalApiHealthIndicator(
    WebClient.Builder webClientBuilder,
    @Value("${external.api.url}") String apiUrl
  ) {
    this.webClient = webClientBuilder.baseUrl(apiUrl).build();
    this.apiUrl = apiUrl;
  }

  @Override
  public Health health() {
    try {
      var response = webClient.get()
        .uri("/health")
        .retrieve()
        .toBodilessEntity()
        .block(Duration.ofSeconds(3));

      if (response != null && response.getStatusCode().is2xxSuccessful()) {
        return Health.up()
          .withDetail("externalApi", apiUrl)
          .build();
      }

      return Health.down()
        .withDetail("externalApi", apiUrl)
        .withDetail("status", response != null ? response.getStatusCode() : "null")
        .build();

    } catch (Exception e) {
      return Health.down()
        .withDetail("externalApi", apiUrl)
        .withDetail("error", e.getMessage())
        .build();
    }
  }
}
```

**4. Kubernetes deployment với probes:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0
        ports:
        - containerPort: 8080

        # Liveness probe: container còn sống không?
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60  # Chờ app khởi động
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3      # Restart sau 3 lần fail

        # Readiness probe: sẵn sàng nhận traffic?
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2      # Loại khỏi service sau 2 lần fail

        # Startup probe: chờ app khởi động chậm
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 0
          periodSeconds: 5
          failureThreshold: 30     # 30 * 5s = 150s max startup time
```

**5. Response examples:**

```json
// GET /actuator/health (public)
{
  "status": "UP"
}

// GET /actuator/health (authenticated)
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "SELECT 1"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 100000000000,
        "threshold": 10737418240
      }
    },
    "externalApi": {
      "status": "DOWN",
      "details": {
        "externalApi": "https://api.example.com",
        "error": "Connection timeout"
      }
    }
  }
}
```

### ❌ Cách sai

```yaml
# ❌ SAI: Không enable health endpoint
management:
  endpoints:
    web:
      exposure:
        include: info,metrics
  # Thiếu health → K8s không check được

# Vấn đề: K8s chỉ biết process running, không biết healthy
```

```yaml
# ❌ SAI: Liveness = Readiness (cùng endpoint)
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080

readinessProbe:
  httpGet:
    path: /actuator/health  # Trùng với liveness
    port: 8080

# Vấn đề:
# - DB down → liveness fail → restart pod (sai)
# - Nên chỉ loại khỏi load balancer, không restart
```

```java
// ❌ SAI: Health check gọi external API quá lâu
@Component
public class SlowHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    try {
      // Timeout 30s → block health endpoint
      var response = webClient.get()
        .retrieve()
        .toBodilessEntity()
        .block(Duration.ofSeconds(30));  // ❌ Quá lâu

      return Health.up().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}

// Vấn đề: K8s timeout probe → restart pod liên tục
```

```yaml
# ❌ SAI: Show sensitive details publicly
management:
  endpoint:
    health:
      show-details: always  # ❌ Lộ DB credentials, internal URLs

# Vấn đề: Security risk
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check health endpoint enabled
grep -r "management.endpoint.health.enabled" src/main/resources/

# Check probes enabled
grep -r "management.health.livenessstate.enabled" src/main/resources/

# Check K8s probes
grep -A 5 "livenessProbe\|readinessProbe" k8s/*.yml
```

**Runtime check:**

```bash
# Test health endpoint
curl http://localhost:8080/actuator/health

# Test liveness
curl http://localhost:8080/actuator/health/liveness

# Test readiness
curl http://localhost:8080/actuator/health/readiness
```

### ✓ Checklist tự kiểm tra

- [ ] `spring-boot-starter-actuator` dependency added
- [ ] `/actuator/health` endpoint enabled và accessible
- [ ] `show-details` set to `when-authorized` (không `always`)
- [ ] Liveness và Readiness probes tách biệt
- [ ] Custom health indicators cho critical dependencies (DB, cache, external API)
- [ ] Health check timeout < 5s
- [ ] K8s deployment có `livenessProbe` và `readinessProbe`
- [ ] `initialDelaySeconds` đủ lớn cho app startup
- [ ] Actuator endpoints protected (authentication/IP whitelist)

---

## 15.03 - Graceful shutdown (server.shutdown=graceful)

### 📋 Metadata
- **ID:** `BP-15.03`
- **Mức độ:** 🔴 BẮT BUỘC
- **Scope:** Server shutdown, Rolling deployment
- **Lý do:** Tránh mất request đang xử lý khi deploy/restart

### 🎯 Tại sao?

**Vấn đề:**
- Immediate shutdown → kill đột ngột requests đang xử lý
- Load balancer còn route traffic đến pod đang shutdown
- Database transactions bị rollback giữa chừng

**Lợi ích:**
- ✅ Zero downtime deployment
- ✅ Không mất requests đang xử lý
- ✅ Transactions được commit hoàn toàn
- ✅ Connections được đóng gracefully

**Khi nào bỏ qua:**
- Không bao giờ (bắt buộc cho production)

### ✅ Cách đúng

**1. Cấu hình graceful shutdown:**

```yaml
# application.yml
server:
  shutdown: graceful  # immediate | graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # Thời gian chờ tối đa
```

**2. Shutdown sequence:**

```
1. SIGTERM signal received
   ↓
2. Stop accepting new requests (readiness = false)
   ↓
3. Wait for in-flight requests to complete (max 30s)
   ↓
4. Close connections gracefully
   ↓
5. Destroy beans (@PreDestroy)
   ↓
6. Exit process
```

**3. Custom cleanup với @PreDestroy:**

```java
@Service
public class DataProcessingService {

  private final ExecutorService executor = Executors.newFixedThreadPool(10);
  private final List<CompletableFuture<Void>> activeTasks = new CopyOnWriteArrayList<>();

  @Scheduled(fixedDelay = 1000)
  public void processData() {
    var future = CompletableFuture.runAsync(() -> {
      // Long-running task
    }, executor);

    activeTasks.add(future);
    future.whenComplete((result, ex) -> activeTasks.remove(future));
  }

  @PreDestroy
  public void cleanup() {
    log.info("Shutting down DataProcessingService...");

    // Wait for active tasks
    CompletableFuture.allOf(activeTasks.toArray(new CompletableFuture[0]))
      .orTimeout(20, TimeUnit.SECONDS)
      .exceptionally(ex -> {
        log.warn("Some tasks did not complete in time", ex);
        return null;
      })
      .join();

    // Shutdown executor
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    log.info("DataProcessingService shutdown complete");
  }
}
```

**4. Database connection cleanup:**

```java
@Configuration
public class DataSourceConfig {

  @Bean
  public DataSource dataSource() {
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
    config.setUsername("user");
    config.setPassword("pass");

    // Graceful shutdown settings
    config.setConnectionTimeout(5000);
    config.setMaxLifetime(1800000);  // 30 minutes
    config.setLeakDetectionThreshold(60000);

    return new HikariDataSource(config);
  }

  @PreDestroy
  public void cleanup() {
    log.info("Closing database connections...");
    // HikariCP auto-closes gracefully
  }
}
```

**5. Kubernetes deployment với preStop hook:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        lifecycle:
          # Hook trước khi send SIGTERM
          preStop:
            exec:
              command:
              - sh
              - -c
              - |
                # Wait for load balancer to deregister
                sleep 10
                # Send graceful shutdown signal
                kill -TERM 1

        # Thời gian chờ graceful shutdown trước khi SIGKILL
        terminationGracePeriodSeconds: 60

      # Thời gian chờ trước khi terminate pod
      terminationGracePeriodSeconds: 60
```

**6. Monitor shutdown events:**

```java
@Component
public class ShutdownListener implements ApplicationListener<ContextClosedEvent> {

  private static final Logger log = LoggerFactory.getLogger(ShutdownListener.class);

  @Override
  public void onApplicationEvent(ContextClosedEvent event) {
    log.info("Application shutdown initiated");
    log.info("Context: {}", event.getApplicationContext().getDisplayName());
    log.info("Timestamp: {}", Instant.ofEpochMilli(event.getTimestamp()));

    // Custom metrics/alerts
    metricsService.recordShutdown();
  }
}
```

**7. Testing graceful shutdown:**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GracefulShutdownTest {

  @Autowired
  private ConfigurableApplicationContext context;

  @LocalServerPort
  private int port;

  @Test
  void shouldCompleteRequestsDuringShutdown() throws Exception {
    var client = HttpClient.newHttpClient();
    var startTime = System.currentTimeMillis();

    // Start long request
    var longRequest = CompletableFuture.supplyAsync(() -> {
      try {
        var request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + "/slow-endpoint"))  // Takes 5s
          .GET()
          .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Wait 1s, then trigger shutdown
    Thread.sleep(1000);
    context.close();

    // Request should complete successfully
    var response = longRequest.get(30, TimeUnit.SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);

    var duration = System.currentTimeMillis() - startTime;
    assertThat(duration).isGreaterThan(5000);  // Request completed
  }
}
```

### ❌ Cách sai

```yaml
# ❌ SAI: Immediate shutdown (default)
server:
  shutdown: immediate  # Hoặc không config (default)

# Vấn đề:
# - Requests đang xử lý bị kill
# - Transactions bị rollback
# - Connection pool không đóng properly
```

```yaml
# ❌ SAI: Timeout quá ngắn
spring:
  lifecycle:
    timeout-per-shutdown-phase: 5s  # Quá ngắn cho long requests

# Vấn đề: Force kill nếu request chạy > 5s
```

```java
// ❌ SAI: Không cleanup resources
@Service
public class BackgroundService {

  private final ExecutorService executor = Executors.newFixedThreadPool(10);

  @Scheduled(fixedDelay = 1000)
  public void processData() {
    executor.submit(() -> {
      // Long task
    });
  }

  // ❌ Thiếu @PreDestroy → threads leak
}
```

```yaml
# ❌ SAI: K8s không đủ thời gian graceful shutdown
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        terminationGracePeriodSeconds: 10  # ❌ Quá ngắn

# Vấn đề:
# - Spring timeout = 30s
# - K8s timeout = 10s
# - SIGKILL sau 10s → force kill
```

```java
// ❌ SAI: Block shutdown indefinitely
@Service
public class BadService {

  @PreDestroy
  public void cleanup() {
    while (true) {  // ❌ Infinite loop
      // Never completes
    }
  }
}

// Vấn đề: App không shutdown được
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check graceful shutdown config
grep -r "server.shutdown" src/main/resources/

# Check timeout config
grep -r "timeout-per-shutdown-phase" src/main/resources/

# Check @PreDestroy methods
grep -r "@PreDestroy" src/main/java/
```

**Runtime test:**

```bash
# Start app
./mvnw spring-boot:run &
APP_PID=$!

# Send request
curl http://localhost:8080/slow-endpoint &

# Send SIGTERM
kill -TERM $APP_PID

# Check logs for graceful shutdown
tail -f logs/application.log | grep -i shutdown
```

### ✓ Checklist tự kiểm tra

- [ ] `server.shutdown=graceful` trong application.yml
- [ ] `timeout-per-shutdown-phase` >= 30s
- [ ] Tất cả `ExecutorService` có `@PreDestroy` cleanup
- [ ] Database connections tự đóng (HikariCP auto-close)
- [ ] K8s `terminationGracePeriodSeconds` >= Spring timeout + 10s
- [ ] K8s `preStop` hook chờ load balancer deregister
- [ ] Không có infinite loop trong `@PreDestroy`
- [ ] Test shutdown với request đang xử lý
- [ ] Log shutdown events cho monitoring

---

## 15.04 - Readiness vs Liveness probes phân biệt rõ

### 📋 Metadata
- **ID:** `BP-15.04`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Scope:** Kubernetes probes
- **Lý do:** Tránh restart loop khi chỉ cần loại khỏi load balancer

### 🎯 Tại sao?

**Vấn đề:**
- Dùng cùng endpoint cho liveness/readiness → sai logic
- DB down → liveness fail → restart pod (không giải quyết vấn đề)
- App khởi động chậm → liveness fail trước khi ready → restart loop

**Lợi ích:**
- ✅ Liveness: chỉ restart khi app deadlock/corrupted state
- ✅ Readiness: loại khỏi load balancer khi dependencies down
- ✅ Tránh restart loop không cần thiết
- ✅ Faster recovery (deregister vs restart)

**Khi nào bỏ qua:**
- Stateless app không có external dependencies (hiếm)

### ✅ Cách đúng

**1. Phân biệt liveness vs readiness:**

| Aspect | Liveness | Readiness |
|--------|----------|-----------|
| **Mục đích** | App còn sống không? | Sẵn sàng nhận traffic không? |
| **Khi fail** | Restart container | Loại khỏi load balancer (giữ container) |
| **Check gì** | Internal state (deadlock, OOM) | External dependencies (DB, cache, APIs) |
| **Endpoint** | `/actuator/health/liveness` | `/actuator/health/readiness` |
| **Fail recovery** | Restart pod | Chờ dependencies khôi phục |

**2. Cấu hình Spring Boot:**

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true  # Enable /liveness, /readiness
      group:
        # Liveness: chỉ check internal state
        liveness:
          include: ping,livenessState
          show-details: never

        # Readiness: check dependencies
        readiness:
          include: readinessState,db,redis,externalApi
          show-details: when-authorized

  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

**3. Custom liveness indicator (ít dùng):**

```java
// Liveness: chỉ check internal critical state
@Component
public class AppLivenessIndicator implements HealthIndicator {

  private final AtomicBoolean alive = new AtomicBoolean(true);

  @Override
  public Health health() {
    if (!alive.get()) {
      return Health.down()
        .withDetail("reason", "Application in corrupted state")
        .build();
    }
    return Health.up().build();
  }

  // Gọi khi phát hiện unrecoverable error
  public void markAsDead() {
    alive.set(false);
  }
}
```

**4. Custom readiness indicator:**

```java
// Readiness: check dependencies
@Component
public class DatabaseReadinessIndicator implements HealthIndicator {

  private final DataSource dataSource;

  @Override
  public Health health() {
    try (var conn = dataSource.getConnection()) {
      conn.createStatement().execute("SELECT 1");
      return Health.up().build();
    } catch (SQLException e) {
      // DB down → readiness fail → deregister (không restart)
      return Health.down()
        .withDetail("error", e.getMessage())
        .build();
    }
  }
}

@Component
public class CacheReadinessIndicator implements HealthIndicator {

  private final RedisTemplate<String, String> redisTemplate;

  @Override
  public Health health() {
    try {
      redisTemplate.opsForValue().get("health-check");
      return Health.up().build();
    } catch (Exception e) {
      // Cache down → readiness fail → deregister
      return Health.down()
        .withDetail("error", e.getMessage())
        .build();
    }
  }
}
```

**5. Kubernetes deployment:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        # Liveness: app còn sống không?
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60      # Chờ app khởi động
          periodSeconds: 10             # Check mỗi 10s
          timeoutSeconds: 5
          failureThreshold: 3           # Restart sau 3 lần fail
          successThreshold: 1

        # Readiness: sẵn sàng nhận traffic?
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30       # Chờ dependencies connect
          periodSeconds: 5              # Check thường xuyên hơn
          timeoutSeconds: 3
          failureThreshold: 2           # Deregister sau 2 lần fail
          successThreshold: 1           # Re-register ngay khi OK

        # Startup: chờ app khởi động chậm (Spring Boot 3.x)
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 0
          periodSeconds: 5
          failureThreshold: 30          # 30 * 5s = 150s max startup
```

**6. Response examples:**

```json
// GET /actuator/health/liveness
{
  "status": "UP",
  "components": {
    "livenessState": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}

// GET /actuator/health/readiness
{
  "status": "DOWN",  // DB down → không ready
  "components": {
    "readinessState": {
      "status": "UP"
    },
    "db": {
      "status": "DOWN",  // ← Nguyên nhân readiness fail
      "details": {
        "error": "Connection timeout"
      }
    },
    "redis": {
      "status": "UP"
    }
  }
}
```

**7. Testing scenarios:**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProbeTest {

  @LocalServerPort
  private int port;

  @Test
  void livenessShouldNotCheckDependencies() {
    // Giả lập DB down
    stopDatabase();

    // Liveness vẫn UP (không check DB)
    var response = restTemplate.getForEntity(
      "http://localhost:" + port + "/actuator/health/liveness",
      HealthResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getStatus()).isEqualTo("UP");
  }

  @Test
  void readinessShouldFailWhenDatabaseDown() {
    stopDatabase();

    // Readiness fail (check DB)
    var response = restTemplate.getForEntity(
      "http://localhost:" + port + "/actuator/health/readiness",
      HealthResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().getStatus()).isEqualTo("DOWN");
  }
}
```

### ❌ Cách sai

```yaml
# ❌ SAI: Dùng cùng endpoint
livenessProbe:
  httpGet:
    path: /actuator/health  # ❌ Cùng endpoint
    port: 8080

readinessProbe:
  httpGet:
    path: /actuator/health  # ❌ Cùng endpoint
    port: 8080

# Vấn đề:
# - DB down → health DOWN
# - Liveness fail → restart pod (sai)
# - Restart loop vô tận (DB vẫn down)
```

```yaml
# ❌ SAI: Liveness check dependencies
management:
  endpoint:
    health:
      group:
        liveness:
          include: livenessState,db,redis  # ❌ Không nên check DB/Redis

# Vấn đề: DB down → restart pod (không cần thiết)
```

```yaml
# ❌ SAI: Readiness không check dependencies
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState  # ❌ Thiếu db, redis, externalApi

# Vấn đề:
# - DB down nhưng readiness UP
# - Load balancer gửi traffic → 500 errors
```

```yaml
# ❌ SAI: initialDelaySeconds quá ngắn
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10  # ❌ App chưa khởi động xong

# Vấn đề: Restart loop (app chưa ready → liveness fail)
```

```yaml
# ❌ SAI: Không có startupProbe cho app chậm
livenessProbe:
  initialDelaySeconds: 120  # ❌ Hardcode 2 phút
  failureThreshold: 3

# Vấn đề:
# - App khởi động 1 phút → OK
# - App khởi động 3 phút → restart loop
# - Nên dùng startupProbe
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check probes enabled
grep -r "management.endpoint.health.probes.enabled" src/main/resources/

# Check liveness/readiness groups
grep -A 5 "group.liveness\|group.readiness" src/main/resources/

# Check K8s probes khác endpoint
grep -E "livenessProbe|readinessProbe" -A 3 k8s/*.yml | grep "path:"
```

**Runtime check:**

```bash
# Test liveness
curl http://localhost:8080/actuator/health/liveness

# Test readiness
curl http://localhost:8080/actuator/health/readiness

# So sánh response
```

### ✓ Checklist tự kiểm tra

- [ ] `management.endpoint.health.probes.enabled=true`
- [ ] Liveness group chỉ include `livenessState`, `ping`
- [ ] Readiness group include `readinessState`, `db`, `redis`, external APIs
- [ ] K8s `livenessProbe` dùng `/actuator/health/liveness`
- [ ] K8s `readinessProbe` dùng `/actuator/health/readiness`
- [ ] `initialDelaySeconds` đủ lớn (liveness: 60s, readiness: 30s)
- [ ] `failureThreshold` phù hợp (liveness: 3, readiness: 2)
- [ ] Có `startupProbe` nếu app khởi động chậm (>60s)
- [ ] Test: DB down → readiness fail, liveness OK

---

## 15.05 - CI/CD pipeline: build → test → scan → deploy

### 📋 Metadata
- **ID:** `BP-15.05`
- **Mức độ:** 🔴 BẮT BUỘC
- **Scope:** CI/CD, Automation
- **Lý do:** Đảm bảo code quality, security trước khi deploy production

### 🎯 Tại sao?

**Vấn đề:**
- Manual deployment → sai sót, không reproducible
- Không chạy tests → bugs vào production
- Không scan security → vulnerabilities không phát hiện

**Lợi ích:**
- ✅ Automated testing → catch bugs sớm
- ✅ Security scanning → phát hiện CVEs
- ✅ Reproducible builds → cùng code = cùng artifact
- ✅ Faster deployment → 10 phút vs 2 giờ manual

**Khi nào bỏ qua:**
- Không bao giờ (bắt buộc cho production)

### ✅ Cách đúng

**1. GitHub Actions pipeline:**

```yaml
# .github/workflows/ci-cd.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  JAVA_VERSION: 21
  DOCKER_REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # ========== Stage 1: Build ==========
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: 'maven'

    - name: Build with Maven
      run: ./mvnw clean package -DskipTests

    - name: Upload JAR artifact
      uses: actions/upload-artifact@v4
      with:
        name: app-jar
        path: target/*.jar
        retention-days: 7

  # ========== Stage 2: Test ==========
  test:
    runs-on: ubuntu-latest
    needs: build
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: 'maven'

    - name: Run unit tests
      run: ./mvnw test

    - name: Run integration tests
      run: ./mvnw verify -Pintegration-tests

    - name: Generate coverage report
      run: ./mvnw jacoco:report

    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v4
      with:
        files: ./target/site/jacoco/jacoco.xml
        fail_ci_if_error: true
        token: ${{ secrets.CODECOV_TOKEN }}

    - name: Enforce coverage threshold (80%)
      run: |
        coverage=$(grep -oP 'INSTRUCTION.*?(\d+)%' target/site/jacoco/index.html | tail -1 | grep -oP '\d+')
        if [ "$coverage" -lt 80 ]; then
          echo "Coverage $coverage% < 80%"
          exit 1
        fi

  # ========== Stage 3: Security Scan ==========
  security:
    runs-on: ubuntu-latest
    needs: build
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Run OWASP Dependency Check
      uses: dependency-check/Dependency-Check_Action@main
      with:
        project: 'my-spring-app'
        path: '.'
        format: 'HTML'
        args: >
          --failOnCVSS 7
          --suppression suppression.xml

    - name: Run Trivy vulnerability scanner
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        scan-ref: '.'
        severity: 'CRITICAL,HIGH'
        exit-code: '1'

    - name: Run Snyk security scan
      uses: snyk/actions/maven@master
      env:
        SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
      with:
        args: --severity-threshold=high

  # ========== Stage 4: Build Docker Image ==========
  docker:
    runs-on: ubuntu-latest
    needs: [test, security]
    if: github.ref == 'refs/heads/main'
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v3

    - name: Log in to GitHub Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.DOCKER_REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.DOCKER_REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=sha,prefix={{branch}}-
          type=ref,event=branch
          type=semver,pattern={{version}}

    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: .
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=gha
        cache-to: type=gha,mode=max

    - name: Scan Docker image with Trivy
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ${{ env.DOCKER_REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.meta.outputs.version }}
        severity: 'CRITICAL,HIGH'
        exit-code: '1'

  # ========== Stage 5: Deploy to Staging ==========
  deploy-staging:
    runs-on: ubuntu-latest
    needs: docker
    if: github.ref == 'refs/heads/develop'
    environment:
      name: staging
      url: https://staging.example.com
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up kubectl
      uses: azure/setup-kubectl@v3

    - name: Configure kubectl
      run: |
        echo "${{ secrets.KUBECONFIG_STAGING }}" | base64 -d > kubeconfig.yml
        export KUBECONFIG=kubeconfig.yml

    - name: Deploy to Kubernetes
      run: |
        kubectl set image deployment/my-app \
          app=${{ env.DOCKER_REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
          -n staging

    - name: Wait for rollout
      run: kubectl rollout status deployment/my-app -n staging --timeout=5m

    - name: Run smoke tests
      run: |
        curl -f https://staging.example.com/actuator/health || exit 1

  # ========== Stage 6: Deploy to Production ==========
  deploy-production:
    runs-on: ubuntu-latest
    needs: docker
    if: github.ref == 'refs/heads/main'
    environment:
      name: production
      url: https://example.com
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up kubectl
      uses: azure/setup-kubectl@v3

    - name: Configure kubectl
      run: |
        echo "${{ secrets.KUBECONFIG_PRODUCTION }}" | base64 -d > kubeconfig.yml
        export KUBECONFIG=kubeconfig.yml

    - name: Deploy with Helm
      run: |
        helm upgrade --install my-app ./helm-chart \
          --set image.tag=${{ github.sha }} \
          --namespace production \
          --wait \
          --timeout 10m

    - name: Run smoke tests
      run: |
        curl -f https://example.com/actuator/health || exit 1

    - name: Notify Slack
      uses: slackapi/slack-github-action@v1
      with:
        webhook-url: ${{ secrets.SLACK_WEBHOOK }}
        payload: |
          {
            "text": "✅ Deployment to production successful",
            "blocks": [
              {
                "type": "section",
                "text": {
                  "type": "mrkdwn",
                  "text": "*Deployment successful*\nCommit: ${{ github.sha }}\nAuthor: ${{ github.actor }}"
                }
              }
            ]
          }
```

**2. GitLab CI/CD:**

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - security
  - docker
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  DOCKER_REGISTRY: registry.gitlab.com
  IMAGE_NAME: $CI_REGISTRY_IMAGE

cache:
  paths:
    - .m2/repository

# ========== Build ==========
build:
  stage: build
  image: eclipse-temurin:21-jdk
  script:
    - ./mvnw clean package -DskipTests
  artifacts:
    paths:
      - target/*.jar
    expire_in: 1 week

# ========== Test ==========
test:unit:
  stage: test
  image: eclipse-temurin:21-jdk
  script:
    - ./mvnw test
  coverage: '/Total.*?([0-9]{1,3})%/'
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml

test:integration:
  stage: test
  image: eclipse-temurin:21-jdk
  services:
    - postgres:15
  variables:
    POSTGRES_DB: testdb
    POSTGRES_USER: testuser
    POSTGRES_PASSWORD: testpass
  script:
    - ./mvnw verify -Pintegration-tests

# ========== Security ==========
security:dependency-check:
  stage: security
  image: owasp/dependency-check:latest
  script:
    - /usr/share/dependency-check/bin/dependency-check.sh
      --project my-app
      --scan .
      --format HTML
      --failOnCVSS 7
  artifacts:
    paths:
      - dependency-check-report.html
    when: always

security:trivy:
  stage: security
  image: aquasec/trivy:latest
  script:
    - trivy fs --severity CRITICAL,HIGH --exit-code 1 .

# ========== Docker ==========
docker:build:
  stage: docker
  image: docker:latest
  services:
    - docker:dind
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
  script:
    - docker build -t $IMAGE_NAME:$CI_COMMIT_SHA .
    - docker push $IMAGE_NAME:$CI_COMMIT_SHA
    - docker tag $IMAGE_NAME:$CI_COMMIT_SHA $IMAGE_NAME:latest
    - docker push $IMAGE_NAME:latest
  only:
    - main

# ========== Deploy ==========
deploy:staging:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context staging
    - kubectl set image deployment/my-app app=$IMAGE_NAME:$CI_COMMIT_SHA -n staging
    - kubectl rollout status deployment/my-app -n staging
  environment:
    name: staging
    url: https://staging.example.com
  only:
    - develop

deploy:production:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context production
    - kubectl set image deployment/my-app app=$IMAGE_NAME:$CI_COMMIT_SHA -n production
    - kubectl rollout status deployment/my-app -n production
  environment:
    name: production
    url: https://example.com
  when: manual
  only:
    - main
```

**3. Maven plugins cho CI:**

```xml
<!-- pom.xml -->
<build>
  <plugins>
    <!-- Code coverage -->
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.11</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>test</phase>
          <goals>
            <goal>report</goal>
          </goals>
        </execution>
        <execution>
          <id>check</id>
          <goals>
            <goal>check</goal>
          </goals>
          <configuration>
            <rules>
              <rule>
                <element>BUNDLE</element>
                <limits>
                  <limit>
                    <counter>INSTRUCTION</counter>
                    <value>COVEREDRATIO</value>
                    <minimum>0.80</minimum>
                  </limit>
                </limits>
              </rule>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>

    <!-- Dependency vulnerability check -->
    <plugin>
      <groupId>org.owasp</groupId>
      <artifactId>dependency-check-maven</artifactId>
      <version>9.0.9</version>
      <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFile>suppression.xml</suppressionFile>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

    <!-- Static code analysis -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-pmd-plugin</artifactId>
      <version>3.21.2</version>
      <configuration>
        <failOnViolation>true</failOnViolation>
        <rulesets>
          <ruleset>/category/java/bestpractices.xml</ruleset>
          <ruleset>/category/java/security.xml</ruleset>
        </rulesets>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

### ❌ Cách sai

```yaml
# ❌ SAI: Không chạy tests
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - run: ./mvnw package -DskipTests  # ❌ Skip tests
    - run: docker build -t app .
    - run: docker push app

# Vấn đề: Bugs vào production
```

```yaml
# ❌ SAI: Không scan security
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - run: docker build -t app .
    - run: docker push app  # ❌ Không scan vulnerabilities
    - run: kubectl apply -f deploy.yml

# Vấn đề: CVEs không phát hiện
```

```yaml
# ❌ SAI: Deploy trực tiếp production
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - run: kubectl apply -f deploy.yml  # ❌ Không có staging

# Vấn đề: Không test trước khi production
```

```yaml
# ❌ SAI: Không kiểm tra rollout
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - run: kubectl set image deployment/app app=new-image
    # ❌ Không chờ rollout complete

# Vấn đề: Deploy fail nhưng pipeline success
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check CI config exists
ls -la .github/workflows/*.yml .gitlab-ci.yml

# Check tests được chạy
grep -r "mvnw test\|gradlew test" .github/workflows/ .gitlab-ci.yml

# Check security scan
grep -r "trivy\|dependency-check\|snyk" .github/workflows/ .gitlab-ci.yml
```

### ✓ Checklist tự kiểm tra

- [ ] CI/CD config file tồn tại (GitHub Actions/GitLab CI/Jenkins)
- [ ] Build stage: compile + package
- [ ] Test stage: unit tests + integration tests
- [ ] Coverage enforcement (>= 80%)
- [ ] Security scan: dependency check + container scan
- [ ] Docker build chỉ khi tests pass
- [ ] Staging deployment trước production
- [ ] Rollout status check
- [ ] Smoke tests sau deploy
- [ ] Notification khi deploy fail

---

## 15.06 - Blue-green hoặc rolling deployment

### 📋 Metadata
- **ID:** `BP-15.06`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Scope:** Deployment strategy
- **Lý do:** Zero-downtime deployment, instant rollback

### 🎯 Tại sao?

**Vấn đề:**
- Recreate deployment → downtime 30s - 2 phút
- Không test production environment trước khi cutover
- Rollback chậm (redeploy old version)

**Lợi ích:**
- ✅ Zero downtime deployment
- ✅ Instant rollback (switch traffic back)
- ✅ Test production environment trước khi cutover
- ✅ A/B testing capabilities

**Khi nào bỏ qua:**
- Dev/staging environments
- Single-instance deployments

### ✅ Cách đúng

**1. Rolling deployment (Kubernetes default):**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  replicas: 6

  # Rolling update strategy
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2        # Tối đa 2 pods mới thêm vào (6 + 2 = 8 pods)
      maxUnavailable: 1  # Tối đa 1 pod unavailable (6 - 1 = 5 pods healthy)

  minReadySeconds: 10  # Chờ 10s sau khi ready mới đánh dấu available

  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:v2

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          periodSeconds: 5
          failureThreshold: 2

        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          periodSeconds: 10
          failureThreshold: 3

---
# Service (stable endpoint)
apiVersion: v1
kind: Service
metadata:
  name: my-spring-app
spec:
  selector:
    app: my-spring-app
  ports:
  - port: 80
    targetPort: 8080
```

**Rollout process:**

```
Initial state: 6 pods (v1)
  ↓
Create 2 new pods (v2) → 8 pods total (6 v1 + 2 v2)
  ↓
Wait for v2 pods ready
  ↓
Terminate 1 v1 pod → 7 pods (5 v1 + 2 v2)
  ↓
Create 1 new v2 pod → 8 pods (5 v1 + 3 v2)
  ↓
Repeat until all v1 → v2
```

**2. Blue-green deployment:**

```yaml
# blue-deployment.yml (current production)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-blue
  labels:
    version: blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-spring-app
      version: blue
  template:
    metadata:
      labels:
        app: my-spring-app
        version: blue
    spec:
      containers:
      - name: app
        image: my-spring-app:v1

---
# green-deployment.yml (new version)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-green
  labels:
    version: green
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-spring-app
      version: green
  template:
    metadata:
      labels:
        app: my-spring-app
        version: green
    spec:
      containers:
      - name: app
        image: my-spring-app:v2

---
# service.yml (switch traffic)
apiVersion: v1
kind: Service
metadata:
  name: my-spring-app
spec:
  selector:
    app: my-spring-app
    version: blue  # Switch to 'green' for cutover
  ports:
  - port: 80
    targetPort: 8080
```

**Deployment script:**

```bash
#!/bin/bash
set -e

# 1. Deploy green (new version)
kubectl apply -f green-deployment.yml

# 2. Wait for green ready
kubectl rollout status deployment/my-spring-app-green --timeout=5m

# 3. Test green internally
kubectl port-forward deployment/my-spring-app-green 8080:8080 &
PF_PID=$!
sleep 5
curl -f http://localhost:8080/actuator/health || {
  echo "Health check failed"
  kill $PF_PID
  exit 1
}
kill $PF_PID

# 4. Switch traffic to green
kubectl patch service my-spring-app -p '{"spec":{"selector":{"version":"green"}}}'

echo "Traffic switched to green"

# 5. Monitor for 10 minutes
sleep 600

# 6. Delete blue (old version)
kubectl delete deployment my-spring-app-blue

echo "Blue-green deployment complete"
```

**Rollback:**

```bash
# Instant rollback: switch service back to blue
kubectl patch service my-spring-app -p '{"spec":{"selector":{"version":"blue"}}}'
```

**3. Canary deployment:**

```yaml
# stable-deployment.yml (90% traffic)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-stable
spec:
  replicas: 9
  selector:
    matchLabels:
      app: my-spring-app
      track: stable
  template:
    metadata:
      labels:
        app: my-spring-app
        track: stable
    spec:
      containers:
      - name: app
        image: my-spring-app:v1

---
# canary-deployment.yml (10% traffic)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-canary
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-spring-app
      track: canary
  template:
    metadata:
      labels:
        app: my-spring-app
        track: canary
    spec:
      containers:
      - name: app
        image: my-spring-app:v2

---
# service.yml (both stable + canary)
apiVersion: v1
kind: Service
metadata:
  name: my-spring-app
spec:
  selector:
    app: my-spring-app  # Match both stable + canary
  ports:
  - port: 80
    targetPort: 8080
```

**Canary process:**

```
1. Deploy canary (1 pod v2) + stable (9 pods v1) → 10% traffic to v2
2. Monitor metrics: error rate, latency, CPU
3. Increase canary: 3 pods v2 + 7 pods v1 → 30% traffic
4. Monitor again
5. Full rollout: 10 pods v2, delete stable
```

**4. Argo Rollouts (advanced):**

```yaml
# rollout.yml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: my-spring-app
spec:
  replicas: 5

  strategy:
    canary:
      steps:
      - setWeight: 20    # 20% traffic to new version
      - pause:           # Manual approval
          duration: 5m
      - setWeight: 50    # 50% traffic
      - pause:
          duration: 5m
      - setWeight: 100   # Full rollout

      # Auto-rollback on metric failure
      analysis:
        templates:
        - templateName: error-rate-check
        args:
        - name: service-name
          value: my-spring-app

  selector:
    matchLabels:
      app: my-spring-app

  template:
    metadata:
      labels:
        app: my-spring-app
    spec:
      containers:
      - name: app
        image: my-spring-app:v2

---
# analysis-template.yml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: error-rate-check
spec:
  args:
  - name: service-name
  metrics:
  - name: error-rate
    interval: 1m
    successCondition: result < 0.05  # <5% error rate
    provider:
      prometheus:
        address: http://prometheus:9090
        query: |
          sum(rate(http_requests_total{status=~"5..",service="{{args.service-name}}"}[1m]))
          /
          sum(rate(http_requests_total{service="{{args.service-name}}"}[1m]))
```

### ❌ Cách sai

```yaml
# ❌ SAI: Recreate strategy (downtime)
apiVersion: apps/v1
kind: Deployment
spec:
  strategy:
    type: Recreate  # ❌ Terminate all → create new

# Vấn đề: Downtime 30s - 2 phút
```

```yaml
# ❌ SAI: maxUnavailable = 100%
apiVersion: apps/v1
kind: Deployment
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 100%  # ❌ Terminate all pods

# Vấn đề: Giống recreate, có downtime
```

```bash
# ❌ SAI: Blue-green không test trước khi cutover
kubectl apply -f green-deployment.yml
kubectl patch service app -p '{"spec":{"selector":{"version":"green"}}}'
# ❌ Không test green

# Vấn đề: Lỗi phát hiện sau khi production nhận traffic
```

```yaml
# ❌ SAI: Không có readiness probe
spec:
  template:
    spec:
      containers:
      - name: app
        # ❌ Thiếu readinessProbe

# Vấn đề: Traffic gửi đến pod chưa ready
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check deployment strategy
grep -A 5 "strategy:" k8s/*.yml

# Check readiness probe
grep -A 10 "readinessProbe:" k8s/*.yml
```

### ✓ Checklist tự kiểm tra

- [ ] Deployment strategy: RollingUpdate (không Recreate)
- [ ] `maxSurge` và `maxUnavailable` được set (ví dụ: 25%, 1)
- [ ] `minReadySeconds` >= 10s
- [ ] Readiness probe configured
- [ ] Blue-green: test green trước khi cutover
- [ ] Rollback plan documented
- [ ] Monitor metrics sau deployment (error rate, latency)

---

## 15.07 - Container resource limits (CPU, memory)

### 📋 Metadata
- **ID:** `BP-15.07`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Scope:** Kubernetes resources
- **Lý do:** Tránh OOM kill, resource starvation, noisy neighbor

### 🎯 Tại sao?

**Vấn đề:**
- Không set limits → 1 pod ngốn hết node resources
- Set limits quá thấp → OOM kill, throttling
- Set limits quá cao → lãng phí, không schedule được

**Lợi ích:**
- ✅ Predictable performance
- ✅ Tránh noisy neighbor problem
- ✅ Efficient resource utilization
- ✅ Auto-scaling dựa trên usage

**Khi nào bỏ qua:**
- Development environments (local Docker)

### ✅ Cách đúng

**1. Kubernetes resource requests & limits:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        resources:
          # Requests: guaranteed resources
          requests:
            cpu: "500m"      # 0.5 CPU cores
            memory: "512Mi"  # 512 MiB

          # Limits: maximum resources
          limits:
            cpu: "1000m"     # 1 CPU core
            memory: "1Gi"    # 1 GiB

        env:
        - name: JAVA_OPTS
          value: >-
            -XX:+UseContainerSupport
            -XX:MaxRAMPercentage=75.0
            -Xms512m
            -Xmx768m
```

**Sizing guide:**

| App Type | CPU Request | CPU Limit | Memory Request | Memory Limit |
|----------|-------------|-----------|----------------|--------------|
| **Lightweight API** | 250m | 500m | 256Mi | 512Mi |
| **Standard API** | 500m | 1000m | 512Mi | 1Gi |
| **Heavy processing** | 1000m | 2000m | 1Gi | 2Gi |
| **Batch job** | 2000m | 4000m | 2Gi | 4Gi |

**2. Vertical Pod Autoscaler (VPA):**

```yaml
# vpa.yml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: my-spring-app-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-spring-app

  updatePolicy:
    updateMode: "Auto"  # Auto | Initial | Off

  resourcePolicy:
    containerPolicies:
    - containerName: app
      minAllowed:
        cpu: 250m
        memory: 256Mi
      maxAllowed:
        cpu: 2000m
        memory: 2Gi
      controlledResources:
      - cpu
      - memory
```

**3. Horizontal Pod Autoscaler (HPA):**

```yaml
# hpa.yml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-spring-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-spring-app

  minReplicas: 2
  maxReplicas: 10

  metrics:
  # CPU-based scaling
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70  # Scale when avg CPU > 70%

  # Memory-based scaling
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80  # Scale when avg memory > 80%

  # Custom metric (requests per second)
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"  # Scale when > 1000 req/s per pod

  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # Chờ 5 phút trước khi scale down
      policies:
      - type: Percent
        value: 50          # Scale down tối đa 50% pods mỗi lần
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0    # Scale up ngay lập tức
      policies:
      - type: Percent
        value: 100         # Scale up tối đa 100% (double) mỗi lần
        periodSeconds: 15
```

**4. ResourceQuota cho namespace:**

```yaml
# resource-quota.yml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: production-quota
  namespace: production
spec:
  hard:
    requests.cpu: "50"        # Tổng CPU requests
    requests.memory: "100Gi"  # Tổng memory requests
    limits.cpu: "100"         # Tổng CPU limits
    limits.memory: "200Gi"    # Tổng memory limits
    pods: "50"                # Tối đa 50 pods

---
# limit-range.yml
apiVersion: v1
kind: LimitRange
metadata:
  name: production-limits
  namespace: production
spec:
  limits:
  - type: Container
    default:         # Default limits
      cpu: "500m"
      memory: "512Mi"
    defaultRequest:  # Default requests
      cpu: "250m"
      memory: "256Mi"
    min:             # Minimum
      cpu: "100m"
      memory: "128Mi"
    max:             # Maximum
      cpu: "4000m"
      memory: "4Gi"
```

**5. Monitoring resource usage:**

```yaml
# prometheus-servicemonitor.yml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: my-spring-app
spec:
  selector:
    matchLabels:
      app: my-spring-app
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

**Grafana dashboard queries:**

```promql
# CPU usage vs limits
sum(rate(container_cpu_usage_seconds_total{pod=~"my-spring-app-.*"}[5m])) by (pod)
/
sum(kube_pod_container_resource_limits{resource="cpu", pod=~"my-spring-app-.*"}) by (pod)

# Memory usage vs limits
sum(container_memory_working_set_bytes{pod=~"my-spring-app-.*"}) by (pod)
/
sum(kube_pod_container_resource_limits{resource="memory", pod=~"my-spring-app-.*"}) by (pod)

# OOMKill events
rate(kube_pod_container_status_restarts_total{pod=~"my-spring-app-.*"}[5m])
```

**6. Load testing để xác định limits:**

```bash
# Apache Bench
ab -n 10000 -c 100 http://my-app.example.com/api/endpoint

# K6
k6 run --vus 100 --duration 5m load-test.js

# Monitor resource usage
kubectl top pods -n production

# Get recommendations from VPA
kubectl describe vpa my-spring-app-vpa
```

### ❌ Cách sai

```yaml
# ❌ SAI: Không set resources
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0
        # ❌ Thiếu resources

# Vấn đề:
# - 1 pod ngốn hết node CPU/memory
# - Không schedule được khi node đầy
# - HPA không hoạt động
```

```yaml
# ❌ SAI: Limits quá thấp
resources:
  limits:
    memory: "256Mi"  # ❌ Spring Boot cần ít nhất 512Mi

# Vấn đề: OOMKilled liên tục
```

```yaml
# ❌ SAI: Requests = Limits (QoS Guaranteed không cần thiết)
resources:
  requests:
    cpu: "2000m"
    memory: "2Gi"
  limits:
    cpu: "2000m"    # ❌ Giống requests
    memory: "2Gi"   # ❌ Giống requests

# Vấn đề:
# - Lãng phí resources (reserved nhưng không dùng hết)
# - Node không schedule được pods khác
```

```yaml
# ❌ SAI: JVM heap > container memory limit
resources:
  limits:
    memory: "512Mi"

env:
- name: JAVA_OPTS
  value: "-Xmx1g"  # ❌ 1GB > 512Mi

# Vấn đề: OOMKilled (JVM + overhead > container limit)
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check resources defined
grep -A 10 "resources:" k8s/*.yml

# Check missing limits
grep -L "limits:" k8s/*.yml
```

**Runtime check:**

```bash
# Check current usage
kubectl top pods -n production

# Check OOMKill events
kubectl get events -n production | grep OOMKilled

# Check VPA recommendations
kubectl describe vpa my-spring-app-vpa
```

### ✓ Checklist tự kiểm tra

- [ ] Tất cả pods có `resources.requests` và `resources.limits`
- [ ] CPU requests: 250m - 1000m (production API)
- [ ] Memory requests: 512Mi - 2Gi (Spring Boot)
- [ ] Limits > Requests (cho burst capacity)
- [ ] JVM `-Xmx` <= 75% container memory limit
- [ ] VPA hoặc load test để xác định optimal sizing
- [ ] HPA configured (min 2, max 10 replicas)
- [ ] ResourceQuota và LimitRange cho namespace
- [ ] Monitor OOMKill events (Prometheus alert)

---

## 15.08 - JVM tuning cho container (-XX:MaxRAMPercentage)

### 📋 Metadata
- **ID:** `BP-15.08`
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Scope:** JVM flags, Container runtime
- **Lý do:** JVM cần biết container memory limit để tránh OOMKill

### 🎯 Tại sao?

**Vấn đề:**
- JVM pre-Java 10 nhìn thấy host memory (64GB) thay vì container limit (1GB)
- Default max heap = 1/4 host memory → OOMKill
- `-Xmx` hardcoded → không flexible khi change container limits

**Lợi ích:**
- ✅ JVM tự điều chỉnh heap theo container limit
- ✅ Tránh OOMKill
- ✅ Tối ưu GC performance
- ✅ Không cần hardcode `-Xmx`

**Khi nào bỏ qua:**
- Không bao giờ (bắt buộc cho containerized Java)

### ✅ Cách đúng

**1. JVM flags cho container:**

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/*.jar app.jar

# JVM flags tối ưu cho container
ENTRYPOINT ["java", \
  # ========== Container Support ========== \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  \
  # ========== GC Configuration ========== \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:G1HeapRegionSize=16m", \
  \
  # ========== GC Logging ========== \
  "-Xlog:gc*:file=/app/logs/gc.log:time,level,tags:filecount=10,filesize=10M", \
  \
  # ========== Heap Dump on OOM ========== \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/app/logs/heapdump.hprof", \
  "-XX:+ExitOnOutOfMemoryError", \
  \
  # ========== Performance ========== \
  "-XX:+TieredCompilation", \
  "-XX:TieredStopAtLevel=1", \
  "-Djava.security.egd=file:/dev/./urandom", \
  \
  "-jar", "app.jar"]
```

**2. Memory calculation:**

```
Container Memory Limit: 1024 Mi (1 GiB)

MaxRAMPercentage=75.0 →  Heap max: 768 Mi
                         ↓
                    ┌─────────────────────┐
                    │  JVM Memory Layout   │
                    ├─────────────────────┤
                    │ Heap: 768 Mi (75%)  │  ← Application objects
                    ├─────────────────────┤
                    │ Metaspace: ~128 Mi  │  ← Classes, methods
                    ├─────────────────────┤
                    │ Code cache: ~48 Mi  │  ← JIT compiled code
                    ├─────────────────────┤
                    │ Thread stacks: ~32Mi│  ← Threads
                    ├─────────────────────┤
                    │ Native memory: ~48Mi│  ← NIO buffers, etc.
                    └─────────────────────┘
                    Total: ~1024 Mi
```

**Recommended percentages:**

| Container Memory | MaxRAMPercentage | InitialRAMPercentage | Heap Max |
|------------------|------------------|----------------------|----------|
| 512 Mi | 70% | 50% | 358 Mi |
| 1 Gi | 75% | 50% | 768 Mi |
| 2 Gi | 75% | 50% | 1.5 Gi |
| 4 Gi+ | 80% | 60% | 3.2 Gi+ |

**3. Kubernetes deployment:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        resources:
          requests:
            memory: "1Gi"
          limits:
            memory: "1Gi"

        env:
        # Override JVM flags nếu cần
        - name: JAVA_OPTS
          value: >-
            -XX:MaxRAMPercentage=75.0
            -XX:+UseG1GC

        # Mount volume cho logs
        volumeMounts:
        - name: logs
          mountPath: /app/logs

      volumes:
      - name: logs
        emptyDir: {}
```

**4. Spring Boot application.yml:**

```yaml
# application.yml
spring:
  application:
    name: my-spring-app

# JVM metrics exposure
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

**5. Monitoring JVM metrics:**

```yaml
# prometheus-queries.yml
# Heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# GC pause time
rate(jvm_gc_pause_seconds_sum[5m])

# GC frequency
rate(jvm_gc_pause_seconds_count[5m])

# Thread count
jvm_threads_live_threads

# Metaspace usage
jvm_memory_used_bytes{area="nonheap", id="Metaspace"}
```

**6. Testing memory limits:**

```java
// MemoryTestController.java
@RestController
@RequestMapping("/test")
public class MemoryTestController {

  @GetMapping("/memory-info")
  public Map<String, String> getMemoryInfo() {
    var runtime = Runtime.getRuntime();
    var mb = 1024 * 1024;

    return Map.of(
      "maxMemory", (runtime.maxMemory() / mb) + " MB",
      "totalMemory", (runtime.totalMemory() / mb) + " MB",
      "freeMemory", (runtime.freeMemory() / mb) + " MB",
      "usedMemory", ((runtime.totalMemory() - runtime.freeMemory()) / mb) + " MB"
    );
  }

  @PostMapping("/allocate/{sizeMb}")
  public String allocateMemory(@PathVariable int sizeMb) {
    var bytes = new byte[sizeMb * 1024 * 1024];
    System.gc();
    return "Allocated " + sizeMb + " MB";
  }
}
```

**Test script:**

```bash
# Check JVM sees container limit
kubectl exec -it my-spring-app-xxx -- java -XX:+PrintFlagsFinal -version | grep MaxRAMPercentage

# Test memory allocation
for i in {1..10}; do
  curl -X POST http://localhost:8080/test/allocate/100
  sleep 2
done

# Monitor memory
watch -n 1 'kubectl top pod my-spring-app-xxx'
```

**7. Alternative: Explicit -Xmx (less flexible):**

```yaml
# k8s-deployment.yml
spec:
  template:
    spec:
      containers:
      - name: app
        resources:
          limits:
            memory: "1Gi"
        env:
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx768m"  # Hardcoded

# Vấn đề:
# - Change container limit → cần update JAVA_OPTS
# - MaxRAMPercentage tự động scale
```

### ❌ Cách sai

```dockerfile
# ❌ SAI: Không set UseContainerSupport
FROM eclipse-temurin:21-jre-jammy

ENTRYPOINT ["java", "-jar", "app.jar"]
# ❌ Thiếu -XX:+UseContainerSupport

# Vấn đề: JVM nhìn thấy host memory
```

```yaml
# ❌ SAI: Heap > container limit
resources:
  limits:
    memory: "512Mi"

env:
- name: JAVA_OPTS
  value: "-Xmx1g"  # ❌ 1GB > 512Mi

# Vấn đề: OOMKilled
```

```dockerfile
# ❌ SAI: MaxRAMPercentage quá cao
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=95.0", \  # ❌ Quá cao
  "-jar", "app.jar"]

# Vấn đề:
# - Không đủ memory cho metaspace, code cache, threads
# - OOMKilled
```

```dockerfile
# ❌ SAI: Dùng JDK thay vì JRE
FROM eclipse-temurin:21-jdk-jammy  # ❌ JDK (400MB)

# Vấn đề: Image lớn, security risk
```

```yaml
# ❌ SAI: Không mount volume cho heap dump
spec:
  containers:
  - name: app
    env:
    - name: JAVA_OPTS
      value: "-XX:+HeapDumpOnOutOfMemoryError"
    # ❌ Thiếu volumeMount → heap dump mất khi pod restart

# Fix: Mount emptyDir hoặc PersistentVolume
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check UseContainerSupport
grep -r "UseContainerSupport" Dockerfile k8s/*.yml

# Check MaxRAMPercentage
grep -r "MaxRAMPercentage" Dockerfile k8s/*.yml

# Check hardcoded -Xmx
grep -r "\-Xmx" Dockerfile k8s/*.yml
```

**Runtime check:**

```bash
# Check JVM flags trong container
kubectl exec -it my-spring-app-xxx -- java -XX:+PrintFlagsFinal -version | grep -E "UseContainerSupport|MaxRAMPercentage"

# Check heap size
kubectl exec -it my-spring-app-xxx -- curl -s http://localhost:8080/test/memory-info
```

### ✓ Checklist tự kiểm tra

- [ ] `-XX:+UseContainerSupport` trong ENTRYPOINT
- [ ] `-XX:MaxRAMPercentage=75.0` (không hardcode `-Xmx`)
- [ ] `-XX:InitialRAMPercentage=50.0`
- [ ] GC logging enabled (`-Xlog:gc*`)
- [ ] Heap dump on OOM (`-XX:+HeapDumpOnOutOfMemoryError`)
- [ ] Volume mounted cho logs/heap dumps
- [ ] JVM max heap <= 80% container memory limit
- [ ] Monitoring JVM metrics (Prometheus)
- [ ] Tested với load test (xác nhận không OOMKill)

---

## 15.09 - Startup/shutdown hooks cho cleanup

### 📋 Metadata
- **ID:** `BP-15.09`
- **Mức độ:** 🟡 NÊN CÓ
- **Scope:** Application lifecycle
- **Lý do:** Cleanup resources, flush data, graceful shutdown

### 🎯 Tại sao?

**Vấn đề:**
- Connections không đóng → resource leak
- In-flight data không flush → mất dữ liệu
- Background threads không stop → zombie processes

**Lợi ích:**
- ✅ Proper resource cleanup
- ✅ Data integrity (flush caches, buffers)
- ✅ Graceful shutdown (no abrupt termination)
- ✅ Easier debugging (log shutdown events)

**Khi nào bỏ qua:**
- Stateless app không có background tasks

### ✅ Cách đúng

**1. Spring Boot lifecycle hooks:**

```java
@Component
@Slf4j
public class ApplicationLifecycleListener {

  // ========== Startup Hooks ==========

  @EventListener(ApplicationStartedEvent.class)
  public void onApplicationStarted() {
    log.info("Application started successfully");
    log.info("Java version: {}", System.getProperty("java.version"));
    log.info("Active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready to serve requests");
    // Warm up caches, connections
    warmUpResources();
  }

  // ========== Shutdown Hooks ==========

  @EventListener(ContextClosedEvent.class)
  public void onContextClosed() {
    log.info("Application context closing...");
    // Cleanup logic here
  }

  private void warmUpResources() {
    // Pre-load caches, establish connections
  }
}
```

**2. @PreDestroy cho cleanup:**

```java
@Service
@Slf4j
public class BackgroundTaskService {

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
  private final List<ScheduledFuture<?>> activeTasks = new CopyOnWriteArrayList<>();

  @Scheduled(fixedDelay = 60000)
  public void runBackgroundTask() {
    var future = scheduler.scheduleWithFixedDelay(
      () -> processData(),
      0, 60, TimeUnit.SECONDS
    );
    activeTasks.add(future);
  }

  @PreDestroy
  public void cleanup() {
    log.info("Shutting down BackgroundTaskService...");

    // Cancel active tasks
    activeTasks.forEach(task -> task.cancel(false));
    activeTasks.clear();

    // Shutdown executor
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
        log.warn("Executor did not terminate in time, forcing shutdown");
        var notExecuted = scheduler.shutdownNow();
        log.warn("Tasks not executed: {}", notExecuted.size());
      }
    } catch (InterruptedException e) {
      log.error("Interrupted during shutdown", e);
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    log.info("BackgroundTaskService shutdown complete");
  }

  private void processData() {
    // Background task logic
  }
}
```

**3. Database connection pool cleanup:**

```java
@Configuration
public class DatabaseConfig {

  @Bean
  public DataSource dataSource() {
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
    config.setUsername("user");
    config.setPassword("pass");
    config.setMaximumPoolSize(10);
    config.setConnectionTimeout(5000);
    config.setIdleTimeout(300000);
    config.setMaxLifetime(600000);

    return new HikariDataSource(config);
  }

  @PreDestroy
  public void cleanup() {
    log.info("Closing database connection pool...");
    // HikariCP auto-closes on context shutdown
  }
}
```

**4. Cache flush on shutdown:**

```java
@Service
@Slf4j
public class CacheService {

  private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  @Cacheable("users")
  public User getUser(Long id) {
    dirty.set(true);
    return userRepository.findById(id).orElse(null);
  }

  @PreDestroy
  public void flushCache() {
    if (dirty.get()) {
      log.info("Flushing cache to persistent storage...");

      try {
        // Persist cache to Redis/file
        persistCache();
        log.info("Cache flushed successfully");
      } catch (Exception e) {
        log.error("Failed to flush cache", e);
      }
    }

    cache.clear();
    log.info("Cache cleanup complete");
  }

  private void persistCache() {
    // Write cache to Redis/disk
  }
}
```

**5. Graceful HTTP client shutdown:**

```java
@Configuration
public class WebClientConfig {

  @Bean
  public WebClient webClient() {
    var connectionProvider = ConnectionProvider.builder("custom")
      .maxConnections(100)
      .pendingAcquireTimeout(Duration.ofSeconds(60))
      .maxIdleTime(Duration.ofSeconds(30))
      .build();

    var httpClient = HttpClient.create(connectionProvider)
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
      .responseTimeout(Duration.ofSeconds(10));

    return WebClient.builder()
      .clientConnector(new ReactorClientHttpConnector(httpClient))
      .build();
  }

  @PreDestroy
  public void cleanup() {
    log.info("Closing HTTP client connections...");
    // Reactor Netty auto-closes connections
  }
}
```

**6. JVM shutdown hook (low-level):**

```java
@Component
@Slf4j
public class JvmShutdownHook {

  @PostConstruct
  public void registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("JVM shutdown hook triggered");

      try {
        // Last-resort cleanup
        cleanupCriticalResources();
      } catch (Exception e) {
        log.error("Error in shutdown hook", e);
      }

      log.info("JVM shutdown hook complete");
    }));
  }

  private void cleanupCriticalResources() {
    // Flush logs, close file handles
  }
}
```

**7. Kubernetes preStop hook:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        lifecycle:
          # Execute before sending SIGTERM
          preStop:
            exec:
              command:
              - sh
              - -c
              - |
                # Deregister from load balancer
                curl -X POST http://localhost:8080/actuator/shutdown

                # Wait for connections to drain
                sleep 15

                # Final cleanup
                echo "PreStop hook complete"

        # Grace period for cleanup
        terminationGracePeriodSeconds: 60
```

**8. Health indicator for shutdown:**

```java
@Component
public class ShutdownHealthIndicator implements HealthIndicator {

  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  @Override
  public Health health() {
    if (shuttingDown.get()) {
      return Health.down()
        .withDetail("reason", "Application shutting down")
        .build();
    }
    return Health.up().build();
  }

  @EventListener(ContextClosedEvent.class)
  public void onShutdown() {
    shuttingDown.set(true);
  }
}
```

**9. Testing cleanup:**

```java
@SpringBootTest
class ShutdownTest {

  @Autowired
  private ConfigurableApplicationContext context;

  @Autowired
  private BackgroundTaskService backgroundTaskService;

  @Test
  void shouldCleanupResourcesOnShutdown() {
    // Verify resources active
    assertThat(backgroundTaskService.getActiveTasks()).isNotEmpty();

    // Trigger shutdown
    context.close();

    // Verify cleanup
    assertThat(backgroundTaskService.getActiveTasks()).isEmpty();
    assertThat(backgroundTaskService.isShutdown()).isTrue();
  }
}
```

### ❌ Cách sai

```java
// ❌ SAI: Không cleanup ExecutorService
@Service
public class BadService {

  private final ExecutorService executor = Executors.newFixedThreadPool(10);

  @Scheduled(fixedDelay = 1000)
  public void runTask() {
    executor.submit(() -> {
      // Long task
    });
  }

  // ❌ Thiếu @PreDestroy → threads leak
}
```

```java
// ❌ SAI: Block shutdown indefinitely
@Service
public class BadService {

  @PreDestroy
  public void cleanup() {
    while (true) {  // ❌ Infinite loop
      // Never completes
    }
  }
}
```

```java
// ❌ SAI: Throw exception trong @PreDestroy
@Service
public class BadService {

  @PreDestroy
  public void cleanup() {
    throw new RuntimeException("Cleanup failed");  // ❌ Exception stops cleanup
  }
}

// Vấn đề: Other @PreDestroy methods không được gọi
```

```yaml
# ❌ SAI: terminationGracePeriodSeconds quá ngắn
spec:
  containers:
  - name: app
    terminationGracePeriodSeconds: 5  # ❌ Quá ngắn

# Vấn đề:
# - Spring shutdown timeout = 30s
# - K8s timeout = 5s
# - SIGKILL trước khi cleanup xong
```

### 🔍 Phát hiện

**Grep pattern:**

```bash
# Check @PreDestroy methods
grep -r "@PreDestroy" src/main/java/

# Check shutdown hooks
grep -r "addShutdownHook\|ContextClosedEvent" src/main/java/

# Check terminationGracePeriodSeconds
grep "terminationGracePeriodSeconds" k8s/*.yml
```

**Runtime check:**

```bash
# Test graceful shutdown
kubectl exec -it my-spring-app-xxx -- kill -TERM 1

# Check logs
kubectl logs -f my-spring-app-xxx | grep -i "shutdown\|cleanup"
```

### ✓ Checklist tự kiểm tra

- [ ] `ExecutorService` có `@PreDestroy` shutdown
- [ ] Database connections tự đóng (HikariCP)
- [ ] Caches được flush trước khi shutdown
- [ ] HTTP clients close connections gracefully
- [ ] `@PreDestroy` không throw exceptions
- [ ] `@PreDestroy` timeout < `terminationGracePeriodSeconds`
- [ ] K8s `preStop` hook deregister từ load balancer
- [ ] Shutdown events được log
- [ ] Test shutdown logic (integration test)

---

## Tổng kết Domain 15

### 🎯 Best Practices bắt buộc (🔴)

1. **15.02** - Health check endpoint (/actuator/health)
2. **15.03** - Graceful shutdown (server.shutdown=graceful)
3. **15.05** - CI/CD pipeline: build → test → scan → deploy

### 🟠 Practices khuyến nghị (🟠)

1. **15.01** - Dockerfile multi-stage build (JRE only)
2. **15.04** - Readiness vs Liveness probes phân biệt rõ
3. **15.06** - Blue-green hoặc rolling deployment
4. **15.07** - Container resource limits (CPU, memory)
5. **15.08** - JVM tuning cho container (-XX:MaxRAMPercentage)

### 🟡 Practices nên có (🟡)

1. **15.09** - Startup/shutdown hooks cho cleanup

### 📊 Quick Reference

```yaml
# Complete production-ready deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: app
        image: my-spring-app:1.0.0

        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "1000m"
            memory: "2Gi"

        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5

        lifecycle:
          preStop:
            exec:
              command: ["sh", "-c", "sleep 15"]

        terminationGracePeriodSeconds: 60
```

**Dockerfile:**

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY pom.xml .
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd -r appuser && useradd -r -g appuser appuser
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
```

**application.yml:**

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db,redis
```
