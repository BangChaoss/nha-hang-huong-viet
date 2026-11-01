# Domain 13: Configuration & Profiles
> **Số practices:** 8 | 🔴 2 | 🟠 3 | 🟡 3
> **Trọng số:** ×1

---

## 13.01 Profile-based config: application-{profile}.yml 🔴

### Metadata
- **Mức độ:** 🔴 BẮT BUỘC
- **Lý do chính:** Tách biệt config cho từng môi trường (dev, test, prod)
- **Ảnh hưởng:** Giảm lỗi do config sai môi trường, dễ quản lý

### Tại sao?
1. **Tách biệt môi trường:** Mỗi môi trường có config riêng biệt (DB, URL, credentials)
2. **Giảm lỗi triển khai:** Không cần thay đổi code khi chuyển môi trường
3. **Dễ quản lý:** Tất cả config tập trung trong application-{profile}.yml
4. **Best practice chuẩn:** Spring Boot khuyến nghị sử dụng profiles

### ✅ Cách đúng

```yaml
# application.yml (default config)
spring:
  application:
    name: medicalbox-api
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

server:
  port: 8080

app:
  cors:
    allowed-origins: "*"
  jwt:
    expiration: 3600000

---
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medicalbox_dev
    username: dev_user
    password: dev_password
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update

logging:
  level:
    jp.medicalbox: DEBUG
    org.hibernate.SQL: DEBUG

app:
  cors:
    allowed-origins: "http://localhost:3000,http://localhost:5173"

---
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  test:
    database:
      replace: none

logging:
  level:
    jp.medicalbox: INFO

---
# application-staging.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:staging-db.example.com}:5432/${DB_NAME:medicalbox_staging}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    jp.medicalbox: INFO
    org.springframework: WARN

app:
  cors:
    allowed-origins: "https://staging.medicalbox.jp"

---
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: false

logging:
  level:
    jp.medicalbox: WARN
    org.springframework: ERROR

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

app:
  cors:
    allowed-origins: "https://medicalbox.jp,https://www.medicalbox.jp"
```

```java
// Kích hoạt profile qua environment variable
// VM options: -Dspring.profiles.active=prod
// Environment variable: SPRING_PROFILES_ACTIVE=prod
// Command line: java -jar app.jar --spring.profiles.active=prod

// Sử dụng multiple profiles
// SPRING_PROFILES_ACTIVE=prod,monitoring
```

```java
// Profile-specific beans
@Configuration
@Profile("dev")
public class DevConfig {

  @Bean
  public DataInitializer devDataInitializer() {
    return new DevDataInitializer(); // Seed test data
  }
}

@Configuration
@Profile("prod")
public class ProdConfig {

  @Bean
  public DataInitializer prodDataInitializer() {
    return new ProdDataInitializer(); // No seeding
  }
}
```

### ❌ Cách sai

```yaml
# ❌ Tất cả config trong một file, hardcoded cho prod
spring:
  datasource:
    url: jdbc:postgresql://prod-db.example.com:5432/medicalbox
    username: prod_user
    password: SecretPassword123  # ❌ Hardcoded password
  jpa:
    show-sql: true  # ❌ Show SQL in production

# ❌ Không có profile separation
```

```java
// ❌ Hardcoded config trong code
@Configuration
public class DatabaseConfig {

  @Bean
  public DataSource dataSource() {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl("jdbc:postgresql://localhost:5432/medicalbox"); // ❌ Hardcoded
    ds.setUsername("dev_user"); // ❌ Hardcoded
    ds.setPassword("dev_password"); // ❌ Hardcoded
    return ds;
  }
}
```

```yaml
# ❌ Sử dụng properties thay vì YAML (khó đọc hơn)
spring.datasource.url=jdbc:postgresql://localhost:5432/medicalbox
spring.datasource.username=dev_user
spring.datasource.password=dev_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### Phát hiện tự động

```regex
# Tìm hardcoded DB credentials trong code
\.setUsername\s*\(\s*"[^"]+"\s*\)
\.setPassword\s*\(\s*"[^"]+"\s*\)
\.setJdbcUrl\s*\(\s*"jdbc:[^"]+"\s*\)

# Tìm file không có profile suffix
application\.properties$
application\.yml$  # (nhưng cần kiểm tra có --- separator không)
```

### Checklist
- [ ] Có file `application.yml` (default config)
- [ ] Có file `application-dev.yml` (development)
- [ ] Có file `application-test.yml` (testing)
- [ ] Có file `application-prod.yml` (production)
- [ ] Không có hardcoded credentials trong config files
- [ ] Sử dụng environment variables cho sensitive data
- [ ] Profile được kích hoạt qua `SPRING_PROFILES_ACTIVE`
- [ ] Có default profile fallback (`${SPRING_PROFILES_ACTIVE:dev}`)

---

## 13.02 Sensitive config qua environment variables, không commit 🔴

### Metadata
- **Mức độ:** 🔴 BẮT BUỘC
- **Lý do chính:** Bảo mật, tránh lộ credentials, API keys
- **Ảnh hưởng:** Ngăn chặn security breach, compliance

### Tại sao?
1. **Bảo mật:** Không commit passwords, API keys vào Git
2. **12-factor app:** Externalized configuration là best practice
3. **Compliance:** GDPR, PCI-DSS yêu cầu bảo mật credentials
4. **Linh hoạt:** Thay đổi credentials không cần rebuild code

### ✅ Cách đúng

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}

app:
  jwt:
    secret: ${JWT_SECRET}  # Không commit JWT secret
    expiration: ${JWT_EXPIRATION:3600000}

  aws:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
    region: ${AWS_REGION:ap-northeast-1}

  mail:
    smtp:
      username: ${MAIL_USERNAME}
      password: ${MAIL_PASSWORD}
```

```bash
# .env (local development, KHÔNG commit)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=medicalbox_dev
DB_USERNAME=dev_user
DB_PASSWORD=dev_password_local

JWT_SECRET=local-dev-secret-key-change-in-prod
JWT_EXPIRATION=3600000

GOOGLE_CLIENT_ID=123456789.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxxxxx

AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
AWS_REGION=ap-northeast-1

MAIL_USERNAME=noreply@medicalbox.jp
MAIL_PASSWORD=smtp-password-here
```

```gitignore
# .gitignore (BẮT BUỘC)
.env
.env.*
!.env.example

application-local.yml
application-secret.yml
*-secret.yml

# Các file có thể chứa secrets
*.key
*.pem
*.p12
*.jks
*.keystore
```

```yaml
# .env.example (commit file này để hướng dẫn)
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=medicalbox_dev
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

# JWT Configuration
JWT_SECRET=your-secret-key-min-256-bits
JWT_EXPIRATION=3600000

# OAuth2 (Google)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

# AWS Configuration
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=ap-northeast-1

# Email Configuration
MAIL_USERNAME=your-smtp-username
MAIL_PASSWORD=your-smtp-password
```

```java
// Đọc environment variables trong code (nếu cần)
@Configuration
public class SecurityConfig {

  @Value("${JWT_SECRET}")
  private String jwtSecret;

  @Bean
  public JwtTokenProvider jwtTokenProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration}") long expiration
  ) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT_SECRET environment variable not set");
    }
    return new JwtTokenProvider(secret, expiration);
  }
}
```

```java
// Validation cho required environment variables
@Component
public class ConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

  @Value("${DB_PASSWORD:#{null}}")
  private String dbPassword;

  @Value("${JWT_SECRET:#{null}}")
  private String jwtSecret;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    List<String> missingVars = new ArrayList<>();

    if (dbPassword == null || dbPassword.isBlank()) {
      missingVars.add("DB_PASSWORD");
    }
    if (jwtSecret == null || jwtSecret.isBlank()) {
      missingVars.add("JWT_SECRET");
    }

    if (!missingVars.isEmpty()) {
      throw new IllegalStateException(
          "Missing required environment variables: " + String.join(", ", missingVars)
      );
    }
  }
}
```

