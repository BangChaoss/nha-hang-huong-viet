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
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'loyalty_phone'",
                Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE invoices ADD COLUMN loyalty_phone VARCHAR(20) DEFAULT NULL AFTER customer_phone");
                logger.info("Successfully added 'loyalty_phone' column to 'invoices' table.");
            } else {
                logger.debug("'loyalty_phone' column already exists in 'invoices' table.");
            }
        } catch (Exception ex) {
            logger.warn("Could not verify or auto-migrate 'loyalty_phone' on invoices table: {}", ex.getMessage());
        }
    }
}
