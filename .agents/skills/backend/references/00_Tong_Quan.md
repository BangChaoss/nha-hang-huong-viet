# Chuẩn Mực Spring Boot — Toàn Tập
# Spring Boot Best Practices — Comprehensive Guide

> **Phiên bản:** 1.0
> **Ngày tạo:** 2026-02-16
> **Mục đích:** Tài liệu tham khảo toàn diện về các chuẩn mực, best practices cho dự án Spring Boot
> **Cách tiếp cận:** Proactive — đề xuất chuẩn mực đúng, thay vì chỉ phát hiện lỗi
> **Bổ sung cho:** Engineering Failures Audit Skill (reactive — phát hiện lỗi)

---

## Giới thiệu

Tài liệu này tổng hợp **174 best practices** cho phát triển Spring Boot, được phân loại thành **19 lĩnh vực**. Mỗi practice bao gồm mô tả lý do, code đúng ✅, code sai ❌, regex phát hiện, và checklist.

## Mục lục

| # | Lĩnh vực | File | Số practices |
|---|----------|------|:------------:|
| 01 | Cấu Trúc Dự Án | `01_Cau_Truc_Du_An.md` | 9 |
| 02 | Dependency Injection & IoC | `02_Dependency_Injection.md` | 9 |
| 03 | REST API & Controller | `03_REST_API_Controller.md` | 10 |
| 04 | Service Layer | `04_Service_Layer.md` | 8 |
| 05 | Spring Data JPA & Hibernate | `05_JPA_Hibernate.md` | 12 |
| 06 | Security | `06_Security.md` | 12 |
| 07 | Exception Handling | `07_Exception_Handling.md` | 9 |
| 08 | Logging & Monitoring | `08_Logging_Monitoring.md` | 9 |
| 09 | Testing | `09_Testing.md` | 10 |
| 10 | Caching | `10_Caching.md` | 8 |
| 11 | Async & Messaging | `11_Async_Messaging.md` | 10 |
| 12 | Validation & Data Binding | `12_Validation.md` | 10 |
| 13 | Configuration & Profiles | `13_Configuration.md` | 8 |
| 14 | Migration & Database Versioning | `14_Migration.md` | 8 |
| 15 | Deployment & DevOps | `15_Deployment.md` | 9 |
| 16 | Spring Cloud | `16_Spring_Cloud.md` | 8 |
| 17 | WebSocket & Real-time | `17_WebSocket.md` | 7 |
| 18 | Email & Notification | `18_Email_Notification.md` | 8 |
| 19 | File Storage & Upload | `19_File_Storage.md` | 10 |
| | **Tổng cộng** | | **174** |

## Phân bố mức độ

| Mức độ | Ký hiệu | Số lượng | Ý nghĩa | Điểm trừ |
|--------|----------|:--------:|---------|:---------:|
| Bắt buộc | 🔴 BẮT BUỘC | 56 | Vi phạm gây rủi ro nghiêm trọng, phải tuân thủ | -10 |
| Khuyến nghị | 🟠 KHUYẾN NGHỊ | 82 | Nên tuân thủ để đảm bảo chất lượng | -5 |
| Nên có | 🟡 NÊN CÓ | 36 | Cải thiện thêm, áp dụng khi có thời gian | -2 |

## Hệ thống chấm điểm

### Công thức
```
Domain Score = 100 - Σ(violation_penalty)
  Minimum: 0, Maximum: 100

Overall Score = Σ(domain_score × weight) / Σ(weight)
```

### Trọng số domain
| Domain | Trọng số | Lý do |
|--------|:--------:|-------|
| 06 Security | ×3 | Bảo mật ảnh hưởng toàn hệ thống |
| 05 JPA & Hibernate | ×2 | Hiệu năng database là nút thắt phổ biến |
| 09 Testing | ×2 | Test đảm bảo chất lượng dài hạn |
| Tất cả domain khác | ×1 | Trọng số cơ bản |

### Đánh giá tổng thể
| Điểm | Xếp hạng | Mô tả |
|:-----:|----------|-------|
| 90-100 | 🏆 XUẤT SẮC | Tuân thủ gần như toàn bộ best practices |
| 75-89 | ✅ TỐT | Đạt chuẩn, cần cải thiện nhỏ |
| 60-74 | ⚠️ TRUNG BÌNH | Nhiều vi phạm, cần khắc phục sớm |
| 40-59 | 🟠 YẾU | Vi phạm nghiêm trọng, cần refactor |
| 0-39 | 🔴 NGUY HIỂM | Rủi ro cao, cần xử lý ngay |

## Format mỗi best practice

Mỗi practice được trình bày theo 6 phần thống nhất:

1. **Tên** — Tiếng Việt (mô tả ngắn gọn)
2. **Metadata** — Mã số, Mức độ, Domain, Tags
3. **Tại sao?** — Giải thích lý do + hậu quả nếu vi phạm
4. **Cách đúng ✅** — Code example đúng chuẩn (Java/Spring Boot)
5. **Cách sai ❌** — Code example vi phạm (anti-pattern)
6. **Phát hiện** — Regex patterns để tự động quét mã nguồn

## So sánh với Engineering Failures Skill

| Tiêu chí | Engineering Failures | Spring Boot Best Practices |
|----------|---------------------|---------------------------|
| Cách tiếp cận | Reactive (phát hiện lỗi) | Proactive (đề xuất chuẩn) |
| Phạm vi | Đa ngôn ngữ | Spring Boot chuyên sâu |
| Patterns | 137 failure patterns | 174 best practices |
| Chấm điểm | Không | Có (0-100 mỗi domain) |
| Mức độ | CRITICAL/HIGH/MEDIUM/LOW | BẮT BUỘC/KHUYẾN NGHỊ/NÊN CÓ |

## Cách sử dụng

### Quét tự động
```bash
# Quét toàn bộ dự án
/springboot-best-practices

# Viết tắt
/sbp

# Chỉ quét domain cụ thể
/springboot-best-practices 06        # Chỉ quét Security
/springboot-best-practices 05        # Chỉ quét JPA

# Chỉ quét theo mức độ
/springboot-best-practices mandatory  # Chỉ BẮT BUỘC

# Quét dự án khác
/springboot-best-practices all D:/my-project/src
```

### Đọc tham khảo
Mở trực tiếp các file trong thư mục `knowledge/` để đọc chi tiết.

## Nguồn tham khảo

- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Baeldung Spring Tutorials](https://www.baeldung.com/spring-tutorial)
- [Vlad Mihalcea - Hibernate Best Practices](https://vladmihalcea.com/)
- [OWASP Top 10](https://owasp.org/Top10/)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