### ❌ Cách sai

```yaml
# ❌ Hardcoded credentials trong config file (COMMIT vào Git)
spring:
  datasource:
    url: jdbc:postgresql://prod-db.example.com:5432/medicalbox
    username: prod_user
    password: P@ssw0rd123!  # ❌ NGUY HIỂM - committed to Git

app:
  jwt:
    secret: mySecretKey123456789  # ❌ Lộ JWT secret

  aws:
    access-key: AKIAIOSFODNN7EXAMPLE  # ❌ Lộ AWS key
    secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCY  # ❌ Lộ AWS secret
```

```java
// ❌ Hardcoded trong code
@Configuration
public class JwtConfig {

  private static final String JWT_SECRET = "mySecretKey123";  // ❌ Hardcoded

  @Bean
  public JwtTokenProvider jwtTokenProvider() {
    return new JwtTokenProvider(JWT_SECRET, 3600000);
  }
}
```

```yaml
# ❌ Comment chứa credentials (vẫn có thể lộ)
spring:
  datasource:
    password: ${DB_PASSWORD}
    # Old password: P@ssw0rd123!  ❌ Không nên ghi vào comment
```

```java
// ❌ Log sensitive data
log.info("Database password: {}", dbPassword);  // ❌ NGUY HIỂM
log.debug("JWT Secret: {}", jwtSecret);  // ❌ NGUY HIỂM
```

### Phát hiện tự động

```regex
# Tìm hardcoded passwords trong YAML
password:\s*[^$\s][^\s]+
secret:\s*[^$\s][^\s]+
api-?key:\s*[^$\s][^\s]+
token:\s*[^$\s][^\s]+

# Tìm hardcoded credentials trong Java
(password|secret|apiKey|token)\s*=\s*"[^"]+"

# Tìm AWS keys
AKIA[0-9A-Z]{16}
[0-9a-zA-Z/+=]{40}  # AWS Secret Access Key pattern

# Tìm JWT secrets hardcoded
jwt\.secret\s*=\s*"[^"]+"
```

### Checklist
- [ ] Không có passwords trong application.yml/properties
- [ ] Không có API keys trong config files
- [ ] Không có AWS credentials trong code
- [ ] File `.env` được thêm vào `.gitignore`
- [ ] Có file `.env.example` để hướng dẫn
- [ ] Sử dụng `${ENV_VAR}` cho tất cả sensitive data
- [ ] Validate required env vars khi startup
- [ ] Không log sensitive data (passwords, tokens, keys)

---

## 13.03 @ConfigurationProperties với @Validated 🟠

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do chính:** Type-safe config, validation tự động
- **Ảnh hưởng:** Giảm lỗi config, dễ maintain

### Tại sao?
1. **Type-safe:** Compile-time checking thay vì runtime string parsing
2. **Validation:** Tự động validate config khi startup
3. **IDE support:** Auto-completion, refactoring support
4. **Structured:** Group related properties thành object

### ✅ Cách đúng

```yaml
# application.yml
app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 3600000
    refresh-expiration-ms: 86400000
    issuer: medicalbox-api

  cors:
    allowed-origins:
      - https://medicalbox.jp
      - https://www.medicalbox.jp
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
    allowed-headers:
      - "*"
    max-age-seconds: 3600

  file-upload:
    max-size-mb: 10
    allowed-types:
      - image/jpeg
      - image/png
      - application/pdf
    storage-path: ${FILE_STORAGE_PATH:/var/medicalbox/uploads}
```

```java
// Type-safe configuration với validation
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
    @NotBlank(message = "JWT secret không được để trống")
    @Size(min = 32, message = "JWT secret phải có ít nhất 32 ký tự")
    String secret,

    @Positive(message = "Expiration phải là số dương")
    @Min(value = 60000, message = "Expiration tối thiểu 1 phút")
    long expirationMs,

    @Positive(message = "Refresh expiration phải là số dương")
    @Min(value = 3600000, message = "Refresh expiration tối thiểu 1 giờ")
    long refreshExpirationMs,

    @NotBlank(message = "Issuer không được để trống")
    String issuer
) {

  public Duration expiration() {
    return Duration.ofMillis(expirationMs);
  }

  public Duration refreshExpiration() {
    return Duration.ofMillis(refreshExpirationMs);
  }
}
```

```java
@ConfigurationProperties(prefix = "app.cors")
@Validated
public record CorsProperties(
    @NotEmpty(message = "Allowed origins không được để trống")
    List<@NotBlank String> allowedOrigins,

    @NotEmpty(message = "Allowed methods không được để trống")
    List<@NotBlank String> allowedMethods,

    @NotEmpty(message = "Allowed headers không được để trống")
    List<@NotBlank String> allowedHeaders,

    @Positive(message = "Max age phải là số dương")
    int maxAgeSeconds
) {}
```

```java
@ConfigurationProperties(prefix = "app.file-upload")
@Validated
public record FileUploadProperties(
    @Positive(message = "Max size phải là số dương")
    @Max(value = 100, message = "Max size không được vượt quá 100MB")
    int maxSizeMb,

    @NotEmpty(message = "Allowed types không được để trống")
    List<@Pattern(regexp = "^[a-z]+/[a-z0-9+.-]+$", message = "MIME type không hợp lệ") String> allowedTypes,

    @NotBlank(message = "Storage path không được để trống")
    String storagePath
) {

  public long maxSizeBytes() {
    return maxSizeMb * 1024L * 1024L;
  }

  public boolean isAllowedType(String mimeType) {
    return allowedTypes.contains(mimeType);
  }
}
```

```java
// Enable configuration properties
@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    CorsProperties.class,
    FileUploadProperties.class
})
public class AppConfig {
  // Configuration beans here
}
```

```java
// Sử dụng trong service
@Service
@RequiredArgsConstructor
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;

  public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
        .setSubject(userDetails.getUsername())
        .setIssuer(jwtProperties.issuer())
        .setIssuedAt(new Date())
        .setExpiration(Date.from(Instant.now().plus(jwtProperties.expiration())))
        .signWith(getSigningKey(), SignatureAlgorithm.HS512)
        .compact();
  }

  private Key getSigningKey() {
    byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
```

```java
// Complex nested properties
@ConfigurationProperties(prefix = "app.database")
@Validated
public record DatabaseProperties(
    @Valid HikariProperties hikari,
    @Valid RetryProperties retry
) {

  public record HikariProperties(
      @Positive int maximumPoolSize,
      @Positive int minimumIdle,
      @Positive long connectionTimeoutMs,
      @Positive long idleTimeoutMs
  ) {}

  public record RetryProperties(
      @Positive int maxAttempts,
      @Positive long initialIntervalMs,
      @Positive double multiplier
  ) {}
}
```

### ❌ Cách sai

```java
// ❌ Sử dụng @Value cho nhiều properties liên quan
@Configuration
public class JwtConfig {

  @Value("${app.jwt.secret}")
  private String jwtSecret;  // ❌ Không type-safe

  @Value("${app.jwt.expiration-ms}")
  private long jwtExpiration;  // ❌ Không validation

  @Value("${app.jwt.refresh-expiration-ms}")
  private long refreshExpiration;  // ❌ Scattered properties

  @Value("${app.jwt.issuer}")
  private String issuer;  // ❌ Không group logic

  @Bean
  public JwtTokenProvider jwtTokenProvider() {
    // Không validate được khi startup
    return new JwtTokenProvider(jwtSecret, jwtExpiration);
  }
}
```

