# Domain 14: Migration & Database Versioning
> **Số practices:** 8 | 🔴 4 | 🟠 3 | 🟡 1
> **Trọng số:** ×1

---

## 14.01 - Flyway hoặc Liquibase cho schema versioning
**Mức độ:** 🔴 BẮT BUỘC

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `flyway`, `liquibase`, `schema versioning`, `database migration`
- **Liên quan:** 14.04, 14.06, 14.08

### Tại sao?
1. **Tính nhất quán:** Schema version được quản lý tập trung, đồng bộ giữa các môi trường
2. **Truy vết được:** Mọi thay đổi DB đều được ghi lại và có thể rollback
3. **Tự động hóa:** Migration chạy tự động khi deploy, giảm lỗi do thao tác thủ công
4. **Audit trail:** Biết ai, khi nào, thay đổi gì trong database schema
5. **Team collaboration:** Nhiều developer có thể làm việc song song mà không conflict schema

### ✅ Cách đúng

**Flyway (khuyên dùng cho dự án đơn giản):**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
    # Clean chỉ dùng dev, KHÔNG BAO GIỜ dùng production
    clean-disabled: true
```

```sql
-- src/main/resources/db/migration/V1__init_schema.sql
CREATE TABLE rel_actor (
  actor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_type SMALLINT NOT NULL,
  actor_ref_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_actor_type_ref (actor_type, actor_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- src/main/resources/db/migration/V2__add_trx_call.sql
CREATE TABLE trx_call (
  call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  call_status SMALLINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (actor_id) REFERENCES rel_actor(actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Liquibase (khuyên dùng cho dự án phức tạp, multi-DB):**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.liquibase</groupId>
  <artifactId>liquibase-core</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    contexts: dev,prod
    drop-first: false # CRITICAL: NEVER true in production
```

```xml
<!-- src/main/resources/db/changelog/db.changelog-master.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
  xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

  <include file="db/changelog/changes/v1.0-init-schema.xml"/>
  <include file="db/changelog/changes/v1.1-add-call-table.xml"/>
</databaseChangeLog>
```

```xml
<!-- src/main/resources/db/changelog/changes/v1.0-init-schema.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
  xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

  <changeSet id="v1.0-001" author="dinhdv">
    <createTable tableName="rel_actor">
      <column name="actor_id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="actor_type" type="SMALLINT">
        <constraints nullable="false"/>
      </column>
      <column name="actor_ref_id" type="BIGINT">
        <constraints nullable="false"/>
      </column>
      <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
    </createTable>

    <addUniqueConstraint
      tableName="rel_actor"
      columnNames="actor_type, actor_ref_id"
      constraintName="uk_actor_type_ref"/>
  </changeSet>
</databaseChangeLog>
```

**Version tracking tự động:**

```java
// FlywayConfig.java
@Configuration
public class FlywayConfig {

  @Bean
  public FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> {
      // Validate migration trước khi chạy
      flyway.validate();

      // Repair nếu cần (chỉ dev)
      if (isDevProfile()) {
        flyway.repair();
      }

      // Chạy migration
      flyway.migrate();
    };
  }

  private boolean isDevProfile() {
    // Logic check active profile
    return false; // Placeholder
  }
}
```

### ❌ Cách sai

```yaml
# ❌ KHÔNG dùng Hibernate auto-DDL ở production
spring:
  jpa:
    hibernate:
      ddl-auto: update # NGUY HIỂM! Có thể mất data

# ❌ KHÔNG tắt validation
spring:
  flyway:
    validate-on-migrate: false # Bỏ qua lỗi checksum - nguy hiểm

# ❌ KHÔNG cho phép out-of-order migration ở production
spring:
  flyway:
    out-of-order: true # Chỉ dùng dev
```

```sql
-- ❌ KHÔNG viết migration không idempotent
CREATE TABLE rel_actor (...); -- Lỗi nếu chạy lần 2

-- ✅ ĐÚNG: Thêm IF NOT EXISTS (hoặc check trong Liquibase)
CREATE TABLE IF NOT EXISTS rel_actor (...);
```

```java
// ❌ KHÔNG tự viết schema migration trong code
@PostConstruct
public void initSchema() {
  jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ...");
}
```

### Phát hiện

**SonarQube Rule:**
```yaml
# custom-rules.xml
- ruleId: "spring-boot-no-ddl-auto-update"
  pattern: "ddl-auto:\\s*(create|create-drop|update)"
  severity: BLOCKER
  message: "Không dùng hibernate.ddl-auto ở production. Dùng Flyway/Liquibase"

- ruleId: "spring-boot-flyway-required"
  pattern: "spring.flyway.enabled:\\s*false"
  severity: CRITICAL
  message: "Flyway phải enabled ở production"
```

**Maven Enforcer:**
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-enforcer-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>enforce</goal>
      </goals>
      <configuration>
        <rules>
          <requireProperty>
            <property>spring.flyway.enabled</property>
            <message>Flyway hoặc Liquibase là bắt buộc</message>
            <regex>true</regex>
          </requireProperty>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Checklist
- [ ] Đã thêm Flyway hoặc Liquibase dependency
- [ ] Migration files trong `src/main/resources/db/migration` (Flyway) hoặc `db/changelog` (Liquibase)
- [ ] `spring.flyway.enabled=true` hoặc `spring.liquibase.enabled=true` trong production profile
- [ ] `validate-on-migrate=true` để check consistency
- [ ] `clean-disabled=true` trong production (Flyway)
- [ ] Không dùng `hibernate.ddl-auto=update/create` trong bất kỳ profile nào ngoài test
- [ ] Mọi thay đổi schema đều qua migration files, không sửa DB thủ công
- [ ] CI/CD chạy migration validation trước khi deploy

---

## 14.02 - Migration backward-compatible (không drop column ngay)
**Mức độ:** 🔴 BẮT BUỘC

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `backward compatible`, `zero downtime`, `blue-green deployment`, `column removal`
- **Liên quan:** 14.05, 14.07

### Tại sao?
1. **Zero-downtime deployment:** Phiên bản cũ vẫn chạy khi deploy phiên bản mới
2. **Rollback an toàn:** Có thể rollback code mà không cần rollback DB
3. **Gradual migration:** Cho phép migrate data từ từ trước khi xóa column cũ
4. **Giảm rủi ro:** Phát hiện lỗi sớm trước khi xóa data vĩnh viễn
5. **Multi-instance deployment:** Nhiều instance chạy phiên bản khác nhau cùng lúc

### ✅ Cách đúng

**Quy trình 3 bước để xóa column:**

```sql
-- BƯỚC 1 (Version N): Thêm column mới, migrate data
-- V10__add_new_status_column.sql
ALTER TABLE trx_call ADD COLUMN call_status_new VARCHAR(20);

-- Migrate data từ column cũ sang mới
UPDATE trx_call
SET call_status_new = CASE call_status
  WHEN 1 THEN 'WAITING'
  WHEN 2 THEN 'RINGING'
  WHEN 3 THEN 'CONNECTED'
  ELSE 'UNKNOWN'
END;

-- Đặt NOT NULL sau khi migrate xong
ALTER TABLE trx_call MODIFY COLUMN call_status_new VARCHAR(20) NOT NULL;
```

```java
// Version N: Code đọc cả 2 columns
@Entity
@Table(name = "trx_call")
public class TrxCall {

  @Column(name = "call_status") // Column cũ (deprecated)
  private Short callStatusOld;

  @Column(name = "call_status_new", nullable = false)
  @Enumerated(EnumType.STRING)
  private CallStatus callStatusNew;

  // Getter ưu tiên column mới
  public CallStatus getCallStatus() {
    return callStatusNew != null ? callStatusNew : mapOldStatus(callStatusOld);
  }

  private CallStatus mapOldStatus(Short old) {
    if (old == null) return null;
    return switch (old) {
      case 1 -> CallStatus.WAITING;
      case 2 -> CallStatus.RINGING;
      case 3 -> CallStatus.CONNECTED;
      default -> CallStatus.UNKNOWN;
    };
  }
}
```

```sql
-- BƯỚC 2 (Version N+1): Sau vài tuần, xóa column cũ khỏi code
-- Không cần migration SQL, chỉ xóa field trong Entity
```

```java
// Version N+1: Chỉ dùng column mới
@Entity
@Table(name = "trx_call")
public class TrxCall {

  @Column(name = "call_status_new", nullable = false)
  @Enumerated(EnumType.STRING)
  private CallStatus callStatus; // Đổi tên field

  // Không còn callStatusOld
}
```

```sql
-- BƯỚC 3 (Version N+2): Sau khi confirm không cần rollback, drop column cũ
-- V12__drop_old_status_column.sql
ALTER TABLE trx_call DROP COLUMN call_status;

-- Rename column mới về tên cũ (optional, nếu cần)
-- ALTER TABLE trx_call CHANGE call_status_new call_status VARCHAR(20) NOT NULL;
```

**Thêm column mới (backward-compatible):**

```sql
-- V15__add_optional_field.sql
-- ✅ ĐÚNG: Thêm column nullable hoặc có default
ALTER TABLE rel_actor
ADD COLUMN last_seen_at TIMESTAMP NULL DEFAULT NULL;

-- ❌ SAI: Thêm column NOT NULL ngay lập tức
-- ALTER TABLE rel_actor ADD COLUMN last_seen_at TIMESTAMP NOT NULL;
-- → Lỗi với data cũ
```

**Rename column (3 bước):**

```sql
-- Version N: Thêm column mới + copy data
ALTER TABLE trx_call ADD COLUMN new_column_name VARCHAR(50);
UPDATE trx_call SET new_column_name = old_column_name;

-- Version N+1: Code dùng column mới
-- Version N+2: Drop column cũ
ALTER TABLE trx_call DROP COLUMN old_column_name;
```

### ❌ Cách sai

```sql
-- ❌ SAI: Drop column ngay lập tức
-- V10__remove_old_status.sql
ALTER TABLE trx_call DROP COLUMN call_status;
-- → Phiên bản code cũ vẫn đang chạy sẽ bị lỗi ngay lập tức
```

```sql
-- ❌ SAI: Thêm column NOT NULL không có default
ALTER TABLE trx_call ADD COLUMN required_field VARCHAR(50) NOT NULL;
-- → INSERT cũ sẽ fail ngay lập tức
```

```sql
-- ❌ SAI: Rename column trực tiếp
ALTER TABLE trx_call CHANGE call_status call_status_new VARCHAR(20);
-- → Code cũ tìm call_status → lỗi ngay
```

```sql
-- ❌ SAI: Thay đổi data type không compatible
ALTER TABLE trx_call MODIFY COLUMN call_status VARCHAR(10);
-- → Nếu data cũ > 10 chars → mất data
```

### Phát hiện

**Code Review Checklist:**
```yaml
migration_review_rules:
  - rule: "DROP COLUMN detected"
    pattern: "DROP\\s+COLUMN"
    action: "Reject"
    message: "Không drop column trực tiếp. Phải qua 3 bước: add new → migrate → drop old"

  - rule: "ADD COLUMN NOT NULL without default"
    pattern: "ADD\\s+COLUMN\\s+\\w+\\s+\\w+\\s+NOT\\s+NULL(?!\\s+DEFAULT)"
    action: "Reject"
    message: "Column mới phải nullable hoặc có DEFAULT value"

  - rule: "CHANGE/RENAME COLUMN detected"
    pattern: "(CHANGE|RENAME)\\s+COLUMN"
    action: "Review"
    message: "Rename column phải qua 3 bước để backward-compatible"
```

**CI/CD Gate:**
```bash
#!/bin/bash
# check-migration-safety.sh

# Check DROP COLUMN
if grep -rE "DROP\s+COLUMN" db/migration/; then
  echo "❌ REJECTED: DROP COLUMN detected. Use 3-step process."
  exit 1
fi

# Check ADD COLUMN NOT NULL without default
if grep -rE "ADD\s+COLUMN\s+\w+\s+\w+\s+NOT\s+NULL(?!\s+DEFAULT)" db/migration/; then
  echo "❌ REJECTED: NOT NULL column without DEFAULT value."
  exit 1
fi

echo "✅ Migration safety check passed"
```

### Checklist
- [ ] Không có `DROP COLUMN` trong migration mới (phải qua 3 bước)
- [ ] Không có `ADD COLUMN ... NOT NULL` mà không có `DEFAULT`
- [ ] Không rename column trực tiếp (dùng add + copy + drop)
- [ ] Code version N hỗ trợ cả column cũ và mới
- [ ] Có kế hoạch rõ ràng cho 3 bước deployment
- [ ] Migration đã test với data production-like
- [ ] Có rollback plan cho mỗi bước
- [ ] Team đã review và approve backward-compatibility strategy

---

## 14.03 - Separate DDL vs DML migrations
**Mức độ:** 🟠 KHUYẾN NGHỊ

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `DDL`, `DML`, `schema change`, `data change`, `transaction`
- **Liên quan:** 14.01, 14.07

### Tại sao?
1. **Performance isolation:** DDL thường lock table, tách riêng để kiểm soát downtime
2. **Rollback strategy:** DDL khó rollback hơn DML
3. **Testing riêng biệt:** DDL test schema structure, DML test data correctness
4. **Audit rõ ràng:** Biết migration nào thay đổi schema, migration nào thay đổi data
5. **Rerun DML an toàn:** DML có thể idempotent, DDL khó hơn

### ✅ Cách đúng

**Tách file migration:**

```
db/migration/
├── V1.0__schema_init.sql          # DDL only
├── V1.1__seed_master_data.sql     # DML only
├── V2.0__add_call_table.sql       # DDL only
├── V2.1__migrate_call_data.sql    # DML only
└── V3.0__add_status_column.sql    # DDL only
```

**DDL Migration (schema changes):**

```sql
-- V2.0__add_call_table.sql (DDL ONLY)
-- Chỉ CREATE/ALTER/DROP table, index, constraint

CREATE TABLE trx_call (
  call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  call_status SMALLINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_actor_status (actor_id, call_status),
  FOREIGN KEY (actor_id) REFERENCES rel_actor(actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Thêm index riêng
CREATE INDEX idx_created_at ON trx_call(created_at);
```

**DML Migration (data changes):**

```sql
-- V2.1__migrate_call_data.sql (DML ONLY)
-- Chỉ INSERT/UPDATE/DELETE data

-- Migrate data từ legacy system
INSERT INTO trx_call (actor_id, call_status, created_at)
SELECT
  ra.actor_id,
  CASE lc.status
    WHEN 'waiting' THEN 1
    WHEN 'ringing' THEN 2
    WHEN 'connected' THEN 3
    ELSE 0
  END,
  lc.created_at
FROM legacy_calls lc
JOIN rel_actor ra ON ra.actor_ref_id = lc.user_id AND ra.actor_type = 1
WHERE NOT EXISTS (
  SELECT 1 FROM trx_call tc
  WHERE tc.actor_id = ra.actor_id
  AND tc.created_at = lc.created_at
);

-- Update status cho existing records
UPDATE trx_call
SET call_status = 3
WHERE call_status = 2
  AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) > 30;
```

**Seed data (DML riêng):**

```sql
-- V1.1__seed_master_data.sql (DML ONLY)
-- Master data cần thiết cho application

-- Insert actor types (nếu dùng lookup table)
INSERT INTO mst_actor_type (type_id, type_name) VALUES
(1, 'USER'),
(2, 'OPERATOR'),
(3, 'CLINIC')
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- Insert call statuses
INSERT INTO mst_call_status (status_id, status_name) VALUES
(1, 'WAITING'),
(2, 'RINGING'),
(3, 'CONNECTED'),
(4, 'ENDED')
ON DUPLICATE KEY UPDATE status_name = VALUES(status_name);
```

**Combined migration khi cần thiết (mark rõ):**

```sql
-- V3.0__add_priority_with_seed.sql
-- COMBINED: DDL + DML (chỉ khi 2 bước phụ thuộc nhau)

-- === DDL SECTION ===
ALTER TABLE trx_call
ADD COLUMN priority SMALLINT DEFAULT 0 NOT NULL;

CREATE INDEX idx_priority ON trx_call(priority);

-- === DML SECTION ===
-- Set default priority based on actor type
UPDATE trx_call tc
JOIN rel_actor ra ON tc.actor_id = ra.actor_id
SET tc.priority = CASE ra.actor_type
  WHEN 2 THEN 10  -- OPERATOR high priority
  WHEN 3 THEN 5   -- CLINIC medium priority
  ELSE 1          -- USER normal priority
END;
```

**Liquibase context để phân loại:**

```xml
<!-- v2.0-schema.xml (DDL) -->
<changeSet id="v2.0-001" author="dinhdv" context="schema">
  <createTable tableName="trx_call">
    <!-- ... -->
  </createTable>
</changeSet>

<!-- v2.1-data.xml (DML) -->
<changeSet id="v2.1-001" author="dinhdv" context="data">
  <insert tableName="trx_call">
    <!-- ... -->
  </insert>
</changeSet>
```

```yaml
# application.yml - Chạy cả 2 contexts
spring:
  liquibase:
    contexts: schema,data
```

### ❌ Cách sai

```sql
-- ❌ SAI: Trộn DDL và DML trong 1 file
-- V2.0__add_call_table_and_data.sql
CREATE TABLE trx_call (...);  -- DDL

-- Ngay sau đó INSERT data
INSERT INTO trx_call VALUES (...);  -- DML

-- Rồi lại ALTER
ALTER TABLE trx_call ADD COLUMN priority INT;  -- DDL

-- Rồi lại UPDATE
UPDATE trx_call SET priority = 1;  -- DML
```

```sql
-- ❌ SAI: Data migration không idempotent
INSERT INTO trx_call (actor_id, call_status)
VALUES (1, 1), (2, 2);
-- Chạy 2 lần → duplicate key error

-- ✅ ĐÚNG: Dùng ON DUPLICATE KEY hoặc WHERE NOT EXISTS
INSERT INTO trx_call (actor_id, call_status)
VALUES (1, 1), (2, 2)
ON DUPLICATE KEY UPDATE call_status = VALUES(call_status);
```

```sql
-- ❌ SAI: DDL phụ thuộc vào DML
CREATE TABLE trx_call (...);
INSERT INTO trx_call VALUES (...);
ALTER TABLE trx_call ADD FOREIGN KEY (...); -- Phụ thuộc data đã insert
```

### Phát hiện

**File naming convention:**
```yaml
naming_rules:
  ddl_pattern: "V\\d+\\.\\d+__.*schema.*\\.sql"
  dml_pattern: "V\\d+\\.\\d+__.*(seed|data|migrate).*\\.sql"

file_validation:
  - if filename contains "schema":
      allowed_keywords: [CREATE, ALTER, DROP, INDEX, CONSTRAINT]
      forbidden_keywords: [INSERT, UPDATE, DELETE]

  - if filename contains "data|seed|migrate":
      allowed_keywords: [INSERT, UPDATE, DELETE, SELECT]
      forbidden_keywords: [CREATE TABLE, ALTER TABLE, DROP]
```

**CI Check Script:**
```bash
#!/bin/bash
# validate-migration-separation.sh

for file in db/migration/*.sql; do
  filename=$(basename "$file")

  # Check schema files
  if [[ $filename == *"schema"* ]]; then
    if grep -iE "INSERT|UPDATE|DELETE" "$file"; then
      echo "❌ $filename: DDL file contains DML statements"
      exit 1
    fi
  fi

  # Check data files
  if [[ $filename == *"data"* || $filename == *"seed"* ]]; then
    if grep -iE "CREATE TABLE|ALTER TABLE|DROP" "$file"; then
      echo "❌ $filename: DML file contains DDL statements"
      exit 1
    fi
  fi
done

echo "✅ Migration separation validated"
```

### Checklist
- [ ] DDL migrations có suffix `_schema.sql` hoặc `_ddl.sql`
- [ ] DML migrations có suffix `_data.sql`, `_seed.sql`, hoặc `_migrate.sql`
- [ ] DDL files chỉ chứa CREATE/ALTER/DROP/INDEX
- [ ] DML files chỉ chứa INSERT/UPDATE/DELETE
- [ ] Seed data tách riêng khỏi schema changes
- [ ] DML migrations là idempotent (dùng ON DUPLICATE KEY, WHERE NOT EXISTS)
- [ ] CI/CD validate file naming convention
- [ ] Team hiểu rõ lý do tách DDL vs DML

---

## 14.04 - Migration chạy trước application startup
**Mức độ:** 🔴 BẮT BUỘC

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `startup`, `initialization`, `dependency order`, `schema ready`
- **Liên quan:** 14.01, 14.06

### Tại sao?
1. **Schema sẵn sàng:** Application code cần schema đã tồn tại trước khi chạy
2. **Tránh race condition:** Nhiều instance khởi động đồng thời không gây conflict
3. **Fail fast:** Phát hiện migration lỗi trước khi application nhận traffic
4. **Atomic deployment:** Migration thành công mới start application
5. **Healthcheck chính xác:** Application chỉ healthy khi DB schema đúng version

### ✅ Cách đúng

**Flyway tự động chạy trước (default behavior):**

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    # Migration chạy TRƯỚC khi Spring Boot khởi tạo beans
    baseline-on-migrate: true
    validate-on-migrate: true
    # Fail application startup nếu migration lỗi
    fail-on-missing-locations: true
```

```java
// Application.java - Không cần config gì thêm
@SpringBootApplication
public class MedicalBoxApplication {

  public static void main(String[] args) {
    // Flyway tự động chạy TRƯỚC dòng này return
    SpringApplication.run(MedicalBoxApplication.class, args);
    // → Application chỉ start nếu migration thành công
  }
}
```

**Explicit dependency order (nếu custom config):**

```java
// FlywayConfig.java
@Configuration
public class FlywayConfig {

  @Bean(initMethod = "migrate")
  @DependsOn("dataSource")
  public Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
      .dataSource(dataSource)
      .locations("classpath:db/migration")
      .baselineOnMigrate(true)
      .validateOnMigrate(true)
      .load();
  }
}

// AppInitializer.java - Chạy SAU khi migration xong
@Component
@DependsOn("flyway") // CRITICAL: Phải chờ Flyway xong
public class AppInitializer implements ApplicationListener<ContextRefreshedEvent> {

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    // Code này chỉ chạy SAU KHI migration thành công
    log.info("Application initialized with schema version: {}",
      getSchemaVersion());
  }
}
```

**Health check bao gồm migration status:**

```java
// MigrationHealthIndicator.java
@Component
public class MigrationHealthIndicator implements HealthIndicator {

  private final Flyway flyway;

  public MigrationHealthIndicator(Flyway flyway) {
    this.flyway = flyway;
  }

  @Override
  public Health health() {
    try {
      MigrationInfo current = flyway.info().current();

      if (current == null) {
        return Health.down()
          .withDetail("reason", "No migration applied")
          .build();
      }

      // Check pending migrations
      MigrationInfo[] pending = flyway.info().pending();
      if (pending.length > 0) {
        return Health.down()
          .withDetail("reason", "Pending migrations exist")
          .withDetail("count", pending.length)
          .build();
      }

      return Health.up()
        .withDetail("version", current.getVersion().toString())
        .withDetail("description", current.getDescription())
        .withDetail("installedOn", current.getInstalledOn())
        .build();

    } catch (Exception e) {
      return Health.down()
        .withException(e)
        .build();
    }
  }
}
```

**Kubernetes liveness/readiness probe:**

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: medicalbox-api
        image: medicalbox:v1.0

        # Readiness: Chỉ nhận traffic khi migration xong
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          failureThreshold: 3

        # Liveness: Restart nếu migration fail
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          failureThreshold: 3
```

**Docker entrypoint script:**

```bash
#!/bin/bash
# entrypoint.sh - Migration trước, app sau

set -e

echo "Running database migrations..."

# Option 1: Flyway CLI (trong container riêng)
flyway -url=$DB_URL -user=$DB_USER -password=$DB_PASSWORD migrate

if [ $? -ne 0 ]; then
  echo "❌ Migration failed. Aborting startup."
  exit 1
fi

echo "✅ Migration completed successfully"

# Option 2: Spring Boot tự chạy (preferred)
echo "Starting application..."
exec java -jar /app/medicalbox-api.jar
```

**Liquibase config tương tự:**

```yaml
# application.yml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    # Fail startup if migration fails
    drop-first: false
    # Liquibase mặc định chạy trước application context
```

### ❌ Cách sai

```yaml
# ❌ SAI: Tắt auto migration
spring:
  flyway:
    enabled: false

# Application start TRƯỚC, rồi migrate thủ công SAU → RACE CONDITION
```

```java
// ❌ SAI: Migration trong @PostConstruct (chạy SAU application context)
@Component
public class DatabaseInitializer {

  @Autowired
  private Flyway flyway;

  @PostConstruct
  public void migrate() {
    flyway.migrate(); // QUÁ MUỘN! Application đã start
  }
}
```

```java
// ❌ SAI: Migration trong background thread
@SpringBootApplication
public class MedicalBoxApplication {

  public static void main(String[] args) {
    SpringApplication.run(MedicalBoxApplication.class, args);

    // Application đã start, migration chạy sau
    new Thread(() -> {
      flyway.migrate(); // NGUY HIỂM! Application nhận request khi schema chưa sẵn sàng
    }).start();
  }
}
```

```yaml
# ❌ SAI: Readiness probe không check migration
readinessProbe:
  httpGet:
    path: /actuator/health # Không check schema version
```

### Phát hiện

**CI/CD Integration Test:**
```bash
#!/bin/bash
# test-migration-before-startup.sh

# Start DB
docker-compose up -d mysql

# Chờ DB ready
sleep 5

# Start app và check migration chạy trước
docker-compose up medicalbox-api &
APP_PID=$!

# Wait for migration logs
timeout 30 bash -c 'until docker logs medicalbox-api 2>&1 | grep "Successfully applied.*migrations"; do sleep 1; done'

if [ $? -ne 0 ]; then
  echo "❌ Migration did not run before timeout"
  kill $APP_PID
  exit 1
fi

# Check app healthy
sleep 5
curl -f http://localhost:8080/actuator/health || {
  echo "❌ Application not healthy after migration"
  exit 1
}

echo "✅ Migration ran successfully before application startup"
```

**Application log validation:**
```
Expected log order:
1. [Flyway] Starting migration...
2. [Flyway] Successfully applied 5 migrations
3. [Spring Boot] Started MedicalBoxApplication in 3.5s
```

### Checklist
- [ ] `spring.flyway.enabled=true` hoặc `spring.liquibase.enabled=true`
- [ ] Migration chạy TRƯỚC `SpringApplication.run()` return
- [ ] Application startup FAIL nếu migration fail
- [ ] Health endpoint trả về migration version
- [ ] Readiness probe check migration status
- [ ] Liveness probe detect migration failure
- [ ] CI/CD test verify migration order
- [ ] Log rõ ràng: "Migration completed" → "Application started"
- [ ] Không có background migration thread
- [ ] Kubernetes deployment có proper probe configuration

---

## 14.05 - Rollback script cho mỗi migration
**Mức độ:** 🟠 KHUYẾN NGHỊ

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `rollback`, `down migration`, `undo`, `disaster recovery`
- **Liên quan:** 14.02, 14.03

### Tại sao?
1. **Disaster recovery:** Có thể rollback nhanh khi phát hiện lỗi nghiêm trọng
2. **Development flexibility:** Dễ dàng undo migration trong môi trường dev/test
3. **Confidence cao hơn:** Biết có cách quay lại nếu deployment fail
4. **Audit trail:** Hiểu rõ cách reverse mỗi thay đổi
5. **Testing rollback:** Có thể test rollback procedure trước khi production deploy

### ✅ Cách đúng

**Flyway rollback (Flyway Teams/Enterprise edition):**

```sql
-- V10__add_call_priority.sql (UP migration)
ALTER TABLE trx_call ADD COLUMN priority SMALLINT DEFAULT 0 NOT NULL;
CREATE INDEX idx_priority ON trx_call(priority);

-- U10__add_call_priority.sql (UNDO migration - Flyway Teams)
DROP INDEX idx_priority ON trx_call;
ALTER TABLE trx_call DROP COLUMN priority;
```

**Liquibase rollback (built-in support):**

```xml
<!-- db/changelog/changes/v10-add-priority.xml -->
<databaseChangeLog>

  <!-- UP migration -->
  <changeSet id="v10-001" author="dinhdv">
    <addColumn tableName="trx_call">
      <column name="priority" type="SMALLINT" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
    </addColumn>

    <createIndex indexName="idx_priority" tableName="trx_call">
      <column name="priority"/>
    </createIndex>

    <!-- ROLLBACK tự động generate -->
    <rollback>
      <dropIndex indexName="idx_priority" tableName="trx_call"/>
      <dropColumn tableName="trx_call" columnName="priority"/>
    </rollback>
  </changeSet>

</databaseChangeLog>
```

```bash
# Rollback Liquibase
liquibase rollback-count 1  # Rollback 1 changeset gần nhất
liquibase rollback-to-tag v1.5  # Rollback về tag cụ thể
liquibase rollback-to-date 2026-02-15  # Rollback về ngày cụ thể
```

**Manual rollback script (cho Flyway Community):**

```
db/migration/
├── up/
│   ├── V10__add_call_priority.sql
│   └── V11__add_status_index.sql
└── down/
    ├── V10__add_call_priority_rollback.sql
    └── V11__add_status_index_rollback.sql
```

```sql
-- down/V10__add_call_priority_rollback.sql
-- Rollback for V10__add_call_priority.sql
-- Run manually if needed: mysql < down/V10__add_call_priority_rollback.sql

-- IMPORTANT: Reverse order of UP migration

-- Step 1: Drop index first (dependency)
DROP INDEX idx_priority ON trx_call;

-- Step 2: Drop column
ALTER TABLE trx_call DROP COLUMN priority;

-- Verify rollback
SELECT COUNT(*) AS verify_rollback
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'trx_call'
  AND COLUMN_NAME = 'priority';
-- Expected: 0
```

**Rollback script cho data migration:**

```sql
-- up/V11__migrate_legacy_calls.sql
INSERT INTO trx_call (actor_id, call_status, legacy_id)
SELECT ra.actor_id, lc.status, lc.id
FROM legacy_calls lc
JOIN rel_actor ra ON ra.actor_ref_id = lc.user_id;

-- down/V11__migrate_legacy_calls_rollback.sql
DELETE FROM trx_call
WHERE legacy_id IS NOT NULL;

-- Verify
SELECT COUNT(*) FROM trx_call WHERE legacy_id IS NOT NULL;
-- Expected: 0
```

**Rollback không thể restore data → Backup first:**

```sql
-- up/V12__drop_deprecated_table.sql

-- CRITICAL: Backup data before drop
CREATE TABLE trx_old_calls_backup AS
SELECT * FROM trx_old_calls;

-- Now safe to drop
DROP TABLE trx_old_calls;

-- down/V12__drop_deprecated_table_rollback.sql
CREATE TABLE trx_old_calls LIKE trx_old_calls_backup;
INSERT INTO trx_old_calls SELECT * FROM trx_old_calls_backup;

-- Optional: Drop backup sau khi confirm
-- DROP TABLE trx_old_calls_backup;
```

**Rollback testing script:**

```bash
#!/bin/bash
# test-rollback.sh - Test rollback trước khi deploy production

DB_NAME="medicalbox_rollback_test"

echo "Creating test database..."
mysql -e "CREATE DATABASE IF NOT EXISTS $DB_NAME;"

echo "Running UP migration..."
flyway -url=jdbc:mysql://localhost/$DB_NAME migrate

# Snapshot schema
mysqldump $DB_NAME > schema_after_up.sql

echo "Running DOWN migration..."
mysql $DB_NAME < down/V10__add_call_priority_rollback.sql

# Snapshot schema
mysqldump $DB_NAME > schema_after_down.sql

# Compare with original
diff schema_before_up.sql schema_after_down.sql || {
  echo "❌ Rollback did not restore schema to original state"
  exit 1
}

echo "✅ Rollback test passed"
mysql -e "DROP DATABASE $DB_NAME;"
```

### ❌ Cách sai

```sql
-- ❌ SAI: Rollback không có WHERE clause
-- down/V11__migrate_legacy_calls_rollback.sql
DELETE FROM trx_call;  -- XÓA HẾT! Không chỉ data vừa migrate
```

```sql
-- ❌ SAI: Rollback thiếu bước (quên drop index)
-- down/V10__add_call_priority_rollback.sql
ALTER TABLE trx_call DROP COLUMN priority;
-- → Index idx_priority vẫn tồn tại → orphaned index
```

```sql
-- ❌ SAI: Rollback không reverse đúng thứ tự
-- UP: CREATE TABLE → ADD CONSTRAINT
-- DOWN sai:
ALTER TABLE trx_call DROP CONSTRAINT fk_actor;  -- Lỗi! Table chưa tồn tại
DROP TABLE trx_call;

-- DOWN đúng:
ALTER TABLE trx_call DROP CONSTRAINT fk_actor;  -- Drop constraint TRƯỚC
DROP TABLE trx_call;  -- Drop table SAU
```

```sql
-- ❌ SAI: Không test rollback script
-- Viết rollback script nhưng không bao giờ chạy thử
-- → Khi cần rollback production → phát hiện script lỗi
```

### Phát hiện

**CI/CD Rollback Test:**
```yaml
# .github/workflows/test-migrations.yml
name: Test Migrations & Rollback

jobs:
  test-rollback:
    runs-on: ubuntu-latest
    steps:
      - name: Start MySQL
        run: docker run -d -e MYSQL_ROOT_PASSWORD=root mysql:8

      - name: Run migrations UP
        run: flyway migrate

      - name: Snapshot schema
        run: mysqldump medicalbox > schema_up.sql

      - name: Run rollback DOWN
        run: |
          for rollback in down/*.sql; do
            mysql medicalbox < $rollback
          done

      - name: Verify rollback
        run: |
          mysqldump medicalbox > schema_down.sql
          diff schema_original.sql schema_down.sql
```

**Manual checklist template:**
```markdown
# Rollback Checklist for V10__add_call_priority

## Rollback Script Review
- [ ] Rollback script exists: `down/V10__add_call_priority_rollback.sql`
- [ ] Steps in reverse order of UP migration
- [ ] All objects created in UP are dropped in DOWN
- [ ] WHERE clauses specific enough (no accidental data loss)
- [ ] Tested in dev environment
- [ ] Tested with production-like data volume

## Rollback Procedure
1. [ ] Backup production DB before rollback
2. [ ] Stop application to prevent new data
3. [ ] Run rollback script
4. [ ] Verify schema matches expected state
5. [ ] Restart application with previous version
6. [ ] Monitor for errors

## Rollback Testing Evidence
- [ ] Screenshot of successful rollback in dev
- [ ] Schema diff before/after matches expectation
- [ ] No data loss in test environment
```

### Checklist
- [ ] Mỗi migration có rollback script tương ứng trong `down/` folder
- [ ] Rollback script reverse đúng thứ tự các bước trong UP migration
- [ ] Rollback script có WHERE clause cụ thể (không xóa nhầm data)
- [ ] Data migration có backup before destructive operations
- [ ] Rollback script được test trong dev/staging environment
- [ ] CI/CD tự động test rollback cho mỗi PR
- [ ] Rollback procedure được document rõ ràng
- [ ] Team đã practice rollback drill ít nhất 1 lần

---

## 14.06 - Không dùng spring.jpa.hibernate.ddl-auto=update ở production
**Mức độ:** 🔴 BẮT BUỘC

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `ddl-auto`, `hibernate`, `schema generation`, `production safety`
- **Liên quan:** 14.01, 14.04

### Tại sao?
1. **Mất data nguy hiểm:** `ddl-auto=update` có thể drop column hoặc thay đổi schema không mong muốn
2. **Không thể kiểm soát:** Hibernate quyết định schema changes, không phải developer
3. **Không có audit trail:** Không biết ai, khi nào, thay đổi gì trong DB
4. **Không thể rollback:** Thay đổi tự động không có rollback script
5. **Race condition:** Nhiều instance start cùng lúc → schema conflict

### ✅ Cách đúng

**Production profile:**

```yaml
# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ✅ CHỈ VALIDATE, KHÔNG TỰ ĐỘNG SỬA
    properties:
      hibernate:
        format_sql: false
        show_sql: false

  flyway:
    enabled: true
    validate-on-migrate: true
    # Schema changes PHẢI qua Flyway
```

**Development profile:**

```yaml
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ✅ VẪN DÙNG VALIDATE, không update
      # Dùng Flyway ngay từ dev để quen workflow
    properties:
      hibernate:
        format_sql: true
        show_sql: true

  flyway:
    enabled: true
    # Clean OK trong dev (KHÔNG BAO GIỜ trong prod)
    clean-disabled: false
```

**Test profile (H2 in-memory):**

```yaml
# application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # ✅ OK cho test với H2 in-memory
    database-platform: org.hibernate.dialect.H2Dialect

  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver

  flyway:
    enabled: false  # Không cần Flyway cho H2 test
```

**Validate mode sẽ fail startup nếu Entity không khớp DB:**

```java
// Entity có field mới
@Entity
@Table(name = "trx_call")
public class TrxCall {

  @Column(name = "priority")  // Column mới
  private Short priority;
}
```

```
# DB chưa có column này → Application FAIL startup với ddl-auto=validate
org.hibernate.tool.schema.spi.SchemaManagementException:
  Schema-validation: missing column [priority] in table [trx_call]
```

→ Developer BẮT BUỘC phải tạo migration trước:

```sql
-- V15__add_call_priority.sql
ALTER TABLE trx_call ADD COLUMN priority SMALLINT DEFAULT 0;
```

→ Chạy lại application → Validate pass → Application start OK.

**CI/CD enforcement:**

```yaml
# .github/workflows/ci.yml
- name: Check DDL-Auto Config
  run: |
    # Reject nếu có ddl-auto != validate trong application*.yml
    if grep -rE "ddl-auto:\s*(create|create-drop|update)" src/main/resources/application*.yml; then
      echo "❌ REJECTED: ddl-auto must be 'validate' or 'none' in application.yml"
      exit 1
    fi
```

**SonarQube custom rule:**

```yaml
# sonar-custom-rules.yml
- ruleId: "spring-boot-ddl-auto-production"
  pattern: "ddl-auto:\\s*(create|create-drop|update)"
  filePattern: "application(-prod|-production)?\\.yml"
  severity: BLOCKER
  message: |
    Không dùng ddl-auto=create/update/create-drop trong production.
    Dùng Flyway hoặc Liquibase cho schema management.
```

### ❌ Cách sai

```yaml
# ❌ SAI: ddl-auto=update trong production
# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # NGUY HIỂM!
      # Hibernate tự động ALTER table → có thể mất data
```

```yaml
# ❌ SAI: ddl-auto=create-drop trong bất kỳ môi trường nào có data
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # DROP ALL TABLES khi shutdown!
      # Mất hết data dev → Developer phải seed lại
```

```yaml
# ❌ SAI: Dùng update để "tiện" trong dev
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # "Tiện" nhưng tạo thói quen xấu
      # Developer không học cách viết migration
```

```yaml
# ❌ SAI: ddl-auto khác nhau giữa dev vs prod
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Dev dùng update

# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Prod dùng validate

# → Dev không bao giờ test migration workflow
# → Production deployment bất ngờ fail do thiếu migration
```

### Phát hiện

**Git pre-commit hook:**

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Check staged application*.yml files
for file in $(git diff --cached --name-only | grep 'application.*\.yml'); do

  # Check for dangerous ddl-auto values
  if grep -E "ddl-auto:\s*(create|create-drop|update)" "$file"; then
    echo "❌ COMMIT REJECTED: $file contains ddl-auto=create/update/create-drop"
    echo "   Use ddl-auto=validate and manage schema via Flyway/Liquibase"
    exit 1
  fi

done

exit 0
```

**Runtime check:**

```java
// DDLAutoValidator.java
@Component
public class DDLAutoValidator implements ApplicationListener<ApplicationReadyEvent> {

  @Value("${spring.jpa.hibernate.ddl-auto:none}")
  private String ddlAuto;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {

    List<String> dangerousValues = List.of("create", "create-drop", "update");

    if (activeProfile.contains("prod") && dangerousValues.contains(ddlAuto)) {
      String message = String.format(
        "CRITICAL: ddl-auto=%s is FORBIDDEN in production. Use 'validate' instead.",
        ddlAuto
      );

      log.error(message);
      throw new IllegalStateException(message);
      // Application sẽ CRASH ngay lập tức
    }

    if (dangerousValues.contains(ddlAuto)) {
      log.warn("⚠️  WARNING: ddl-auto={} detected. Consider using Flyway/Liquibase.", ddlAuto);
    }
  }
}
```

**SonarQube scan:**
```bash
# CI/CD pipeline
mvn sonar:sonar -Dsonar.customRules=check-ddl-auto
```

### Checklist
- [ ] `ddl-auto=validate` trong tất cả profiles (trừ test với H2)
- [ ] `ddl-auto=none` hoặc không config (mặc định `none`)
- [ ] KHÔNG BAO GIỜ có `ddl-auto=create/update/create-drop` trong application-prod.yml
- [ ] Flyway hoặc Liquibase enabled trong production
- [ ] CI/CD reject nếu phát hiện ddl-auto nguy hiểm
- [ ] Git pre-commit hook kiểm tra config files
- [ ] Runtime validation crash application nếu config sai trong prod
- [ ] Team đã được training về nguy hiểm của ddl-auto=update

---

## 14.07 - Seed data qua migration, không hardcode trong code
**Mức độ:** 🟠 KHUYẾN NGHỊ

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `seed data`, `master data`, `reference data`, `initialization`
- **Liên quan:** 14.03, 14.05

### Tại sao?
1. **Consistency:** Data khởi tạo giống nhau ở mọi môi trường (dev, staging, prod)
2. **Truy vết được:** Biết data nào được thêm khi nào, bởi migration nào
3. **Idempotent:** Chạy lại migration không tạo duplicate data
4. **Version control:** Seed data được track trong Git giống như code
5. **Rollback:** Có thể rollback seed data nếu cần

### ✅ Cách đúng

**Master data qua migration (DML):**

```sql
-- V1.1__seed_master_data.sql (SEPARATE từ schema migration)

-- Actor types (nếu dùng lookup table)
INSERT INTO mst_actor_type (type_id, type_name, description) VALUES
(1, 'USER', '患者 - Patient'),
(2, 'OPERATOR', 'オペレーター - Operator'),
(3, 'CLINIC', '医院 - Clinic')
ON DUPLICATE KEY UPDATE
  type_name = VALUES(type_name),
  description = VALUES(description);

-- Call statuses
INSERT INTO mst_call_status (status_id, status_name, description) VALUES
(1, 'WAITING', '待機中 - Waiting in queue'),
(2, 'RINGING', '呼び出し中 - Ringing'),
(3, 'CONNECTED', '接続中 - Connected'),
(4, 'ENDED', '終了 - Call ended'),
(5, 'CANCELLED', 'キャンセル - Cancelled')
ON DUPLICATE KEY UPDATE
  status_name = VALUES(status_name),
  description = VALUES(description);

-- Default admin user (CHỈ trong dev/staging)
INSERT INTO trx_user_operator (
  user_name,
  email,
  password_hash,
  role,
  created_at
) VALUES (
  'admin',
  'admin@medicalbox.jp',
  '$2a$10$...', -- Bcrypt hash của "admin123"
  'SUPER_ADMIN',
  NOW()
)
ON DUPLICATE KEY UPDATE email = email; -- No-op nếu đã tồn tại
```

**Environment-specific seed data:**

```
db/migration/
├── common/
│   ├── V1.1__seed_master_data.sql      # Chạy mọi env
│   └── V1.2__seed_call_statuses.sql
├── dev/
│   └── V99.1__seed_dev_test_data.sql   # CHỈ chạy dev
└── staging/
    └── V99.2__seed_staging_users.sql   # CHỈ chạy staging
```

```yaml
# application-dev.yml
spring:
  flyway:
    locations:
      - classpath:db/migration/common
      - classpath:db/migration/dev  # Load thêm dev seed data

# application-prod.yml
spring:
  flyway:
    locations:
      - classpath:db/migration/common  # CHỈ common, không có dev/staging
```

**Liquibase context cho seed data:**

```xml
<!-- db/changelog/seed/master-data.xml -->
<databaseChangeLog>

  <!-- Master data - chạy mọi env -->
  <changeSet id="seed-001" author="dinhdv" context="!test">
    <insert tableName="mst_call_status">
      <column name="status_id" valueNumeric="1"/>
      <column name="status_name" value="WAITING"/>
    </insert>
    <!-- ... -->
  </changeSet>

  <!-- Test data - CHỈ chạy dev/staging -->
  <changeSet id="seed-002" author="dinhdv" context="dev,staging">
    <insert tableName="trx_user_operator">
      <column name="user_name" value="test_operator"/>
      <column name="email" value="test@example.com"/>
      <!-- ... -->
    </insert>
  </changeSet>

</databaseChangeLog>
```

```yaml
# application-dev.yml
spring:
  liquibase:
    contexts: dev,staging  # Load test data

# application-prod.yml
spring:
  liquibase:
    contexts: prod  # KHÔNG load test data
```

**Seed data với file CSV (large data):**

```sql
-- V1.3__seed_prefectures.sql
LOAD DATA LOCAL INFILE '/db/seed/prefectures.csv'
INTO TABLE mst_prefecture
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(prefecture_code, prefecture_name_ja, prefecture_name_en);
```

```
db/seed/prefectures.csv:
prefecture_code,prefecture_name_ja,prefecture_name_en
01,"北海道","Hokkaido"
02,"青森県","Aomori"
...
```

**Liquibase loadData:**

```xml
<changeSet id="seed-003" author="dinhdv">
  <loadData
    file="db/seed/prefectures.csv"
    tableName="mst_prefecture"
    separator=","
    quotchar='"'>
    <column name="prefecture_code" type="STRING"/>
    <column name="prefecture_name_ja" type="STRING"/>
    <column name="prefecture_name_en" type="STRING"/>
  </loadData>
</changeSet>
```

### ❌ Cách sai

```java
// ❌ SAI: Seed data trong @PostConstruct
@Component
public class DataSeeder {

  @Autowired
  private CallStatusRepository callStatusRepository;

  @PostConstruct
  public void seedData() {
    // Hardcode trong code → Không track được trong migration
    callStatusRepository.save(new CallStatus(1, "WAITING"));
    callStatusRepository.save(new CallStatus(2, "RINGING"));
    // ...
  }
}
```

```java
// ❌ SAI: Seed data trong ApplicationRunner
@Component
public class DatabaseInitializer implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) throws Exception {
    // Chạy MỖI LẦN application start → duplicate data
    jdbcTemplate.execute("INSERT INTO mst_call_status VALUES (1, 'WAITING')");
  }
}
```

```java
// ❌ SAI: Seed data hardcode trong enum + DB sync logic
public enum CallStatus {
  WAITING(1, "待機中"),
  RINGING(2, "呼び出し中");

  // Logic phức tạp để sync enum → DB
  @PostConstruct
  public void syncToDatabase() {
    // Không track được changes, khó rollback
  }
}
```

```sql
-- ❌ SAI: Seed data KHÔNG idempotent
INSERT INTO mst_call_status VALUES (1, 'WAITING');
-- Chạy lần 2 → Duplicate key error
```

### Phát hiện

**Code review checklist:**
```yaml
anti_patterns:
  - pattern: "@PostConstruct.*repository\\.save"
    message: "Seed data phải qua migration SQL, không hardcode trong @PostConstruct"

  - pattern: "ApplicationRunner.*INSERT INTO"
    message: "Seed data phải qua migration, không trong ApplicationRunner"

  - pattern: "jdbcTemplate\\.execute.*INSERT.*mst_"
    message: "Master data phải qua migration files"
```

**CI/CD check:**
```bash
#!/bin/bash
# check-seed-data-location.sh

# Tìm code seed data trong Java files
if grep -rE "@PostConstruct.*repository\.save|ApplicationRunner.*INSERT" src/main/java/; then
  echo "❌ REJECTED: Seed data detected in Java code. Use migration SQL instead."
  exit 1
fi

# Verify seed data có trong migration folder
if [ ! -f "src/main/resources/db/migration/V1.1__seed_master_data.sql" ]; then
  echo "⚠️  WARNING: No seed data migration found. Master data should be in migrations."
fi

echo "✅ Seed data location check passed"
```

### Checklist
- [ ] Master data trong `V{version}__seed_{name}.sql`
- [ ] Seed SQL dùng `ON DUPLICATE KEY UPDATE` hoặc `INSERT IGNORE` (idempotent)
- [ ] Environment-specific data tách riêng folder (dev, staging, prod)
- [ ] Flyway locations hoặc Liquibase contexts config đúng cho mỗi env
- [ ] KHÔNG có seed data logic trong `@PostConstruct`, `ApplicationRunner`, hoặc `CommandLineRunner`
- [ ] Large seed data dùng CSV + LOAD DATA hoặc Liquibase loadData
- [ ] Seed data migration có rollback script (nếu cần)
- [ ] CI/CD reject nếu phát hiện seed data trong Java code

---

## 14.08 - Migration naming convention: V{version}__{description}.sql
**Mức độ:** 🟡 NÊN CÓ

### Metadata
- **Danh mục:** Migration & Database Versioning
- **Từ khóa:** `naming convention`, `version`, `flyway`, `liquibase`, `file organization`
- **Liên quan:** 14.01, 14.03

### Tại sao?
1. **Thứ tự rõ ràng:** Version number đảm bảo migrations chạy đúng thứ tự
2. **Tìm kiếm dễ dàng:** Biết migration nào làm gì từ tên file
3. **Tránh conflict:** Team nhiều người không tạo file trùng version
4. **Tool compatibility:** Flyway/Liquibase yêu cầu naming convention cụ thể
5. **Self-documenting:** Tên file mô tả mục đích, không cần mở file để biết nội dung

### ✅ Cách đúng

**Flyway naming convention:**

```
Format: V{VERSION}__{DESCRIPTION}.sql

- V: Prefix bắt buộc (Versioned migration)
- {VERSION}: Số version (1, 1.1, 2.0, 2.1, etc.)
- __: 2 underscores phân tách version và description
- {DESCRIPTION}: Mô tả ngắn gọn (snake_case)
- .sql: Extension
```

**Examples:**

```
db/migration/
├── V1__init_schema.sql
├── V1.1__seed_master_data.sql
├── V2__add_call_table.sql
├── V2.1__add_call_indexes.sql
├── V2.2__migrate_legacy_calls.sql
├── V3__add_actor_last_seen.sql
├── V3.1__add_call_priority.sql
└── V4__drop_deprecated_tables.sql
```

**Version numbering strategy:**

```
V1.x   - Initial schema + master data
V2.x   - Feature: Call system
  V2.0 - Schema changes
  V2.1 - Indexes
  V2.2 - Data migration
V3.x   - Feature: Actor tracking
  V3.0 - Schema
  V3.1 - Enhancement
V10.x  - Major version (breaking changes)
```

**Semantic versioning approach:**

```
V{MAJOR}.{MINOR}.{PATCH}__{description}.sql

MAJOR: Breaking changes (schema không backward compatible)
MINOR: New features (backward compatible)
PATCH: Bug fixes, index additions

Examples:
V1.0.0__init_schema.sql
V1.1.0__add_call_table.sql
V1.1.1__fix_call_index.sql
V2.0.0__breaking_change_status_enum.sql
```

**Date-based versioning (alternative):**

```
V{YYYYMMDD}{NN}__{description}.sql

YYYYMMDD: Date
NN: Sequence number trong ngày

Examples:
V20260215_01__init_schema.sql
V20260215_02__seed_data.sql
V20260216_01__add_call_table.sql
```

**Repeatable migrations (Flyway):**

```
R__{description}.sql

- Chạy lại mỗi khi checksum thay đổi
- Dùng cho views, stored procedures, functions

Examples:
R__create_call_summary_view.sql
R__update_actor_statistics_sp.sql
```

**Liquibase naming convention:**

```xml
<!-- db/changelog/db.changelog-master.xml -->
<databaseChangeLog>
  <include file="db/changelog/v1.0/01-init-schema.xml"/>
  <include file="db/changelog/v1.0/02-seed-data.xml"/>
  <include file="db/changelog/v2.0/01-add-call-table.xml"/>
  <include file="db/changelog/v2.0/02-add-indexes.xml"/>
</databaseChangeLog>
```

```
db/changelog/
├── db.changelog-master.xml
├── v1.0/
│   ├── 01-init-schema.xml
│   └── 02-seed-data.xml
├── v2.0/
│   ├── 01-add-call-table.xml
│   ├── 02-add-indexes.xml
│   └── 03-migrate-data.xml
└── v3.0/
    └── 01-add-priority.xml
```

**Description best practices:**

```
✅ GOOD:
V2__add_call_table.sql
V3__add_actor_last_seen_column.sql
V4__create_call_priority_index.sql
V5__migrate_legacy_user_data.sql

❌ BAD:
V2__update.sql                    # Quá chung chung
V3__fix.sql                       # Không rõ fix gì
V4__changes.sql                   # Không mô tả
V5__ticket_jr_123.sql             # Dùng ticket ID, không mô tả thay đổi
V6__john_changes_20260215.sql     # Tên người + date, không mô tả nội dung
```

### ❌ Cách sai

```
❌ SAI: Thiếu prefix V
2__add_call_table.sql

❌ SAI: 1 underscore thay vì 2
V2_add_call_table.sql

❌ SAI: Không có version number
add_call_table.sql

❌ SAI: Version trùng nhau
V2__add_call_table.sql
V2__add_actor_table.sql  # Conflict!

❌ SAI: Khoảng trắng trong tên
V2__add call table.sql

❌ SAI: Ký tự đặc biệt
V2__add-call-table!.sql

❌ SAI: Extension sai
V2__add_call_table.txt
```

### Phát hiện

**Git pre-commit hook:**

```bash
#!/bin/bash
# .git/hooks/pre-commit

MIGRATION_DIR="src/main/resources/db/migration"

for file in $(git diff --cached --name-only --diff-filter=A | grep "$MIGRATION_DIR"); do

  filename=$(basename "$file")

  # Check Flyway naming convention
  if [[ ! $filename =~ ^V[0-9]+(\.[0-9]+)*__[a-z0-9_]+\.sql$ ]]; then
    echo "❌ INVALID MIGRATION NAME: $filename"
    echo "   Expected format: V{VERSION}__{description}.sql"
    echo "   Example: V2.1__add_call_table.sql"
    exit 1
  fi

  # Check for duplicate versions
  version=$(echo "$filename" | sed -E 's/V([0-9.]+)__.*/\1/')
  existing=$(find "$MIGRATION_DIR" -name "V${version}__*.sql" | wc -l)

  if [ "$existing" -gt 1 ]; then
    echo "❌ DUPLICATE VERSION: $version already exists"
    exit 1
  fi

done

echo "✅ Migration naming validation passed"
```

**CI/CD validation:**

```bash
#!/bin/bash
# validate-migration-names.sh

MIGRATION_DIR="src/main/resources/db/migration"

# Check naming convention
find "$MIGRATION_DIR" -name "*.sql" | while read -r file; do
  filename=$(basename "$file")

  if [[ ! $filename =~ ^(V[0-9]+(\.[0-9]+)*|R)__[a-z0-9_]+\.sql$ ]]; then
    echo "❌ Invalid migration name: $filename"
    exit 1
  fi
done

# Check for version conflicts
versions=$(find "$MIGRATION_DIR" -name "V*.sql" | sed -E 's/.*V([0-9.]+)__.*/\1/' | sort)
duplicates=$(echo "$versions" | uniq -d)

if [ -n "$duplicates" ]; then
  echo "❌ Duplicate versions found:"
  echo "$duplicates"
  exit 1
fi

echo "✅ All migration names valid"
```

**Maven enforcer plugin:**

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-enforcer-plugin</artifactId>
  <executions>
    <execution>
      <id>validate-migration-names</id>
      <goals>
        <goal>enforce</goal>
      </goals>
      <configuration>
        <rules>
          <requireFilesMatch>
            <files>
              <file>src/main/resources/db/migration/*.sql</file>
            </files>
            <pattern>^V[0-9]+(\.[0-9]+)*__[a-z0-9_]+\.sql$</pattern>
            <message>Migration files must follow Flyway naming convention: V{VERSION}__{description}.sql</message>
          </requireFilesMatch>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Team versioning strategy document:**

```markdown
# Migration Versioning Strategy

## Version Number Assignment

1. Check latest version:
   ```bash
   ls -1 src/main/resources/db/migration/ | grep "^V" | sort -V | tail -1
   ```

2. Increment version:
   - Feature branch: Next minor version (V2.3 → V2.4)
   - Hotfix: Patch version (V2.3 → V2.3.1)
   - Major release: Next major version (V2.9 → V3.0)

3. Create file:
   ```bash
   touch src/main/resources/db/migration/V2.4__add_new_feature.sql
   ```

## Conflict Resolution

- If 2 developers create same version → Later PR must renumber
- Use date-based versioning if team > 5 developers
- Coordinate in Slack #database channel before creating migration

## Naming Rules

- Prefix: `V` (versioned) or `R` (repeatable)
- Version: Numeric with dots (1, 1.1, 2.0, etc.)
- Separator: Double underscore `__`
- Description: snake_case, lowercase, descriptive
- Extension: `.sql`

## Examples

✅ V2.1__add_call_priority_column.sql
✅ V3.0__migrate_legacy_user_data.sql
✅ R__create_call_summary_view.sql

❌ V2.1_AddCallPriority.sql (single underscore, PascalCase)
❌ add_call_priority.sql (no version)
❌ V2.1__JR-123.sql (ticket ID instead of description)
```

### Checklist
- [ ] Tất cả migrations follow format `V{VERSION}__{description}.sql`
- [ ] Version numbers không trùng nhau
- [ ] Description dùng snake_case, lowercase
- [ ] Description mô tả rõ ràng nội dung thay đổi
- [ ] Team có versioning strategy document
- [ ] Git pre-commit hook validate naming convention
- [ ] CI/CD reject nếu tên file không hợp lệ
- [ ] Repeatable migrations dùng prefix `R__` (nếu có)
- [ ] Liquibase changesets có ID unique và author clear

---

## Summary Checklist - Domain 14: Migration & Database Versioning

### 🔴 BẮT BUỘC (CRITICAL)
- [ ] 14.01: Flyway hoặc Liquibase enabled trong production
- [ ] 14.02: Không DROP COLUMN trực tiếp, dùng 3-step migration
- [ ] 14.04: Migration chạy TRƯỚC application startup (validate before run)
- [ ] 14.06: `ddl-auto=validate` hoặc `none`, KHÔNG BAO GIỜ `update/create` trong prod

### 🟠 KHUYẾN NGHỊ (RECOMMENDED)
- [ ] 14.03: Tách riêng DDL vs DML migration files
- [ ] 14.05: Mỗi migration có rollback script tương ứng
- [ ] 14.07: Seed data qua migration SQL, không hardcode trong code

### 🟡 NÊN CÓ (NICE TO HAVE)
- [ ] 14.08: Follow naming convention `V{VERSION}__{description}.sql`

### CI/CD Integration
```bash
# Pre-commit checks
- Validate migration naming
- Check ddl-auto config
- Detect DROP COLUMN

# CI pipeline
- Run migration validation
- Test rollback scripts
- Schema diff comparison

# CD pipeline
- Run migrations before deployment
- Verify schema version in health check
```

---

**Tổng kết:**

Domain 14 đảm bảo database schema được quản lý chuyên nghiệp như source code:
- **Version control** cho schema changes
- **Backward compatibility** cho zero-downtime deployment
- **Audit trail** cho mọi thay đổi DB
- **Rollback capability** khi cần thiết
- **Environment consistency** từ dev đến production

Migration là **nền tảng** cho DevOps practices và continuous deployment an toàn.
