# Domain 01: Cấu Trúc Dự Án (Project Structure)
> **Số practices:** 9 | 🔴 2 | 🟠 3 | 🟡 4
> **Trọng số:** ×1

## 01.01 — Package theo feature/domain, không theo layer

### Metadata
- **Mã số:** 01.01
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** package-structure, domain-driven-design, modularity

### Tại sao?
Tổ chức package theo feature/domain giúp code có tính cohesion cao, dễ tìm kiếm và bảo trì. Khi một feature thay đổi, tất cả code liên quan nằm trong cùng một package thay vì phải tìm kiếm qua nhiều layer (controller, service, repository). Cách này cũng tạo điều kiện tốt cho việc tách module sau này và áp dụng Domain-Driven Design.

### ✅ Cách đúng
```java
// Cấu trúc theo feature/domain
jp.medicalbox
├── auth
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── AuthRepository.java
│   ├── dto
│   │   ├── LoginRequest.java
│   │   └── LoginResponse.java
│   └── entity
│       └── UserSession.java
├── appointment
│   ├── AppointmentController.java
│   ├── AppointmentService.java
│   ├── AppointmentRepository.java
│   ├── dto
│   │   ├── CreateAppointmentRequest.java
│   │   └── AppointmentResponse.java
│   └── entity
│       └── Appointment.java
└── doctor
    ├── DoctorController.java
    ├── DoctorService.java
    ├── DoctorRepository.java
    └── entity
        └── Doctor.java
```

### ❌ Cách sai
```java
// Cấu trúc theo layer (anti-pattern)
jp.medicalbox
├── controller
│   ├── AuthController.java
│   ├── AppointmentController.java
│   └── DoctorController.java
├── service
│   ├── AuthService.java
│   ├── AppointmentService.java
│   └── DoctorService.java
├── repository
│   ├── AuthRepository.java
│   ├── AppointmentRepository.java
│   └── DoctorRepository.java
└── entity
    ├── UserSession.java
    ├── Appointment.java
    └── Doctor.java
```

### Phát hiện
```
# Phát hiện cấu trúc layer-first (có folder controller/service/repository ở root)
src/main/java/.*/controller/.*Controller\.java
src/main/java/.*/service/.*Service\.java
src/main/java/.*/repository/.*Repository\.java
```

### Checklist
- [ ] Mỗi feature có package riêng chứa tất cả các layer liên quan
- [ ] Các class liên quan đến cùng business logic nằm gần nhau
- [ ] Không có package controller/service/repository ở root level
- [ ] Package name phản ánh business domain, không phải technical layer

## 01.02 — Tách module Maven/Gradle cho microservices

### Metadata
- **Mã số:** 01.02
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** multi-module, microservices, scalability

### Tại sao?
Tách module giúp quản lý dependencies tốt hơn, tránh circular dependency, và tạo điều kiện cho việc deploy độc lập các service. Mỗi module có thể có version riêng, dependencies riêng, và có thể được build/test/deploy độc lập. Điều này đặc biệt quan trọng khi dự án lớn lên hoặc chuyển sang kiến trúc microservices.

### ✅ Cách đúng
```xml
<!-- pom.xml (root) -->
<project>
  <groupId>jp.medicalbox</groupId>
  <artifactId>medicalbox-parent</artifactId>
  <packaging>pom</packaging>

  <modules>
    <module>medicalbox-common</module>
    <module>medicalbox-auth</module>
    <module>medicalbox-appointment</module>
    <module>medicalbox-notification</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>3.2.0</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

```xml
<!-- medicalbox-auth/pom.xml -->
<project>
  <parent>
    <groupId>jp.medicalbox</groupId>
    <artifactId>medicalbox-parent</artifactId>
    <version>1.0.0</version>
  </parent>

  <artifactId>medicalbox-auth</artifactId>

  <dependencies>
    <dependency>
      <groupId>jp.medicalbox</groupId>
      <artifactId>medicalbox-common</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
</project>
```

### ❌ Cách sai
```xml
<!-- Monolith - tất cả code trong 1 module duy nhất -->
<project>
  <groupId>jp.medicalbox</groupId>
  <artifactId>medicalbox-api</artifactId>
  <packaging>jar</packaging>

  <!-- Không có modules, tất cả code trong src/main/java -->
  <dependencies>
    <!-- Tất cả dependencies cho toàn bộ ứng dụng -->
  </dependencies>