```java
// ❌ Không validation
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {  // ❌ Mutable class

  private String secret;  // ❌ Không @NotBlank
  private long expirationMs;  // ❌ Không @Positive

  // Getters/Setters
  public String getSecret() { return secret; }
  public void setSecret(String secret) { this.secret = secret; }
}
```

```java
// ❌ Manual parsing trong code
@Configuration
public class CorsConfig {

  @Value("${app.cors.allowed-origins}")
  private String allowedOriginsString;  // ❌ String thay vì List

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    String[] origins = allowedOriginsString.split(",");  // ❌ Manual parsing
    // ...
  }
}
```

### Phát hiện tự động

```regex
# Tìm nhiều @Value cho cùng prefix
@Value\s*\(\s*"\$\{([^.}]+\.[^.}]+)\.

# Tìm @ConfigurationProperties không có @Validated
@ConfigurationProperties(?!.*@Validated)

# Tìm mutable configuration properties (có setter)
@ConfigurationProperties.*\n.*class.*\{[\s\S]*?public void set
```

### Checklist
- [ ] Sử dụng `@ConfigurationProperties` thay vì nhiều `@Value`
- [ ] Có annotation `@Validated` trên properties class
- [ ] Sử dụng `record` hoặc `final` fields (immutable)
- [ ] Có validation annotations (`@NotBlank`, `@Positive`, etc.)
- [ ] Group related properties thành nested objects
- [ ] Enable với `@EnableConfigurationProperties`
- [ ] Có custom validation messages (tiếng Việt)
- [ ] Có helper methods khi cần (vd: `maxSizeBytes()`)

---

## 13.04 Immutable configuration classes (record hoặc final fields) 🟠

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do chính:** Thread-safe, không thể thay đổi sau khi khởi tạo
- **Ảnh hưởng:** Giảm bugs, dễ reasoning

### Tại sao?
1. **Thread-safe:** Configuration không thay đổi sau startup
2. **Immutability:** Tránh bugs do mutation không mong muốn
3. **Cleaner code:** Record tự động generate constructor, getters, equals, hashCode
4. **Intent-revealing:** Rõ ràng là value object, không thay đổi

### ✅ Cách đúng

```java
// ✅ Sử dụng record (Java 16+)
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secret,
    @Positive long expirationMs,
    @Positive long refreshExpirationMs,
    @NotBlank String issuer
) {

  // Compact constructor cho custom validation
  public JwtProperties {
    if (secret.length() < 32) {
      throw new IllegalArgumentException("JWT secret phải có ít nhất 32 ký tự");
    }
  }

  // Helper methods (không mutate)
  public Duration expiration() {
    return Duration.ofMillis(expirationMs);
  }

  public Duration refreshExpiration() {
    return Duration.ofMillis(refreshExpirationMs);
  }
}
```

```java
// ✅ Immutable class với final fields (Java 8+)
@ConfigurationProperties(prefix = "app.cors")
@Validated
@Getter
public final class CorsProperties {

  private final List<String> allowedOrigins;
  private final List<String> allowedMethods;
  private final List<String> allowedHeaders;
  private final int maxAgeSeconds;

  // Constructor injection
  public CorsProperties(
      @NotEmpty List<String> allowedOrigins,
      @NotEmpty List<String> allowedMethods,
      @NotEmpty List<String> allowedHeaders,
      @Positive int maxAgeSeconds
  ) {
    // Defensive copy để đảm bảo immutability
    this.allowedOrigins = List.copyOf(allowedOrigins);
    this.allowedMethods = List.copyOf(allowedMethods);
    this.allowedHeaders = List.copyOf(allowedHeaders);
    this.maxAgeSeconds = maxAgeSeconds;
  }
}
```

```java
// ✅ Nested immutable properties
@ConfigurationProperties(prefix = "app.database")
@Validated
public record DatabaseProperties(
    @Valid ConnectionPool connectionPool,
    @Valid Retry retry,
    @Valid Monitoring monitoring
) {

  public record ConnectionPool(
      @Positive @Max(100) int maximumPoolSize,
      @Positive @Max(50) int minimumIdle,
      @Positive long connectionTimeoutMs,
      @Positive long idleTimeoutMs,
      @Positive long maxLifetimeMs
  ) {}

  public record Retry(
      @Positive @Max(5) int maxAttempts,
      @Positive long initialIntervalMs,
      @Positive @Max(3) double multiplier,
      @Positive long maxIntervalMs
  ) {}

  public record Monitoring(
      boolean enabled,
      @Positive int metricIntervalSeconds,
      @NotEmpty List<String> exporters
  ) {
    public Monitoring {
      exporters = List.copyOf(exporters);  // Defensive copy
    }
  }
}
```

```java
// ✅ Complex immutable properties với collections
@ConfigurationProperties(prefix = "app.file-upload")
@Validated
public record FileUploadProperties(
    @Positive @Max(100) int maxSizeMb,
    @NotEmpty List<String> allowedTypes,
    @NotBlank String storagePath,
    @Valid Map<String, CategoryConfig> categories
) {

  // Compact constructor với defensive copies
  public FileUploadProperties {
    allowedTypes = List.copyOf(allowedTypes);
    categories = Map.copyOf(categories);
  }

  public record CategoryConfig(
      @Positive int maxSizeMb,
      @NotEmpty List<String> allowedTypes
  ) {
    public CategoryConfig {
      allowedTypes = List.copyOf(allowedTypes);
    }
  }

  public long maxSizeBytes() {
    return maxSizeMb * 1024L * 1024L;
  }

  public boolean isAllowedType(String mimeType) {
    return allowedTypes.contains(mimeType);
  }

  public CategoryConfig getCategoryConfig(String category) {
    return categories.getOrDefault(category,
        new CategoryConfig(maxSizeMb, allowedTypes));
  }
}
```

```yaml
# application.yml tương ứng
app:
  file-upload:
    max-size-mb: 10
    allowed-types:
      - image/jpeg
      - image/png
      - application/pdf
    storage-path: /var/medicalbox/uploads
    categories:
      avatar:
        max-size-mb: 2
        allowed-types:
          - image/jpeg
          - image/png
      document:
        max-size-mb: 20
        allowed-types:
          - application/pdf
          - application/msword
```

```java
// ✅ Immutable với builder pattern (cho complex cases)
@ConfigurationProperties(prefix = "app.security")
@Validated
public final class SecurityProperties {

  private final JwtConfig jwt;
  private final OAuth2Config oauth2;
  private final RateLimitConfig rateLimit;

  private SecurityProperties(Builder builder) {
    this.jwt = builder.jwt;
    this.oauth2 = builder.oauth2;
    this.rateLimit = builder.rateLimit;
  }

  // Getters only
  public JwtConfig jwt() { return jwt; }
  public OAuth2Config oauth2() { return oauth2; }
  public RateLimitConfig rateLimit() { return rateLimit; }

  // Nested immutable records
  public record JwtConfig(
      @NotBlank String secret,
      @Positive long expirationMs
  ) {}

  public record OAuth2Config(
      Map<String, ProviderConfig> providers
  ) {
    public OAuth2Config {
      providers = Map.copyOf(providers);
    }

    public record ProviderConfig(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        List<String> scopes
    ) {
      public ProviderConfig {
        scopes = List.copyOf(scopes);
      }
    }
  }

  public record RateLimitConfig(
      boolean enabled,
      @Positive int requestsPerMinute
  ) {}
}
```

### ❌ Cách sai

