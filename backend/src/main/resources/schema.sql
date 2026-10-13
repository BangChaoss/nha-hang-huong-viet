-- ==============================================================================
-- HƯƠNG VIỆT RESTAURANT MANAGEMENT SYSTEM - DATABASE SCHEMA
-- Target Database: MySQL 8.0+
-- Storage Engine: InnoDB (utf8mb4 / utf8mb4_unicode_ci)
-- Primary Key Strategy: UUIDv7 as CHAR(36) (Optimized for Pure Java 22 + JDBC)
-- Soft Delete Strategy: Virtual Generated Column + Unique Key
-- Financial Integrity: Immutable Price & Voucher Snapshots
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS huong_viet_restaurant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE huong_viet_restaurant;

-- Disable foreign key checks during schema creation/migration
SET FOREIGN_KEY_CHECKS = 0;

-- Drop tables in reverse dependency order if recreating
DROP TABLE IF EXISTS daily_dish_sales_summaries;
DROP TABLE IF EXISTS daily_revenue_summaries;
DROP TABLE IF EXISTS feedbacks;
DROP TABLE IF EXISTS loyalty_transactions;
DROP TABLE IF EXISTS loyalty_wallets;
DROP TABLE IF EXISTS voucher_usages;
DROP TABLE IF EXISTS voucher_audits;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS invoice_items;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS vouchers;
DROP TABLE IF EXISTS kitchen_tickets;
DROP TABLE IF EXISTS order_item_audits;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS menu_item_price_audits;
DROP TABLE IF EXISTS menu_items;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS reservation_audits;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS restaurant_tables;
DROP TABLE IF EXISTS table_zones;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- 1. IDENTITY & USER MANAGEMENT (users)
-- ==============================================================================
CREATE TABLE users (
    id CHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('CUSTOMER', 'STAFF', 'CHEF', 'MANAGER', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    -- Virtual columns for partial unique constraints on active records
    active_username VARCHAR(50) GENERATED ALWAYS AS (IF(deleted_at IS NULL, username, NULL)) VIRTUAL,
    active_email VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) VIRTUAL,
    active_phone VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, phone_number, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_active_username (active_username),
    UNIQUE KEY uq_users_active_email (active_email),
    UNIQUE KEY uq_users_active_phone (active_phone),
    KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 2. TABLE ZONES (table_zones)
-- ==============================================================================
CREATE TABLE table_zones (
    id CHAR(36) NOT NULL,
    zone_name VARCHAR(50) NOT NULL,
    is_vip BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME(6) NULL,

    active_zone_name VARCHAR(50) GENERATED ALWAYS AS (IF(deleted_at IS NULL, zone_name, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_zones_active_name (active_zone_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 3. RESTAURANT TABLES (restaurant_tables)
-- ==============================================================================
CREATE TABLE restaurant_tables (
    id CHAR(36) NOT NULL,
    zone_id CHAR(36) NOT NULL,
    table_number VARCHAR(20) NOT NULL,
    capacity INT NOT NULL,
    status ENUM('AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,

    active_table_number VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, table_number, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_tables_active_number (active_table_number),
    KEY idx_tables_zone_status (zone_id, status),
    CONSTRAINT fk_tables_zone FOREIGN KEY (zone_id) REFERENCES table_zones (id) ON DELETE RESTRICT,
    CONSTRAINT chk_tables_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 4. TABLE RESERVATIONS (reservations)
-- ==============================================================================
CREATE TABLE reservations (
    id CHAR(36) NOT NULL,
    customer_id CHAR(36) NULL,
    guest_name VARCHAR(100) NOT NULL,
    guest_phone VARCHAR(20) NOT NULL,
    guest_email VARCHAR(100) NULL,
    table_id CHAR(36) NULL,
    party_size INT NOT NULL,
    reservation_date DATE NOT NULL,
    reservation_time TIME NOT NULL,
    deposit_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status ENUM('PENDING_HOLD', 'PENDING_DEPOSIT', 'CONFIRMED', 'ARRIVED', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'PENDING_HOLD',
    cancellation_reason TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    KEY idx_reservations_check_slot (reservation_date, reservation_time, table_id, status),
    KEY idx_reservations_customer (customer_id, status),
    CONSTRAINT fk_res_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_res_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (id) ON DELETE SET NULL,
    CONSTRAINT chk_res_party CHECK (party_size > 0),
    CONSTRAINT chk_res_deposit CHECK (deposit_amount >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 5. RESERVATION AUDIT TRAIL (reservation_audits)
-- ==============================================================================
CREATE TABLE reservation_audits (
    id CHAR(36) NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    old_status ENUM('PENDING_HOLD', 'PENDING_DEPOSIT', 'CONFIRMED', 'ARRIVED', 'CANCELLED', 'NO_SHOW') NOT NULL,
    new_status ENUM('PENDING_HOLD', 'PENDING_DEPOSIT', 'CONFIRMED', 'ARRIVED', 'CANCELLED', 'NO_SHOW') NOT NULL,
    old_table_id CHAR(36) NULL,
    new_table_id CHAR(36) NULL,
    reason TEXT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_res_audit_res (reservation_id),
    KEY idx_res_audit_user (changed_by),
    CONSTRAINT fk_res_audit_res FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT fk_res_audit_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_res_audit_old_tbl FOREIGN KEY (old_table_id) REFERENCES restaurant_tables (id) ON DELETE SET NULL,
    CONSTRAINT fk_res_audit_new_tbl FOREIGN KEY (new_table_id) REFERENCES restaurant_tables (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 6. MENU CATEGORIES (categories)
-- ==============================================================================
CREATE TABLE categories (
    id CHAR(36) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,

    active_category_name VARCHAR(50) GENERATED ALWAYS AS (IF(deleted_at IS NULL, category_name, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_active_name (active_category_name),
    KEY idx_categories_order (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 7. MENU ITEMS (menu_items)
-- ==============================================================================
CREATE TABLE menu_items (
    id CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    current_price DECIMAL(12, 2) NOT NULL,
    image_url VARCHAR(255) NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    active_item_name VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, item_name, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_menu_active_name (active_item_name),
    KEY idx_menu_category_avail (category_id, is_available),
    CONSTRAINT fk_menu_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_menu_price CHECK (current_price >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 8. MENU ITEM PRICE AUDIT (menu_item_price_audits)
-- ==============================================================================
CREATE TABLE menu_item_price_audits (
    id CHAR(36) NOT NULL,
    menu_item_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    old_price DECIMAL(12, 2) NOT NULL,
    new_price DECIMAL(12, 2) NOT NULL,
    change_reason TEXT NULL,
    effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_price_audit_item (menu_item_id),
    KEY idx_price_audit_user (changed_by),
    CONSTRAINT fk_price_audit_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_price_audit_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 9. DINING ORDERS (orders)
-- ==============================================================================
CREATE TABLE orders (
    id CHAR(36) NOT NULL,
    table_id CHAR(36) NOT NULL,
    staff_id CHAR(36) NULL,
    order_type ENUM('DINE_IN', 'TAKE_AWAY') NOT NULL DEFAULT 'DINE_IN',
    status ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'IN_PROGRESS',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    KEY idx_orders_active_table (table_id, status),
    KEY idx_orders_staff (staff_id),
    CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_staff FOREIGN KEY (staff_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 10. ORDER ITEMS (order_items)
-- ==============================================================================
CREATE TABLE order_items (
    id CHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    menu_item_id CHAR(36) NOT NULL,
    quantity INT NOT NULL,
    cooking_status ENUM('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'QUEUED',
    cancel_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_items_order_status (order_id, cooking_status),
    KEY idx_order_items_menu (menu_item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 11. ORDER ITEM AUDIT TRAIL (order_item_audits)
-- ==============================================================================
CREATE TABLE order_item_audits (
    id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    action VARCHAR(30) NOT NULL,
    old_quantity INT NOT NULL,
    new_quantity INT NOT NULL,
    old_status ENUM('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL,
    new_status ENUM('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL,
    reason TEXT NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_item_audit_item (order_item_id),
    KEY idx_order_item_audit_user (changed_by),
    CONSTRAINT fk_item_audit_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_audit_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 12. KITCHEN TICKETS / KDS QUEUE (kitchen_tickets)
-- ==============================================================================
CREATE TABLE kitchen_tickets (
    id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    table_number VARCHAR(20) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    status ENUM('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'QUEUED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_kitchen_order_item (order_item_id),
    KEY idx_kds_live_queue (status, priority, created_at),
    CONSTRAINT fk_kitchen_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 13. PROMOTIONAL VOUCHERS (vouchers)
-- ==============================================================================
CREATE TABLE vouchers (
    id CHAR(36) NOT NULL,
    voucher_code VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NULL,
    discount_type ENUM('PERCENTAGE', 'FIXED_AMOUNT') NOT NULL,
    discount_value DECIMAL(12, 2) NOT NULL,
    max_discount_amount DECIMAL(12, 2) NULL,
    min_order_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    start_date DATETIME(6) NOT NULL,
    end_date DATETIME(6) NOT NULL,
    total_quantity INT NULL,
    used_quantity INT NOT NULL DEFAULT 0,
    max_uses_per_customer INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    active_voucher_code VARCHAR(50) GENERATED ALWAYS AS (IF(deleted_at IS NULL, voucher_code, NULL)) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_vouchers_active_code (active_voucher_code),
    KEY idx_vouchers_available (is_active, start_date, end_date),
    CONSTRAINT chk_voucher_value CHECK (discount_value > 0.00),
    CONSTRAINT chk_voucher_qty CHECK (total_quantity IS NULL OR used_quantity <= total_quantity),
    CONSTRAINT chk_voucher_dates CHECK (end_date > start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 14. VOUCHER AUDIT TRAIL (voucher_audits)
-- ==============================================================================
CREATE TABLE voucher_audits (
    id CHAR(36) NOT NULL,
    voucher_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_value JSON NULL,
    new_value JSON NULL,
    reason TEXT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_voucher_audit_vch (voucher_id),
    KEY idx_voucher_audit_user (changed_by),
    CONSTRAINT fk_voucher_audit_vch FOREIGN KEY (voucher_id) REFERENCES vouchers (id) ON DELETE CASCADE,
    CONSTRAINT fk_voucher_audit_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 15. INVOICES & FINANCIAL SNAPSHOTS (invoices)
-- ==============================================================================
CREATE TABLE invoices (
    id CHAR(36) NOT NULL,
    invoice_code VARCHAR(50) NOT NULL,
    order_id CHAR(36) NOT NULL,
    customer_id CHAR(36) NULL,
    staff_id CHAR(36) NOT NULL,
    voucher_id CHAR(36) NULL,
    voucher_code_snapshot VARCHAR(50) NULL,
    voucher_discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    subtotal_amount DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    final_amount DECIMAL(12, 2) NOT NULL,
    deposit_deducted DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    loyalty_points_earned INT NOT NULL DEFAULT 0,
    status ENUM('UNPAID', 'PAID', 'REFUNDED') NOT NULL DEFAULT 'UNPAID',
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_invoices_code (invoice_code),
    UNIQUE KEY uq_invoices_order (order_id),
    KEY idx_invoices_customer (customer_id),
    KEY idx_invoices_staff (staff_id),
    KEY idx_invoices_voucher (voucher_id),
    KEY idx_invoices_status_created (status, created_at),
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_invoices_staff FOREIGN KEY (staff_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id) ON DELETE SET NULL,
    CONSTRAINT chk_invoices_amounts CHECK (final_amount >= 0.00 AND subtotal_amount >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 16. INVOICE ITEMS SNAPSHOT (invoice_items)
-- ==============================================================================
CREATE TABLE invoice_items (
    id CHAR(36) NOT NULL,
    invoice_id CHAR(36) NOT NULL,
    dish_id CHAR(36) NOT NULL,
    dish_name_snapshot VARCHAR(100) NOT NULL,
    unit_price_snapshot DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_inv_items_invoice (invoice_id),
    KEY idx_inv_items_dish (dish_id),
    CONSTRAINT fk_inv_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_items_dish FOREIGN KEY (dish_id) REFERENCES menu_items (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inv_items_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 17. SETTLEMENT PAYMENTS (payments)
-- ==============================================================================
CREATE TABLE payments (
    id CHAR(36) NOT NULL,
    invoice_id CHAR(36) NOT NULL,
    payment_method ENUM('CASH', 'CREDIT_CARD', 'BANK_TRANSFER') NOT NULL,
    transaction_reference VARCHAR(100) NULL,
    amount_paid DECIMAL(12, 2) NOT NULL,
    payment_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_payments_invoice (invoice_id),
    KEY idx_payments_method (payment_method),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_payments_amount CHECK (amount_paid > 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 18. VOUCHER USAGE HISTORY (voucher_usages)
-- ==============================================================================
CREATE TABLE voucher_usages (
    id CHAR(36) NOT NULL,
    voucher_id CHAR(36) NOT NULL,
    customer_id CHAR(36) NULL,
    invoice_id CHAR(36) NOT NULL,
    discount_applied DECIMAL(12, 2) NOT NULL,
    used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_invoice_voucher (invoice_id, voucher_id),
    KEY idx_vch_usage_voucher_customer (voucher_id, customer_id),
    CONSTRAINT fk_vch_usage_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id) ON DELETE CASCADE,
    CONSTRAINT fk_vch_usage_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_vch_usage_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_vch_usage_discount CHECK (discount_applied > 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 19. CUSTOMER LOYALTY WALLET (loyalty_wallets)
-- ==============================================================================
CREATE TABLE loyalty_wallets (
    id CHAR(36) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    point_balance INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_loyalty_customer (customer_id),
    CONSTRAINT fk_loyalty_user FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_loyalty_balance CHECK (point_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 20. LOYALTY POINT TRANSACTION LEDGER (loyalty_transactions)
-- ==============================================================================
CREATE TABLE loyalty_transactions (
    id CHAR(36) NOT NULL,
    wallet_id CHAR(36) NOT NULL,
    invoice_id CHAR(36) NULL,
    transaction_type ENUM('EARN_5_PERCENT', 'REDEEM', 'EXPIRE', 'ADJUSTMENT') NOT NULL,
    points INT NOT NULL,
    balance_after INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_loyalty_invoice_earn (invoice_id, transaction_type),
    KEY idx_loyalty_tx_wallet (wallet_id),
    CONSTRAINT fk_loyalty_tx_wallet FOREIGN KEY (wallet_id) REFERENCES loyalty_wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_loyalty_tx_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 21. CUSTOMER FEEDBACK & REVIEWS (feedbacks)
-- ==============================================================================
CREATE TABLE feedbacks (
    id CHAR(36) NOT NULL,
    customer_id CHAR(36) NULL,
    guest_name VARCHAR(100) NULL,
    guest_phone VARCHAR(20) NULL,
    invoice_id CHAR(36) NULL,
    rating INT NOT NULL,
    comment TEXT NULL,
    staff_response TEXT NULL,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    KEY idx_feedbacks_rating_date (rating, created_at),
    KEY idx_feedbacks_customer (customer_id),
    KEY idx_feedbacks_invoice (invoice_id),
    CONSTRAINT fk_feedbacks_user FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_feedbacks_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE SET NULL,
    CONSTRAINT chk_feedbacks_rating CHECK (rating >= 1 AND rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 22. DAILY REVENUE SUMMARY READ MODEL (daily_revenue_summaries)
-- ==============================================================================
CREATE TABLE daily_revenue_summaries (
    summary_date DATE NOT NULL,
    total_revenue DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total_orders INT NOT NULL DEFAULT 0,
    total_customers_served INT NOT NULL DEFAULT 0,
    total_discount_given DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_voucher_discount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_deposit_collected DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 23. DAILY DISH SALES SUMMARY READ MODEL (daily_dish_sales_summaries)
-- ==============================================================================
CREATE TABLE daily_dish_sales_summaries (
    id CHAR(36) NOT NULL,
    summary_date DATE NOT NULL,
    dish_id CHAR(36) NOT NULL,
    dish_name VARCHAR(100) NOT NULL,
    total_quantity_sold INT NOT NULL DEFAULT 0,
    total_sales_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

    PRIMARY KEY (id),
    UNIQUE KEY uq_daily_dish_date_dish (summary_date, dish_id),
    KEY idx_daily_dish_rank (summary_date, total_quantity_sold),
    CONSTRAINT fk_daily_dish_menu FOREIGN KEY (dish_id) REFERENCES menu_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
