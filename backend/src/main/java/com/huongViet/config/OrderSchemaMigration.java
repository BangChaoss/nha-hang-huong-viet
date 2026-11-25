package com.huongViet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tự động kiểm tra và thêm cột reservation_id vào bảng orders nếu chưa tồn tại
 * để liên kết trực tiếp đơn gọi món tại bàn với đơn đặt bàn.
 */
@Component
@Order(2)
public class OrderSchemaMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OrderSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public OrderSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureReservationIdColumn();
    }

    private void ensureReservationIdColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'reservation_id'",
                Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN reservation_id CHAR(36) NULL AFTER staff_id");
                logger.info("Successfully added 'reservation_id' column to 'orders' table.");
            }
            ensureIndex("idx_orders_reservation", "CREATE INDEX idx_orders_reservation ON orders (reservation_id)");
            ensureForeignKey("fk_orders_reservation",
                "ALTER TABLE orders ADD CONSTRAINT fk_orders_reservation " +
                "FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE SET NULL");
        } catch (Exception ex) {
            logger.warn("Could not verify or auto-migrate 'reservation_id' on orders table: {}", ex.getMessage());
        }
    }

    private void ensureIndex(String indexName, String sql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = ?",
                Integer.class,
                indexName
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(sql);
            }
        } catch (Exception ex) {
            logger.debug("Could not ensure index '{}': {}", indexName, ex.getMessage());
        }
    }

    private void ensureForeignKey(String constraintName, String sql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND CONSTRAINT_NAME = ?",
                Integer.class,
                constraintName
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(sql);
            }
        } catch (Exception ex) {
            logger.debug("Could not ensure foreign key '{}': {}", constraintName, ex.getMessage());
        }
    }
}