```java
// ❌ Mutable class với setters
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public class JwtProperties {  // ❌ Không final

  @NotBlank
  private String secret;  // ❌ Không final

  @Positive
  private long expirationMs;  // ❌ Có thể thay đổi

  // ❌ Có setters - có thể mutation sau khi khởi tạo
  public void setSecret(String secret) {
    this.secret = secret;  // ❌ NGUY HIỂM
  }

  public void setExpirationMs(long expirationMs) {
    this.expirationMs = expirationMs;  // ❌ NGUY HIỂM
  }

  public String getSecret() { return secret; }
  public long getExpirationMs() { return expirationMs; }
}
```

```java
// ❌ Mutable collections
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
    List<String> allowedOrigins  // ❌ Có thể mutate: props.allowedOrigins().add(...)
) {}

// ✅ Phải sử dụng defensive copy
public record CorsProperties(
    List<String> allowedOrigins
) {
  public CorsProperties {
    allowedOrigins = List.copyOf(allowedOrigins);  // ✅ Immutable copy
  }
}
```

```java
// ❌ Expose mutable state
@ConfigurationProperties(prefix = "app.database")
public final class DatabaseProperties {

  private final List<String> hosts;

  public DatabaseProperties(List<String> hosts) {
    this.hosts = hosts;  // ❌ Không defensive copy
  }

  public List<String> getHosts() {
    return hosts;  // ❌ Caller có thể mutate
  }
}

// Caller có thể làm:
DatabaseProperties props = ...;
props.getHosts().add("malicious-host");  // ❌ Mutation
```

```java
// ❌ Manual mutation trong code
@Service
public class ConfigService {

  private final JwtProperties jwtProperties;

  public void updateExpiration(long newExpiration) {
    jwtProperties.setExpirationMs(newExpiration);  // ❌ Runtime mutation
  }
}
```

### Phát hiện tự động

```regex
# Tìm @ConfigurationProperties không final/record
@ConfigurationProperties.*\n\s*public class(?!.*final)

# Tìm setters trong configuration properties
@ConfigurationProperties[\s\S]*?public void set[A-Z]

# Tìm mutable collections không có defensive copy
public record.*\(\s*List<[^>]+>\s+\w+\s*\)(?![\s\S]*List\.copyOf)
public record.*\(\s*Map<[^>]+>\s+\w+\s*\)(?![\s\S]*Map\.copyOf)
```

### Checklist
- [ ] Configuration class là `record` hoặc `final class`
- [ ] Tất cả fields là `final`
- [ ] Không có setters
- [ ] Collections sử dụng `List.copyOf()`, `Map.copyOf()`
- [ ] Nested objects cũng immutable
- [ ] Không expose mutable references
- [ ] Constructor injection thay vì setter injection
- [ ] Có validation trong constructor (record compact constructor)

---

## 13.05 Default values hợp lý cho mọi config property 🟡

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do chính:** Application chạy được ngay cả khi thiếu config
- **Ảnh hưởng:** Giảm config errors, dễ onboarding

### Tại sao?
1. **Resilience:** Application không crash khi thiếu config
2. **Developer experience:** Dev mới có thể chạy app ngay lập tức
3. **Sensible defaults:** Theo convention over configuration
4. **Production-ready:** Defaults phải an toàn cho production

### ✅ Cách đúng

```yaml
# application.yml với defaults
app:
  jwt:
    secret: ${JWT_SECRET:default-dev-secret-change-in-production}  # ⚠️ Warning nếu dùng default
    expiration-ms: ${JWT_EXPIRATION_MS:3600000}  # Default 1 giờ
    refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION_MS:86400000}  # Default 1 ngày
    issuer: ${JWT_ISSUER:medicalbox-api}

  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
    allowed-methods: ${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,PATCH}
    allowed-headers: ${CORS_ALLOWED_HEADERS:*}
    max-age-seconds: ${CORS_MAX_AGE:3600}

  file-upload:
    max-size-mb: ${FILE_UPLOAD_MAX_SIZE_MB:10}
    storage-path: ${FILE_STORAGE_PATH:./uploads}  # Relative path cho dev
    allowed-types: ${FILE_UPLOAD_ALLOWED_TYPES:image/jpeg,image/png,application/pdf}

  database:
    connection-pool:
      maximum-pool-size: ${DB_MAX_POOL_SIZE:10}
      minimum-idle: ${DB_MIN_IDLE:5}
      connection-timeout-ms: ${DB_CONNECTION_TIMEOUT_MS:30000}
    retry:
      max-attempts: ${DB_RETRY_MAX_ATTEMPTS:3}
      initial-interval-ms: ${DB_RETRY_INITIAL_INTERVAL_MS:1000}
      multiplier: ${DB_RETRY_MULTIPLIER:2.0}

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/medicalbox_dev}
    username: ${DB_USERNAME:dev_user}
    password: ${DB_PASSWORD:dev_password}

  jpa:
    hibernate:
      ddl-auto: ${HIBERNATE_DDL_AUTO:update}  # Dev: update, Prod: validate
    show-sql: ${HIBERNATE_SHOW_SQL:false}

  jackson:
    default-property-inclusion: ${JACKSON_INCLUSION:non_null}
    time-zone: ${JACKSON_TIMEZONE:Asia/Tokyo}
```

```java
// Default values trong @ConfigurationProperties
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secret,

    @Positive
    @DefaultValue("3600000")  // 1 giờ
    long expirationMs,

    @Positive
    @DefaultValue("86400000")  // 1 ngày
    long refreshExpirationMs,

    @NotBlank
    @DefaultValue("medicalbox-api")
    String issuer
) {

  // Validation để cảnh báo nếu dùng default secret
  public JwtProperties {
    if ("default-dev-secret-change-in-production".equals(secret)) {
      log.warn("⚠️  CẢNH BÁO: Đang sử dụng JWT secret mặc định! " +
               "Hãy set JWT_SECRET environment variable cho production.");
    }
  }
}
```

```java
// Default values cho complex properties
@ConfigurationProperties(prefix = "app.file-upload")
@Validated
public record FileUploadProperties(
    @Positive
    @DefaultValue("10")
    int maxSizeMb,

    @NotEmpty
    List<String> allowedTypes,

    @NotBlank
    @DefaultValue("./uploads")
    String storagePath
) {

  // Constructor với defaults
  public FileUploadProperties {
    // Nếu allowedTypes null/empty, dùng default
    if (allowedTypes == null || allowedTypes.isEmpty()) {
      allowedTypes = List.of(
          "image/jpeg",
          "image/png",
          "application/pdf"
      );
    } else {
      allowedTypes = List.copyOf(allowedTypes);
    }
  }

  public static FileUploadProperties defaults() {
    return new FileUploadProperties(
        10,
        List.of("image/jpeg", "image/png", "application/pdf"),
        "./uploads"
    );
  }
}
```

```java
// Validation và warning cho production
@Component
@RequiredArgsConstructor
public class ConfigurationValidator implements ApplicationListener<ApplicationReadyEvent> {

  private final JwtProperties jwtProperties;
  private final Environment env;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Check production profile
    boolean isProduction = Arrays.asList(env.getActiveProfiles()).contains("prod");

    if (isProduction) {
      // Validate JWT secret không phải default
      if ("default-dev-secret-change-in-production".equals(jwtProperties.secret())) {
        errors.add("JWT_SECRET đang dùng giá trị mặc định trong production!");
      }

      // Validate expiration hợp lý
      if (jwtProperties.expirationMs() > 7200000) {  // > 2 giờ
        warnings.add("JWT expiration quá dài cho production: " +
                     jwtProperties.expirationMs() + "ms");
      }
    }

    // Log warnings
    if (!warnings.isEmpty()) {
      log.warn("⚠️  Configuration warnings:\n  - {}", String.join("\n  - ", warnings));
    }

    // Throw nếu có errors
    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "❌ Configuration errors:\n  - " + String.join("\n  - ", errors)
      );
    }
  }
}
```

