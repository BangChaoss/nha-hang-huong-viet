package com.huongViet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tự động kiểm tra và thêm cột loyalty_phone vào bảng invoices nếu chưa tồn tại
 * để hỗ trợ tách biệt giữa chủ sở hữu hóa đơn và số điện thoại tích điểm.
 */
@Component
@Order(1)
public class InvoiceSchemaMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public InvoiceSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureColumn("loyalty_phone", "ALTER TABLE invoices ADD COLUMN loyalty_phone VARCHAR(20) DEFAULT NULL AFTER customer_phone");
        ensureColumn("vip_fee_amount", "ALTER TABLE invoices ADD COLUMN vip_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER final_amount");
        ensureColumn("vip_deposit_paid", "ALTER TABLE invoices ADD COLUMN vip_deposit_paid DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER vip_fee_amount");
    }

    private void ensureColumn(String columnName, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(alterSql);
                logger.info("Successfully added '{}' column to 'invoices' table.", columnName);
            } else {
                logger.debug("'{}' column already exists in 'invoices' table.", columnName);
            }
        } catch (Exception ex) {
            logger.warn("Could not verify or auto-migrate '{}' on invoices table: {}", columnName, ex.getMessage());
        }
    }
}
