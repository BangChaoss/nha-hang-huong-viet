# Hương Việt Restaurant Management System - Database Documentation

## 1. Overview
* **Database Engine**: MySQL 8.0+ (InnoDB Storage Engine)
* **Default Charset**: `utf8mb4`
* **Default Collation**: `utf8mb4_unicode_ci`
* **Total Tables**: 23 tables matching Domain-Driven Design (DDD) & Crow's Foot ERD 1:1.

## 2. Key Architecture Decisions
1. **Primary Key Strategy**:
   * Stored as `CHAR(36)` containing time-ordered UUIDv7 strings.
   * Seamless integration with Java 22 (`UUID.fromString(rs.getString("id"))` / `pstmt.setString(1, id.toString())`) without framework overhead or complex byte buffer conversions.
2. **Soft-Delete with Unique Constraint**:
   * Uses MySQL 8.0 **Virtual Generated Columns** (`active_... GENERATED ALWAYS AS (IF(deleted_at IS NULL, field, NULL)) VIRTUAL`).
   * Because MySQL allows multiple `NULL` values in `UNIQUE KEY`, deleted records (`deleted_at != NULL`) resolve to `NULL` and do not block reusing the email, table number, or voucher code.
3. **Financial Snapshot Immutability**:
   * Dish unit prices and voucher discounts at time of checkout are snapshotted into `invoice_items` and `invoices`. Future menu price updates never alter historical financial audits.
4. **Optimistic Locking**:
   * State-sensitive entities (`users`, `restaurant_tables`, `reservations`, `menu_items`, `orders`, `vouchers`, `loyalty_wallets`) contain `version BIGINT DEFAULT 0` to prevent lost updates in concurrent environments.

## 3. Execution
To run this schema in MySQL 8.0:
```bash
mysql -u root -p < database/schema.sql
```