```java
// Programmatic defaults với @ConditionalOnMissingBean
@Configuration
public class DefaultsConfig {

  @Bean
  @ConditionalOnMissingBean
  public FileUploadProperties fileUploadProperties() {
    return FileUploadProperties.defaults();
  }

  @Bean
  @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
  public CacheManager cacheManager() {
    // Default cache config nếu không có custom config
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(List.of(
        new ConcurrentMapCache("users"),
        new ConcurrentMapCache("sessions")
    ));
    return cacheManager;
  }
}
```

### ❌ Cách sai

```yaml
# ❌ Không có default values
app:
  jwt:
    secret: ${JWT_SECRET}  # ❌ Crash nếu không set
    expiration-ms: ${JWT_EXPIRATION_MS}  # ❌ Required

spring:
  datasource:
    url: ${DB_URL}  # ❌ Application không start nếu thiếu
    username: ${DB_USERNAME}  # ❌ Required
```

```java
// ❌ Không validate default values
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret  // ❌ Có thể null/empty
) {
  // ❌ Không warning khi dùng default không an toàn
}
```

```yaml
# ❌ Default values không hợp lý
app:
  jwt:
    expiration-ms: ${JWT_EXPIRATION_MS:999999999999}  # ❌ Quá dài (31 năm!)

  file-upload:
    max-size-mb: ${FILE_UPLOAD_MAX_SIZE_MB:9999}  # ❌ Quá lớn (9GB!)

  database:
    connection-pool:
      maximum-pool-size: ${DB_MAX_POOL_SIZE:1000}  # ❌ Quá nhiều connections
```

```java
// ❌ Hardcoded defaults trong code thay vì config
@Service
public class JwtTokenProvider {

  private static final long EXPIRATION = 3600000;  // ❌ Không configurable

  public String generateToken(UserDetails user) {
    return Jwts.builder()
        .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
        .compact();
  }
}
```

### Phát hiện tự động

```regex
# Tìm env vars không có default
\$\{[A-Z_]+\}(?!:)

# Tìm hardcoded values trong service code
private static final (long|int|String) [A-Z_]+ =

# Tìm unreasonable defaults
expiration.*:9{8,}  # Expiration quá dài
max.*size.*:9{4,}  # Size quá lớn
pool.*size.*:[5-9]\d{2,}  # Pool size > 500
```

### Checklist
- [ ] Mọi config property đều có default value
- [ ] Defaults an toàn cho development
- [ ] Defaults hợp lý cho production (hoặc warning)
- [ ] Sensitive configs (secret, password) có warning nếu dùng default
- [ ] Defaults documented trong README hoặc .env.example
- [ ] Validation cho unreasonable values
- [ ] Environment-specific defaults (dev vs prod)
- [ ] Factory method `defaults()` cho complex properties

---

## 13.06 Externalized config cho 12-factor app compliance 🟠

### Metadata
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Lý do chính:** Deploy flexibility, cloud-native compliance
- **Ảnh hưởng:** Dễ deploy, scale, maintain

### Tại sao?
1. **12-factor app:** Best practice cho cloud-native apps
2. **Flexibility:** Thay đổi config không cần rebuild
3. **Security:** Credentials không lưu trong code/image
4. **Cloud-ready:** Tương thích với Kubernetes, Docker, Cloud platforms

### ✅ Cách đúng

```yaml
# application.yml - Chỉ chứa structure, không chứa values
spring:
  application:
    name: ${APP_NAME:medicalbox-api}

  datasource:
    url: ${DATABASE_URL}  # Externalized
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: ${DATABASE_DRIVER:org.postgresql.Driver}

    hikari:
      maximum-pool-size: ${DATASOURCE_POOL_MAX:${HIKARI_MAXIMUM_POOL_SIZE:10}}
      minimum-idle: ${DATASOURCE_POOL_MIN:${HIKARI_MINIMUM_IDLE:5}}
      connection-timeout: ${DATASOURCE_CONNECTION_TIMEOUT:${HIKARI_CONNECTION_TIMEOUT:30000}}

  jpa:
    hibernate:
      ddl-auto: ${HIBERNATE_DDL_AUTO:validate}
    show-sql: ${HIBERNATE_SHOW_SQL:false}
    properties:
      hibernate:
        dialect: ${HIBERNATE_DIALECT:org.hibernate.dialect.PostgreSQLDialect}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:#{null}}
    ssl:
      enabled: ${REDIS_SSL_ENABLED:false}

  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: ${MAIL_SMTP_AUTH:true}
          starttls:
            enable: ${MAIL_SMTP_STARTTLS:true}

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: ${JWT_EXPIRATION_MS:3600000}

  aws:
    region: ${AWS_REGION:ap-northeast-1}
    s3:
      bucket: ${AWS_S3_BUCKET}
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}

  oauth2:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
```

```bash
# .env (local development)
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/medicalbox_dev
DATABASE_USERNAME=dev_user
DATABASE_PASSWORD=dev_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=local-dev-secret-key-min-256-bits
JWT_EXPIRATION_MS=3600000

# AWS
AWS_REGION=ap-northeast-1
AWS_S3_BUCKET=medicalbox-dev-uploads
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCY

# OAuth2
GOOGLE_CLIENT_ID=123456789.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxxxxx

# Mail
MAIL_USERNAME=noreply@medicalbox.jp
MAIL_PASSWORD=smtp-password-here
```

```dockerfile
# Dockerfile - Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Không copy config files - sẽ inject qua env vars
COPY --from=builder /app/target/*.jar app.jar

# Environment variables sẽ được inject khi runtime
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

```yaml
# docker-compose.yml - Development
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      # Inject từ .env file
      DATABASE_URL: jdbc:postgresql://db:5432/medicalbox
      DATABASE_USERNAME: postgres
      DATABASE_PASSWORD: postgres

      REDIS_HOST: redis
      REDIS_PORT: 6379

      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION_MS: 3600000

      SPRING_PROFILES_ACTIVE: dev
    env_file:
      - .env  # Load từ file
    depends_on:
      - db
      - redis

  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: medicalbox
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

```yaml
# kubernetes/deployment.yaml - Production
apiVersion: apps/v1
kind: Deployment
metadata:
  name: medicalbox-api
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: medicalbox/api:latest
        ports:
        - containerPort: 8080
        env:
        # Database config từ ConfigMap
        - name: DATABASE_URL
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: database.url

        # Secrets từ Kubernetes Secret
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password

        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jwt-secret
              key: secret

        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: access-key-id

        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: secret-access-key

        # Non-sensitive config
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JWT_EXPIRATION_MS
          value: "3600000"

        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  database.url: "jdbc:postgresql://postgres-service:5432/medicalbox"
  redis.host: "redis-service"
  redis.port: "6379"
---
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:
  username: "prod_user"
  password: "prod_secure_password"
---
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
type: Opaque
stringData:
  secret: "production-jwt-secret-min-256-bits"
```

```java
// Spring Cloud Config Server (optional - advanced)
// config-server/application.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: ${CONFIG_GIT_URI:https://github.com/org/config-repo}
          username: ${CONFIG_GIT_USERNAME}
          password: ${CONFIG_GIT_PASSWORD}
          default-label: main
          search-paths: '{application}'

// Client app
// bootstrap.yml
spring:
  application:
    name: medicalbox-api
  cloud:
    config:
      uri: ${CONFIG_SERVER_URI:http://localhost:8888}
      fail-fast: true
      retry:
        max-attempts: 6
```