</project>
```

### Phát hiện
```
# Phát hiện project chỉ có 1 pom.xml duy nhất (không có multi-module)
^pom\.xml$ # Chỉ có 1 file pom.xml ở root, không có subfolder
```

### Checklist
- [ ] Có pom.xml ở root với packaging=pom
- [ ] Mỗi service/module có thư mục và pom.xml riêng
- [ ] Module common chứa code dùng chung
- [ ] dependencyManagement được định nghĩa ở parent pom

## 01.03 — Đặt tên package theo chuẩn Java (lowercase, reverse domain)

### Metadata
- **Mã số:** 01.03
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** naming-convention, java-standard

### Tại sao?
Chuẩn đặt tên package theo reverse domain name (ví dụ: com.company.product) giúp tránh xung đột tên package giữa các tổ chức khác nhau. Sử dụng lowercase giúp dễ đọc và tuân thủ Java naming convention. Tên package rõ ràng giúp developer hiểu được tổ chức code và ownership.

### ✅ Cách đúng
```java
// Package name: lowercase, reverse domain, phân cấp rõ ràng
package jp.medicalbox.appointment.service;

import jp.medicalbox.common.exception.BusinessException;
import jp.medicalbox.appointment.dto.CreateAppointmentRequest;
import jp.medicalbox.appointment.entity.Appointment;

public class AppointmentService {
  // Implementation
}
```

### ❌ Cách sai
```java
// Sai: Có chữ hoa trong package name
package jp.MedicalBox.Appointment.Service;

// Sai: Không theo reverse domain
package appointment.service;

// Sai: Tên package quá chung chung
package com.app.service;

// Sai: Sử dụng từ khóa Java
package jp.medicalbox.class.interface;
```

### Phát hiện
```
# Phát hiện package name có chữ hoa
^package\s+.*[A-Z].*; # Package name chứa ký tự in hoa
# Phát hiện package name không bắt đầu bằng domain
^package\s+(?!jp\.|com\.|org\.|net\.) # Không bắt đầu bằng domain chuẩn
```

### Checklist
- [ ] Package name toàn bộ lowercase
- [ ] Bắt đầu bằng reverse domain (jp.medicalbox)
- [ ] Không sử dụng từ khóa Java (class, interface, etc.)
- [ ] Tên package phản ánh business domain rõ ràng

## 01.04 — Tách configuration classes riêng biệt

### Metadata
- **Mã số:** 01.04
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** configuration, separation-of-concerns, maintainability

### Tại sao?
Tách configuration classes giúp dễ tìm kiếm và quản lý cấu hình của từng component (database, security, cache, etc.). Khi cần thay đổi cấu hình, developer biết chính xác file nào cần sửa. Cấu trúc rõ ràng cũng giúp tránh conflict khi nhiều người cùng làm việc trên dự án.

### ✅ Cách đúng
```java
// config/DatabaseConfig.java
package jp.medicalbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
  // Database-specific configuration
}

// config/SecurityConfig.java
package jp.medicalbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .anyRequest().authenticated()
      )
      .build();
  }
}

// config/CacheConfig.java
package jp.medicalbox.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
  // Cache-specific configuration
}
```

### ❌ Cách sai
```java
// ApplicationConfig.java - Tất cả config trong 1 file
@Configuration
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class ApplicationConfig {

  // Database beans
  @Bean
  public DataSource dataSource() { /*...*/ }

  // Security beans
  @Bean
  public SecurityFilterChain securityFilterChain() { /*...*/ }

  // Cache beans
  @Bean
  public CacheManager cacheManager() { /*...*/ }

  // 500+ dòng config trong 1 file
}
```

### Phát hiện
```
# Phát hiện configuration file quá lớn (>200 dòng)
@Configuration.*\n(.*\n){200,} # File có @Configuration và >200 dòng
# Phát hiện nhiều @Enable* annotation trong cùng 1 file
@Configuration.*@Enable.*@Enable.*@Enable # >2 @Enable annotation
```

### Checklist
- [ ] Mỗi concern có configuration class riêng (Database, Security, Cache, etc.)
- [ ] Configuration classes nằm trong package config
- [ ] Mỗi file config <200 dòng
- [ ] Tên file phản ánh rõ mục đích (DatabaseConfig, SecurityConfig)

## 01.05 — Sử dụng @ConfigurationProperties thay @Value

### Metadata
- **Mã số:** 01.05
- **Mức độ:** 🟠 KHUYẾN NGHỊ
- **Điểm trừ:** -5
- **Tags:** configuration, type-safety, validation

### Tại sao?
@ConfigurationProperties cung cấp type-safe configuration với validation tự động, autocomplete trong IDE, và dễ test hơn @Value. Nó cho phép group các properties liên quan vào một class, giúp code dễ đọc và bảo trì. @Value chỉ nên dùng cho các giá trị đơn lẻ, không liên quan.

### ✅ Cách đúng
```java
// AppProperties.java
package jp.medicalbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

  @NotBlank
  private String name;

  private Security security = new Security();
  private Database database = new Database();

  public static class Security {
    @NotBlank
    private String jwtSecret;

    @Min(3600)
    private int jwtExpirationSeconds;

    // Getters and setters
  }

  public static class Database {
    @Min(1)
    private int maxPoolSize = 10;

    @Min(1000)
    private int connectionTimeout = 30000;

    // Getters and setters
  }

  // Getters and setters
}

