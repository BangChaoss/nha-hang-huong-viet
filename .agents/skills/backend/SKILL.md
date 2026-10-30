---
name: springboot-best-practices
description: |
  Quét mã nguồn Spring Boot tự động để đánh giá mức tuân thủ best practices.
  174 practices từ 19 lĩnh vực: Cấu trúc, DI, REST API, Service, JPA, Security,
  Exception, Logging, Testing, Caching, Async, Validation, Configuration,
  Migration, Deployment, Spring Cloud, WebSocket, Email, File Storage.
  Chấm điểm 0-100 mỗi domain với trọng số (Security ×3, JPA ×2, Testing ×2).
triggers:
  - /springboot-best-practices
  - /sbp
---

# Kỹ Năng Đánh Giá Chuẩn Mực Spring Boot (Spring Boot Best Practices Audit)

Bạn là một chuyên gia Spring Boot, nhiệm vụ là quét dự án để đánh giá mức tuân thủ 174 best practices dựa trên kho kiến thức 19 domains.

## Tham số đầu vào

Người dùng có thể cung cấp tham số:
- **scope**: `all` (mặc định) | số domain `01`-`19` | mức độ `mandatory` / `recommended` / `nice-to-have`
- **path**: đường dẫn thư mục cần quét (mặc định: thư mục làm việc hiện tại)

Ví dụ:
- `/springboot-best-practices` — quét toàn bộ
- `/sbp 06` — chỉ quét domain Security
- `/sbp mandatory` — chỉ quét practices BẮT BUỘC
- `/sbp all D:/my-project/src` — quét project khác

## Quy trình thực hiện

### Bước 1: Xác nhận dự án Spring Boot

Kiểm tra sự tồn tại của các file đặc trưng:

| Dấu hiệu | Xác nhận |
|-----------|----------|
| `pom.xml` với `spring-boot-starter` | Maven + Spring Boot |
| `build.gradle` với `spring-boot` | Gradle + Spring Boot |
| `application.yml` hoặc `application.properties` | Spring Boot config |
| `@SpringBootApplication` trong source | Main application class |

Nếu không tìm thấy Spring Boot → Thông báo "Dự án này không phải Spring Boot" và dừng.

Xác định thêm:
- Spring Boot version (2.x vs 3.x)
- Java version (8, 11, 17, 21)
- Các starter dependencies (web, data-jpa, security, etc.)
- Build tool (Maven vs Gradle)

### Bước 2: Đọc kho kiến thức

Đọc các file knowledge từ thư mục `~/.claude/skills/springboot-best-practices/knowledge/`:

```
00_Tong_Quan.md              — Tổng quan và mục lục
01_Cau_Truc_Du_An.md         — Cấu trúc dự án (9 practices)
02_Dependency_Injection.md   — DI & IoC (9 practices)
03_REST_API_Controller.md    — REST API (10 practices)
04_Service_Layer.md          — Service layer (8 practices)
05_JPA_Hibernate.md          — JPA & Hibernate (12 practices)
06_Security.md               — Security (12 practices)
07_Exception_Handling.md     — Exception handling (9 practices)
08_Logging_Monitoring.md     — Logging (9 practices)
09_Testing.md                — Testing (10 practices)
10_Caching.md                — Caching (8 practices)
11_Async_Messaging.md        — Async & Messaging (10 practices)
12_Validation.md             — Validation (10 practices)
13_Configuration.md          — Configuration (8 practices)
14_Migration.md              — Migration (8 practices)
15_Deployment.md             — Deployment (9 practices)
16_Spring_Cloud.md           — Spring Cloud (8 practices)
17_WebSocket.md              — WebSocket (7 practices)
18_Email_Notification.md     — Email (8 practices)
19_File_Storage.md           — File Storage (10 practices)
```

Chỉ đọc domain được yêu cầu nếu scope là số cụ thể.
Nếu dự án không dùng module nào (vd: không có WebSocket dependency), bỏ qua domain đó.

### Bước 3: Quét mã nguồn bằng 4 agents song song

Tạo 4 agents song song bằng Task tool, mỗi agent quét ~5 domains:

**Agent A — Domains 01-05:**
- 01: Cấu Trúc Dự Án
- 02: Dependency Injection
- 03: REST API & Controller
- 04: Service Layer
- 05: JPA & Hibernate

**Agent B — Domains 06-10:**
- 06: Security
- 07: Exception Handling
- 08: Logging & Monitoring
- 09: Testing
- 10: Caching

**Agent C — Domains 11-15:**
- 11: Async & Messaging
- 12: Validation
- 13: Configuration
- 14: Migration
- 15: Deployment

**Agent D — Domains 16-19:**
- 16: Spring Cloud
- 17: WebSocket
- 18: Email & Notification
- 19: File Storage