```bash
# AWS Parameter Store (cloud-native)
aws ssm put-parameter \
  --name "/medicalbox/prod/database/url" \
  --value "jdbc:postgresql://..." \
  --type "String"

aws ssm put-parameter \
  --name "/medicalbox/prod/database/password" \
  --value "secure-password" \
  --type "SecureString"  # Encrypted
```

```java
// Read từ AWS Parameter Store
@Configuration
public class AwsParameterStoreConfig {

  @Bean
  public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
  }

  // Hoặc sử dụng Spring Cloud AWS
  // spring-cloud-starter-aws-parameter-store-config
}
```

### ❌ Cách sai

```yaml
# ❌ Hardcoded values trong application.yml (committed to Git)
spring:
  datasource:
    url: jdbc:postgresql://prod-db.example.com:5432/medicalbox  # ❌
    username: prod_user  # ❌
    password: SecretPassword123  # ❌ NGUY HIỂM

app:
  jwt:
    secret: hardcoded-jwt-secret-key  # ❌ Committed to Git

  aws:
    access-key: AKIAIOSFODNN7EXAMPLE  # ❌ Lộ credentials
```

```dockerfile
# ❌ Embed config trong Docker image
FROM eclipse-temurin:21-jre-alpine
COPY application-prod.yml /app/config/  # ❌ Baked into image
COPY app.jar /app/
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Problem: Phải rebuild image mỗi khi thay đổi config
```

```java
// ❌ Hardcoded config trong code
@Configuration
public class DatabaseConfig {

  @Bean
  public DataSource dataSource() {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl("jdbc:postgresql://localhost:5432/medicalbox");  // ❌
    ds.setUsername("prod_user");  // ❌
    ds.setPassword("password");  // ❌
    return ds;
  }
}
```

```yaml
# ❌ Environment-specific files committed
application-prod.yml  # ❌ Chứa production credentials, committed to Git
application-staging.yml  # ❌ Chứa staging credentials
```

### Phát hiện tự động

```regex
# Tìm hardcoded credentials trong YAML
password:\s*[^$][^\s]+
secret:\s*[^$][^\s]+
jdbc:postgresql://[^$]

# Tìm hardcoded trong Dockerfile
COPY.*application-prod\.yml
ENV DATABASE_PASSWORD=

# Tìm hardcoded trong code
\.setPassword\("
\.setUsername\("
```

### Checklist
- [ ] Tất cả configs externalized qua environment variables
- [ ] Không commit sensitive configs vào Git
- [ ] Dockerfile không chứa configs
- [ ] Sử dụng ConfigMap (K8s) hoặc .env (Docker Compose)
- [ ] Secrets quản lý riêng (K8s Secrets, AWS Secrets Manager)
- [ ] Config có thể override ở nhiều levels (default → env → runtime)
- [ ] Application chạy được trên bất kỳ environment nào
- [ ] Document tất cả required env vars trong README/.env.example

---

## 13.07 Config refresh runtime với @RefreshScope (Cloud Config) 🟡

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do chính:** Update config không cần restart app
- **Ảnh hưởng:** Zero-downtime config changes

### Tại sao?
1. **Zero-downtime:** Thay đổi config mà không restart app
2. **Flexibility:** A/B testing, feature flags runtime
3. **Dynamic configuration:** Thích ứng với thay đổi môi trường
4. **Cloud-native:** Best practice cho microservices

### ✅ Cách đúng

```xml
<!-- pom.xml - Dependencies -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2023.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

```yaml
# application.yml
spring:
  cloud:
    config:
      enabled: true
      uri: ${CONFIG_SERVER_URI:http://localhost:8888}
      fail-fast: false
      retry:
        max-attempts: 6
        initial-interval: 1000
        multiplier: 1.1

management:
  endpoints:
    web:
      exposure:
        include: refresh,health,info  # Expose /actuator/refresh endpoint
  endpoint:
    refresh:
      enabled: true
```

```java
// Refreshable configuration
@Configuration
@RefreshScope  // ✅ Bean sẽ được recreate khi refresh
public class DynamicConfig {

  @Value("${app.feature.new-ui-enabled:false}")
  private boolean newUiEnabled;

  @Value("${app.maintenance.mode:false}")
  private boolean maintenanceMode;

  @Value("${app.rate-limit.requests-per-minute:60}")
  private int rateLimitRequestsPerMinute;

  public boolean isNewUiEnabled() {
    return newUiEnabled;
  }

  public boolean isMaintenanceMode() {
    return maintenanceMode;
  }

  public int getRateLimitRequestsPerMinute() {
    return rateLimitRequestsPerMinute;
  }
}
```

```java
// Refreshable @ConfigurationProperties
@ConfigurationProperties(prefix = "app.feature")
@RefreshScope  // ✅ Refresh khi config thay đổi
@Validated
public record FeatureFlags(
    boolean newUiEnabled,
    boolean experimentalApiEnabled,
    boolean maintenanceMode,
    @Valid Map<String, Boolean> features
) {

  public FeatureFlags {
    features = features != null ? Map.copyOf(features) : Map.of();
  }

  public boolean isEnabled(String featureName) {
    return features.getOrDefault(featureName, false);
  }
}
```

```java
// Service sử dụng refreshable config
@Service
@RequiredArgsConstructor
public class FeatureToggleService {

  private final FeatureFlags featureFlags;  // RefreshScope bean

  public boolean isFeatureEnabled(String featureName) {
    // Giá trị này sẽ update khi gọi /actuator/refresh
    return featureFlags.isEnabled(featureName);
  }

  public boolean isMaintenanceMode() {
    return featureFlags.maintenanceMode();
  }
}
```

```java
// Controller với maintenance mode check
@RestController
@RequiredArgsConstructor
public class ApiController {

  private final FeatureToggleService featureToggleService;

  @GetMapping("/api/patients")
  public ResponseEntity<?> getPatients() {
    // Check maintenance mode (refreshable runtime)
    if (featureToggleService.isMaintenanceMode()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of(
              "error", "Service is under maintenance",
              "message", "Hệ thống đang bảo trì, vui lòng thử lại sau"
          ));
    }

    // Normal logic
    return ResponseEntity.ok(patientService.findAll());
  }
}
```

```yaml
# Config Server - Git repository
# config-repo/medicalbox-api.yml
app:
  feature:
    new-ui-enabled: false
    experimental-api-enabled: false
    maintenance-mode: false
    features:
      patient-video-call: true
      doctor-schedule-optimization: false
      ai-diagnosis-assistant: false

# config-repo/medicalbox-api-prod.yml
app:
  feature:
    new-ui-enabled: true
    experimental-api-enabled: false
    maintenance-mode: false  # ← Có thể thay đổi thành true runtime
```

```bash
# Refresh config runtime (không cần restart)
# Cách 1: Gọi actuator endpoint
curl -X POST http://localhost:8080/actuator/refresh

# Response:
# ["app.feature.maintenance-mode", "app.feature.new-ui-enabled"]

# Cách 2: Spring Cloud Bus (broadcast refresh to all instances)
curl -X POST http://localhost:8080/actuator/bus-refresh

# Cách 3: Webhooks từ Git repository
# Khi push config changes → auto trigger refresh
```

```java
// Advanced: Config change event listener
@Component
@Slf4j
public class ConfigChangeListener {

  @EventListener
  public void handleRefresh(RefreshScopeRefreshedEvent event) {
    log.info("Configuration refreshed: {}", event.getName());

    // Custom logic khi config thay đổi
    // VD: clear cache, update rate limiter, etc.
  }
}
```

```java
// Spring Cloud Bus cho distributed refresh
@Configuration
@EnableConfigServer  // Config Server
public class ConfigServerConfig {
  // Config Server sẽ push changes đến tất cả instances qua message bus
}