// Application.java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}

// Service sử dụng
@Service
public class AuthService {
  private final AppProperties appProperties;

  public AuthService(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  public String generateToken() {
    String secret = appProperties.getSecurity().getJwtSecret();
    // Use secret
  }
}
```

### ❌ Cách sai
```java
// Sử dụng @Value cho nhiều properties liên quan
@Service
public class AuthService {

  @Value("${app.security.jwt-secret}")
  private String jwtSecret;

  @Value("${app.security.jwt-expiration-seconds}")
  private int jwtExpirationSeconds;

  @Value("${app.security.refresh-token-expiration-days}")
  private int refreshTokenExpirationDays;

  @Value("${app.security.password-min-length}")
  private int passwordMinLength;

  // Không có validation, không có type-safety
  // Khó test vì phải mock Spring environment
}
```

### Phát hiện
```
# Phát hiện nhiều @Value trong cùng 1 class (>3)
@Value.*\n.*@Value.*\n.*@Value.*\n.*@Value # >=4 @Value annotation
# Phát hiện @Value với prefix giống nhau
@Value\("\$\{app\.security\..*@Value\("\$\{app\.security\. # Cùng prefix
```

### Checklist
- [ ] Sử dụng @ConfigurationProperties cho nhóm properties liên quan
- [ ] Có validation constraints (@NotBlank, @Min, etc.)
- [ ] Enable @ConfigurationProperties trong main class
- [ ] @Value chỉ dùng cho properties đơn lẻ, không liên quan

## 01.06 — File application.yml theo profile (dev/staging/prod)

### Metadata
- **Mã số:** 01.06
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** configuration, environment, deployment

### Tại sao?
Tách cấu hình theo môi trường giúp tránh nhầm lẫn giữa dev/staging/prod, giảm rủi ro deploy nhầm config. Mỗi môi trường có yêu cầu khác nhau về database, logging level, security, v.v. Spring Profile giúp quản lý cấu hình này một cách an toàn và rõ ràng.

### ✅ Cách đúng
```yaml
# application.yml (common configuration)
spring:
  application:
    name: medicalbox-api
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

app:
  name: Medical Box API

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
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

---
# application-staging.yml
spring:
  config:
    activate:
      on-profile: staging
  datasource:
    url: jdbc:postgresql://staging-db.example.com:5432/medicalbox_staging
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

logging:
  level:
    jp.medicalbox: INFO

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:postgresql://prod-db.example.com:5432/medicalbox_prod
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false

logging:
  level:
    jp.medicalbox: WARN
```

### ❌ Cách sai
```yaml
# application.yml - Tất cả config trong 1 file, không có profile
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medicalbox # Hardcoded cho dev
    username: postgres
    password: postgres123 # Password trong code
  jpa:
    show-sql: true # Luôn bật trong mọi môi trường

# Không có cách nào switch giữa dev/staging/prod
```

### Phát hiện
```
# Phát hiện không có application-{profile}.yml
ls src/main/resources/application-*.yml # Không có file nào
# Phát hiện hardcoded password trong application.yml
password:\s*[^$\{] # Password không dùng environment variable
```

### Checklist
- [ ] Có ít nhất 3 profile files (dev, staging, prod)
- [ ] application.yml chỉ chứa config chung
- [ ] Mỗi profile có datasource riêng
- [ ] Production không có show-sql: true hoặc ddl-auto: create-drop
- [ ] Sensitive data dùng environment variables

## 01.07 — Không hardcode giá trị cấu hình trong code

### Metadata
- **Mã số:** 01.07
- **Mức độ:** 🔴 BẮT BUỘC
- **Điểm trừ:** -10
- **Tags:** configuration, security, maintainability

### Tại sao?
Hardcode values gây khó khăn khi cần thay đổi cấu hình giữa các môi trường, tạo rủi ro bảo mật khi commit secrets vào git, và khó bảo trì khi cần update giá trị. Tất cả configuration nên được externalize vào application.yml hoặc environment variables.

### ✅ Cách đúng
```java
// application.yml
app:
  business:
    max-appointments-per-day: 20
    appointment-duration-minutes: 30
    cancellation-deadline-hours: 24

// AppointmentService.java
@Service
public class AppointmentService {
  private final AppProperties appProperties;

  public AppointmentService(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  public boolean canCreateAppointment(Doctor doctor, LocalDate date) {
    int maxPerDay = appProperties.getBusiness().getMaxAppointmentsPerDay();
    long currentCount = appointmentRepository.countByDoctorAndDate(doctor, date);
    return currentCount < maxPerDay;
  }

  public boolean canCancelAppointment(Appointment appointment) {
    long hoursUntil = ChronoUnit.HOURS.between(LocalDateTime.now(), appointment.getStartTime());
    int deadline = appProperties.getBusiness().getCancellationDeadlineHours();
    return hoursUntil >= deadline;
  }
}
```

### ❌ Cách sai
```java
// Hardcode business logic values
@Service
public class AppointmentService {

  public boolean canCreateAppointment(Doctor doctor, LocalDate date) {
    long currentCount = appointmentRepository.countByDoctorAndDate(doctor, date);
    return currentCount < 20; // Magic number - hardcoded!
  }

  public boolean canCancelAppointment(Appointment appointment) {
    long hoursUntil = ChronoUnit.HOURS.between(LocalDateTime.now(), appointment.getStartTime());
    return hoursUntil >= 24; // Magic number - hardcoded!
  }

  public void sendNotification(User user) {
    String apiKey = "sk-1234567890abcdef"; // Secret key hardcoded!
    String apiUrl = "https://api.example.com/notify"; // URL hardcoded!
    // Send notification
  }
}
```

### Phát hiện
```
# Phát hiện magic numbers trong business logic
return.*<\s*\d{2,}; # So sánh với số >10
return.*>\s*\d{2,}; # So sánh với số >10
# Phát hiện hardcoded URLs
String.*=\s*"https?:// # URL trong string literal
# Phát hiện hardcoded API keys (pattern: sk-, api-, key-)
String.*=\s*"(sk-|api-|key-|secret-) # Potential API key
```

### Checklist
- [ ] Không có magic numbers trong business logic (số >10)
- [ ] Không có hardcoded URLs, API endpoints
- [ ] Không có hardcoded secrets, API keys, passwords
- [ ] Tất cả config values nằm trong application.yml hoặc @ConfigurationProperties

## 01.08 — Giới hạn kích thước file (<500 dòng)

### Metadata
- **Mã số:** 01.08
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** maintainability, readability, single-responsibility

### Tại sao?
File quá dài (>500 dòng) thường vi phạm Single Responsibility Principle, khó đọc, khó review, và khó test. Việc tách nhỏ file giúp mỗi class/file có trách nhiệm rõ ràng, dễ hiểu và bảo trì. Nếu một file quá dài, đó là dấu hiệu cần refactor.

### ✅ Cách đúng
```java
// AppointmentService.java (200 dòng)
@Service
public class AppointmentService {
  private final AppointmentRepository appointmentRepository;
  private final AppointmentValidator appointmentValidator;
  private final AppointmentNotifier appointmentNotifier;

  public Appointment createAppointment(CreateAppointmentRequest request) {
    appointmentValidator.validate(request);

    Appointment appointment = Appointment.builder()
      .doctorId(request.getDoctorId())
      .patientId(request.getPatientId())
      .startTime(request.getStartTime())
      .build();

    Appointment saved = appointmentRepository.save(appointment);
    appointmentNotifier.notifyCreated(saved);

    return saved;
  }
}

// AppointmentValidator.java (150 dòng) - Tách validation logic
@Component
public class AppointmentValidator {
  private final AppProperties appProperties;
  private final AppointmentRepository appointmentRepository;

  public void validate(CreateAppointmentRequest request) {
    validateTime(request.getStartTime());
    validateDoctorAvailability(request.getDoctorId(), request.getStartTime());
    validatePatientLimit(request.getPatientId());
  }

  private void validateTime(LocalDateTime startTime) { /*...*/ }
  private void validateDoctorAvailability(Long doctorId, LocalDateTime startTime) { /*...*/ }
  private void validatePatientLimit(Long patientId) { /*...*/ }
}

// AppointmentNotifier.java (100 dòng) - Tách notification logic
@Component
public class AppointmentNotifier {
  private final EmailService emailService;
  private final SmsService smsService;

  public void notifyCreated(Appointment appointment) { /*...*/ }
  public void notifyCancelled(Appointment appointment) { /*...*/ }
}
```

### ❌ Cách sai
```java
// AppointmentService.java (1200+ dòng) - God class
@Service
public class AppointmentService {
  // 100 dòng dependencies và initialization

  // 200 dòng CRUD operations
  public Appointment createAppointment() { /*...*/ }
  public Appointment updateAppointment() { /*...*/ }
  public void deleteAppointment() { /*...*/ }

  // 300 dòng validation logic
  private void validateAppointmentTime() { /*...*/ }
  private void validateDoctorSchedule() { /*...*/ }
  private void validatePatientEligibility() { /*...*/ }
  private void validateInsurance() { /*...*/ }

  // 200 dòng notification logic
  private void sendEmailNotification() { /*...*/ }
  private void sendSmsNotification() { /*...*/ }
  private void sendPushNotification() { /*...*/ }

  // 200 dòng business rules
  private boolean canReschedule() { /*...*/ }
  private void calculateFee() { /*...*/ }
  private void processPayment() { /*...*/ }

  // 200 dòng utility methods
  // ... total: 1200 dòng
}
```

### Phát hiện
```
# Phát hiện file Java >500 dòng
wc -l **/*.java | awk '$1 > 500' # Count lines >500
# Phát hiện class có >20 methods (dấu hiệu God class)
grep -c "public.*\(.*\)" *.java # Count public methods
```

### Checklist
- [ ] Mỗi file Java <500 dòng
- [ ] Mỗi class có trách nhiệm rõ ràng (Single Responsibility)
- [ ] Logic phức tạp được tách thành các component/helper riêng
- [ ] Không có God class (class làm quá nhiều việc)

## 01.09 — README.md với hướng dẫn chạy dự án

### Metadata
- **Mã số:** 01.09
- **Mức độ:** 🟡 NÊN CÓ
- **Điểm trừ:** -2
- **Tags:** documentation, onboarding, developer-experience

### Tại sao?
README.md là điểm tiếp xúc đầu tiên của developer mới với dự án. Hướng dẫn rõ ràng giúp onboarding nhanh hơn, giảm thời gian setup môi trường, và tránh lỗi cấu hình. Đây cũng là nơi lưu trữ kiến thức về kiến trúc, quyết định kỹ thuật, và quy trình development.

### ✅ Cách đúng
```markdown
# Medical Box API

## Yêu cầu hệ thống
- Java 21
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+ (optional, cho caching)

## Cài đặt và chạy

### 1. Clone repository
```bash
git clone https://github.com/example/medicalbox-api.git
cd medicalbox-api
```

### 2. Cấu hình database
```bash
# Tạo database
createdb medicalbox_dev

# Chạy migration
./mvnw flyway:migrate
```

### 3. Cấu hình environment variables
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres123
export JWT_SECRET=your-secret-key
```

### 4. Chạy ứng dụng
```bash
# Development mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production build
./mvnw clean package
java -jar target/medicalbox-api-1.0.0.jar --spring.profiles.active=prod
```

## Testing
```bash
# Chạy unit tests
./mvnw test

# Chạy integration tests
./mvnw verify -P integration-test

# Test coverage report
./mvnw jacoco:report
```

## Kiến trúc
- **Package structure:** Feature-based (auth, appointment, doctor)
- **Database:** PostgreSQL với Flyway migration
- **Security:** JWT-based authentication
- **Caching:** Redis (optional)

## API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Quy trình development
1. Checkout từ `dev` branch
2. Tạo feature branch: `feature/TICKET-ID-description`
3. Commit theo conventional commits
4. Tạo PR vào `dev` branch
```

### ❌ Cách sai
```markdown
# Project

Run: `mvn spring-boot:run`

Done.
```

### Phát hiện
```
# Phát hiện README.md quá ngắn (<20 dòng)
wc -l README.md | awk '$1 < 20' # README <20 lines
# Phát hiện README.md không có section "Requirements" hoặc "Installation"
grep -i "requirement\|installation\|setup" README.md # Missing key sections
```

### Checklist
- [ ] Có file README.md ở root của repository
- [ ] Có section "Requirements" (Java version, dependencies)
- [ ] Có section "Installation" với step-by-step guide
- [ ] Có section "Testing" với lệnh chạy tests
- [ ] Có section "API Documentation" hoặc "Architecture"
- [ ] Có hướng dẫn cấu hình environment variables