Mỗi agent thực hiện:
1. Đọc file knowledge của các domains được giao
2. Trích xuất các detection regex patterns từ phần "Phát hiện"
3. Chạy Grep với từng regex pattern trên thư mục mã nguồn
4. Thu thập kết quả: file, dòng, nội dung khớp
5. Đọc ngữ cảnh xung quanh (±5 dòng) để xác nhận
6. Phân loại: TUÂN THỦ ✅ hoặc VI PHẠM ❌
7. Tính điểm per-domain
8. Trả về kết quả dạng JSON:
```json
{
  "domain": "06_Security",
  "score": 72,
  "total_practices": 12,
  "compliant": 8,
  "violations": [
    {
      "id": "06.07",
      "name": "Không log sensitive data",
      "severity": "BẮT BUỘC",
      "penalty": -10,
      "file": "src/main/java/com/example/service/AuthService.java",
      "line": 45,
      "code": "log.info(\"Login: \" + username + \":\" + password)",
      "suggestion": "Không log password. Dùng: log.info(\"Login attempt: {}\", username)"
    }
  ]
}
```

### Bước 4: Tính điểm và lọc kết quả

**Tính điểm mỗi domain:**
```
Domain Score = max(0, 100 - Σ(violation_penalty))
  🔴 BẮT BUỘC vi phạm: -10 điểm
  🟠 KHUYẾN NGHỊ vi phạm: -5 điểm
  🟡 NÊN CÓ vi phạm: -2 điểm
```

**Tính điểm tổng với trọng số:**
```
Trọng số: Security ×3, JPA ×2, Testing ×2, còn lại ×1
Overall Score = Σ(domain_score × weight) / Σ(weight)
```

**Lọc nhiễu:**
- Bỏ test/, node_modules/, build/, target/, generated/
- Bỏ false positives trong comments
- Loại bỏ trùng lặp (cùng file + dòng + practice)
- Bỏ qua domain không áp dụng (không có dependency tương ứng)

### Bước 5: Xuất báo cáo

**1. Terminal (tóm tắt):**
```markdown
# 📊 Báo Cáo Đánh Giá Spring Boot Best Practices
**Dự án:** [tên thư mục]
**Ngày:** [YYYY-MM-DD]
**Spring Boot:** [version]
**Java:** [version]
**Phạm vi:** [all / domain X / severity Y]

## Điểm Tổng: [XX]/100 — [Xếp hạng]

## Điểm Theo Domain
| # | Domain | Điểm | Tuân thủ | Vi phạm | Trọng số |
|---|--------|:----:|:--------:|:-------:|:--------:|
| 01 | Cấu Trúc Dự Án | 85 | 7/9 | 2 | ×1 |
| ... | ... | ... | ... | ... | ... |

## Chi Tiết Vi Phạm

### 🔴 BẮT BUỘC (phải sửa ngay)

#### [V-01] [06.07] Không log sensitive data — AuthService.java:45
**Mã nguồn:**
```java
log.info("Login: " + username + ":" + password);
```
**Đề xuất:** Không log password
```java
log.info("Login attempt: {}", username);
```

### 🟠 KHUYẾN NGHỊ (nên sửa sớm)
[tương tự...]

### 🟡 NÊN CÓ (cải thiện khi có thời gian)
[tương tự...]

## Đề Xuất Ưu Tiên
1. [Top 3 vi phạm nghiêm trọng nhất cần sửa]
2. [...]
3. [...]
```

**2. File báo cáo (chi tiết):**
Ghi vào `reports/sbp-audit-YYYY-MM-DD-HHMMSS.md` trong thư mục skill.

### Bước 6: Đề xuất tiếp theo

Sau khi xuất báo cáo, đề xuất:
- "Chạy `/sbp mandatory` để tập trung vào practices BẮT BUỘC"
- "Chạy `/sbp [domain_number]` để kiểm tra chuyên sâu một lĩnh vực"
- "Đọc file knowledge tương ứng để hiểu chi tiết và áp dụng"
- "Chạy `/engineering-failures` để phát hiện thêm các mẫu lỗi kỹ thuật"

## Lưu ý quan trọng

1. **Không sửa code tự động** — Skill chỉ báo cáo và đề xuất, không tự ý sửa mã nguồn
2. **False positives** — Một số findings có thể là false positive, người dùng cần xác nhận
3. **Ngữ cảnh** — Luôn đọc ngữ cảnh xung quanh để giảm false positive
4. **Smart detection** — Bỏ qua domain không áp dụng (ví dụ: không quét WebSocket nếu không có dependency)
5. **Bổ sung** — Skill này bổ sung cho Engineering Failures, không thay thế. Nên chạy cả hai.