// Client app
@SpringBootApplication
@EnableDiscoveryClient
public class MedicalboxApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(MedicalboxApiApplication.class, args);
  }
}
```

```yaml
# Spring Cloud Bus với RabbitMQ/Kafka
spring:
  cloud:
    bus:
      enabled: true
      refresh:
        enabled: true
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}

# Khi gọi /actuator/bus-refresh → broadcast đến tất cả instances
```

### ❌ Cách sai

```java
// ❌ Không có @RefreshScope - config không update runtime
@Configuration
public class FeatureConfig {  // ❌ Missing @RefreshScope

  @Value("${app.feature.new-ui-enabled}")
  private boolean newUiEnabled;  // ❌ Giá trị không bao giờ thay đổi

  public boolean isNewUiEnabled() {
    return newUiEnabled;  // ❌ Stale value
  }
}
```

```java
// ❌ Cache giá trị config (không refresh được)
@Service
public class FeatureService {

  private final boolean newUiEnabled;  // ❌ Final - không thể refresh

  public FeatureService(@Value("${app.feature.new-ui-enabled}") boolean newUiEnabled) {
    this.newUiEnabled = newUiEnabled;  // ❌ Cached khi khởi tạo
  }

  public boolean isNewUiEnabled() {
    return newUiEnabled;  // ❌ Luôn trả về giá trị cũ
  }
}
```

```yaml
# ❌ Không expose refresh endpoint
management:
  endpoints:
    web:
      exposure:
        include: health,info  # ❌ Missing 'refresh'
```

```java
// ❌ Sử dụng static configuration
public class FeatureFlags {

  public static final boolean NEW_UI_ENABLED = true;  // ❌ Static - không refresh
}
```

### Phát hiện tự động

```regex
# Tìm config beans không có @RefreshScope
@Value\s*\(.*\$\{(?!.*@RefreshScope)

# Tìm @ConfigurationProperties không có @RefreshScope
@ConfigurationProperties(?!.*@RefreshScope)

# Tìm static final config
public static final.*=.*true|false
```

### Checklist
- [ ] Có dependency `spring-cloud-starter-config`
- [ ] Có dependency `spring-boot-starter-actuator`
- [ ] Config beans có `@RefreshScope`
- [ ] Expose `/actuator/refresh` endpoint
- [ ] Test refresh bằng cách gọi actuator endpoint
- [ ] Document cách refresh config trong README
- [ ] Consider Spring Cloud Bus cho distributed systems
- [ ] Config Server setup (nếu dùng centralized config)

---

## 13.08 Tách config theo concern (db, security, cache, messaging) 🟡

### Metadata
- **Mức độ:** 🟡 NÊN CÓ
- **Lý do chính:** Organization, maintainability, separation of concerns
- **Ảnh hưởng:** Dễ tìm, dễ maintain, modular

### Tại sao?
1. **Separation of concerns:** Mỗi concern có config riêng
2. **Maintainability:** Dễ tìm và sửa config liên quan
3. **Modularity:** Enable/disable features dễ dàng
4. **Team collaboration:** Mỗi team quản lý config của mình

### ✅ Cách đúng

```
config/
├── application.yml              # Main config, imports
├── application-dev.yml          # Dev overrides
├── application-prod.yml         # Prod overrides
├── database.yml                 # Database config
├── security.yml                 # Security, JWT, OAuth2
├── cache.yml                    # Redis, Caffeine cache
├── messaging.yml                # Kafka, RabbitMQ
├── monitoring.yml               # Actuator, metrics, logging
├── integration.yml              # External APIs (AWS, Google, etc.)
└── feature-flags.yml            # Feature toggles
```

```yaml
# application.yml - Main entry point
spring:
  application:
    name: medicalbox-api
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # Import từ các files khác
  config:
    import:
      - optional:classpath:database.yml
      - optional:classpath:security.yml
      - optional:classpath:cache.yml
      - optional:classpath:messaging.yml
      - optional:classpath:monitoring.yml
      - optional:classpath:integration.yml
      - optional:classpath:feature-flags.yml

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful
```

```yaml
# database.yml - Tất cả database configs
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/medicalbox_dev}
    username: ${DATABASE_USERNAME:dev_user}
    password: ${DATABASE_PASSWORD:dev_password}
    driver-class-name: ${DATABASE_DRIVER:org.postgresql.Driver}

    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:10}
      minimum-idle: ${HIKARI_MIN_IDLE:5}
      connection-timeout: ${HIKARI_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${HIKARI_IDLE_TIMEOUT:600000}
      max-lifetime: ${HIKARI_MAX_LIFETIME:1800000}
      pool-name: MedicalboxHikariPool
      auto-commit: true
      leak-detection-threshold: ${HIKARI_LEAK_DETECTION:60000}

  jpa:
    hibernate:
      ddl-auto: ${HIBERNATE_DDL_AUTO:validate}
    show-sql: ${HIBERNATE_SHOW_SQL:false}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
    open-in-view: false

  flyway:
    enabled: ${FLYWAY_ENABLED:true}
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

```yaml
# security.yml - Security, JWT, OAuth2, CORS
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration-ms: ${JWT_EXPIRATION_MS:3600000}
      refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION_MS:86400000}
      issuer: ${JWT_ISSUER:medicalbox-api}

    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
      allowed-methods: ${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,PATCH,OPTIONS}
      allowed-headers: ${CORS_ALLOWED_HEADERS:*}
      allow-credentials: ${CORS_ALLOW_CREDENTIALS:true}
      max-age-seconds: ${CORS_MAX_AGE:3600}

    oauth2:
      google:
        client-id: ${GOOGLE_CLIENT_ID}
        client-secret: ${GOOGLE_CLIENT_SECRET}
        redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:8080/oauth2/callback/google}
        scope: ${GOOGLE_SCOPE:profile,email}

      facebook:
        client-id: ${FACEBOOK_CLIENT_ID}
        client-secret: ${FACEBOOK_CLIENT_SECRET}
        redirect-uri: ${FACEBOOK_REDIRECT_URI:http://localhost:8080/oauth2/callback/facebook}

    rate-limit:
      enabled: ${RATE_LIMIT_ENABLED:true}
      requests-per-minute: ${RATE_LIMIT_RPM:60}
      burst-capacity: ${RATE_LIMIT_BURST:100}
```

```yaml
# cache.yml - Redis, Caffeine caching
spring:
  cache:
    type: ${CACHE_TYPE:redis}  # redis, caffeine, none
    cache-names:
      - users
      - sessions
      - doctors
      - clinics

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:#{null}}
      database: ${REDIS_DATABASE:0}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}
      timeout: ${REDIS_TIMEOUT:2000}
      lettuce:
        pool:
          max-active: ${REDIS_POOL_MAX_ACTIVE:8}
          max-idle: ${REDIS_POOL_MAX_IDLE:8}
          min-idle: ${REDIS_POOL_MIN_IDLE:0}
          max-wait: ${REDIS_POOL_MAX_WAIT:1000}

app:
  cache:
    redis:
      ttl:
        users: ${CACHE_TTL_USERS:3600}  # 1 giờ
        sessions: ${CACHE_TTL_SESSIONS:1800}  # 30 phút
        doctors: ${CACHE_TTL_DOCTORS:7200}  # 2 giờ

    caffeine:
      spec:
        users: "maximumSize=1000,expireAfterWrite=1h"
        sessions: "maximumSize=5000,expireAfterWrite=30m"
```

```yaml
# messaging.yml - Kafka, RabbitMQ, Email
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: ${KAFKA_PRODUCER_ACKS:all}
      retries: ${KAFKA_PRODUCER_RETRIES:3}
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: ${KAFKA_CONSUMER_GROUP:medicalbox-api}
      auto-offset-reset: ${KAFKA_AUTO_OFFSET_RESET:earliest}

  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: ${MAIL_SMTP_AUTH:true}
          starttls:
            enable: ${MAIL_SMTP_STARTTLS:true}
          connectiontimeout: ${MAIL_TIMEOUT:5000}
          timeout: ${MAIL_TIMEOUT:5000}
          writetimeout: ${MAIL_TIMEOUT:5000}

app:
  messaging:
    kafka:
      topics:
        appointment-created: appointment.created
        appointment-updated: appointment.updated
        notification-send: notification.send

    mail:
      from: ${MAIL_FROM:noreply@medicalbox.jp}
      templates-path: ${MAIL_TEMPLATES_PATH:classpath:/templates/mail}
```

```yaml
# monitoring.yml - Actuator, metrics, logging
management:
  endpoints:
    web:
      exposure:
        include: ${ACTUATOR_ENDPOINTS:health,info,metrics,prometheus}
      base-path: /actuator

  endpoint:
    health:
      show-details: ${HEALTH_SHOW_DETAILS:when-authorized}
      probes:
        enabled: true  # Kubernetes liveness/readiness

  metrics:
    export:
      prometheus:
        enabled: ${PROMETHEUS_ENABLED:true}
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:dev}

  health:
    redis:
      enabled: ${HEALTH_REDIS_ENABLED:true}
    db:
      enabled: true

logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    jp.medicalbox: ${LOG_LEVEL_APP:DEBUG}
    org.springframework.web: ${LOG_LEVEL_SPRING_WEB:INFO}
    org.hibernate.SQL: ${LOG_LEVEL_HIBERNATE_SQL:DEBUG}
    org.hibernate.type.descriptor.sql.BasicBinder: ${LOG_LEVEL_HIBERNATE_BINDER:TRACE}

  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

  file:
    name: ${LOG_FILE_NAME:logs/medicalbox-api.log}
    max-size: ${LOG_FILE_MAX_SIZE:10MB}
    max-history: ${LOG_FILE_MAX_HISTORY:30}
```

```yaml
# integration.yml - External APIs
app:
  integration:
    aws:
      region: ${AWS_REGION:ap-northeast-1}
      s3:
        bucket: ${AWS_S3_BUCKET}
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}

      ses:
        enabled: ${AWS_SES_ENABLED:false}
        from-email: ${AWS_SES_FROM:noreply@medicalbox.jp}

    google:
      maps:
        api-key: ${GOOGLE_MAPS_API_KEY}

      calendar:
        enabled: ${GOOGLE_CALENDAR_ENABLED:false}

    stripe:
      enabled: ${STRIPE_ENABLED:false}
      public-key: ${STRIPE_PUBLIC_KEY}
      secret-key: ${STRIPE_SECRET_KEY}
      webhook-secret: ${STRIPE_WEBHOOK_SECRET}
```

```yaml
# feature-flags.yml - Feature toggles
app:
  features:
    new-ui:
      enabled: ${FEATURE_NEW_UI:false}

    video-call:
      enabled: ${FEATURE_VIDEO_CALL:true}
      max-participants: ${FEATURE_VIDEO_CALL_MAX_PARTICIPANTS:4}

    ai-diagnosis:
      enabled: ${FEATURE_AI_DIAGNOSIS:false}
      confidence-threshold: ${FEATURE_AI_CONFIDENCE:0.85}

    payment:
      enabled: ${FEATURE_PAYMENT:true}
      providers:
        stripe: ${FEATURE_PAYMENT_STRIPE:true}
        paypal: ${FEATURE_PAYMENT_PAYPAL:false}

    maintenance-mode:
      enabled: ${MAINTENANCE_MODE:false}
      message: ${MAINTENANCE_MESSAGE:システムメンテナンス中です}
```

```java
// Tương ứng configuration classes
@ConfigurationProperties(prefix = "app.security")
@Validated
public record SecurityProperties(
    @Valid JwtProperties jwt,
    @Valid CorsProperties cors,
    @Valid OAuth2Properties oauth2,
    @Valid RateLimitProperties rateLimit
) {
  public record JwtProperties(...) {}
  public record CorsProperties(...) {}
  public record OAuth2Properties(...) {}
  public record RateLimitProperties(...) {}
}

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(...) {}

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(...) {}

@ConfigurationProperties(prefix = "app.integration")
public record IntegrationProperties(...) {}

@ConfigurationProperties(prefix = "app.features")
@RefreshScope  // Runtime refresh
public record FeatureProperties(...) {}
```

### ❌ Cách sai

```yaml
# ❌ Tất cả config trong một file khổng lồ
spring:
  datasource:
    url: ...
    hikari:
      ...
  jpa:
    ...
  cache:
    ...
  kafka:
    ...
  mail:
    ...
  security:
    ...

app:
  jwt:
    ...
  cors:
    ...
  oauth2:
    ...
  aws:
    ...
  features:
    ...
  # 500+ lines trong một file ❌
```

```yaml
# ❌ Tách file nhưng không logical
config1.yml  # ❌ Tên không rõ nghĩa
config2.yml
random-settings.yml
misc.yml
```

```java
// ❌ Một @ConfigurationProperties class chứa tất cả
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    JwtProperties jwt,
    CorsProperties cors,
    CacheProperties cache,
    MessagingProperties messaging,
    IntegrationProperties integration,
    FeatureProperties features
    // ❌ Quá nhiều concerns trong một class
) {}
```

### Phát hiện tự động

```regex
# Tìm file config quá lớn (>300 lines)
wc -l application.yml | awk '$1 > 300'

# Tìm config không có spring.config.import
grep -L "spring.config.import" application.yml
```

### Checklist
- [ ] Config files tách theo concerns (database, security, cache, etc.)
- [ ] Main `application.yml` import các files con
- [ ] Mỗi file < 200 lines
- [ ] File names rõ ràng (database.yml, security.yml, etc.)
- [ ] Mỗi concern có corresponding `@ConfigurationProperties`
- [ ] Environment-specific overrides (application-dev.yml, etc.)
- [ ] Document structure trong README
- [ ] Team members biết tìm config ở đâu

---

## Summary Checklist

### 🔴 BẮT BUỘC
- [ ] 13.01: Có profile-based config (application-{profile}.yml)
- [ ] 13.02: Sensitive data qua env vars, không commit

### 🟠 KHUYẾN NGHỊ
- [ ] 13.03: @ConfigurationProperties với @Validated
- [ ] 13.04: Immutable configuration (record hoặc final)
- [ ] 13.06: Externalized config (12-factor compliance)

### 🟡 NÊN CÓ
- [ ] 13.05: Default values hợp lý
- [ ] 13.07: Config refresh runtime (@RefreshScope)
- [ ] 13.08: Tách config theo concerns

---

## Quick Reference

### Profile activation
```bash
# Environment variable
export SPRING_PROFILES_ACTIVE=prod

# Command line
java -jar app.jar --spring.profiles.active=prod

# Docker
docker run -e SPRING_PROFILES_ACTIVE=prod app:latest
```

### Refresh config runtime
```bash
# Single instance
curl -X POST http://localhost:8080/actuator/refresh

# All instances (with Spring Cloud Bus)
curl -X POST http://localhost:8080/actuator/bus-refresh
```

### Validation annotations
```java
@NotBlank, @NotEmpty, @NotNull
@Positive, @PositiveOrZero, @Min, @Max
@Size(min=, max=), @Pattern(regexp=)
@Email, @URL
@Valid  // Nested validation
```
