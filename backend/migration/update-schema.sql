ALTER TABLE daily_dish_sales_summaries
DROP
FOREIGN KEY fk_daily_dish_menu;

ALTER TABLE feedbacks
DROP
FOREIGN KEY fk_feedbacks_invoice;

ALTER TABLE feedbacks
DROP
FOREIGN KEY fk_feedbacks_user;

ALTER TABLE invoice_items
DROP
FOREIGN KEY fk_inv_items_dish;

ALTER TABLE invoice_items
DROP
FOREIGN KEY fk_inv_items_invoice;

ALTER TABLE invoices
DROP
FOREIGN KEY fk_invoices_customer;

ALTER TABLE invoices
DROP
FOREIGN KEY fk_invoices_order;

ALTER TABLE invoices
DROP
FOREIGN KEY fk_invoices_staff;

ALTER TABLE invoices
DROP
FOREIGN KEY fk_invoices_voucher;

ALTER TABLE order_item_audits
DROP
FOREIGN KEY fk_item_audit_item;

ALTER TABLE order_item_audits
DROP
FOREIGN KEY fk_item_audit_user;

ALTER TABLE kitchen_tickets
DROP
FOREIGN KEY fk_kitchen_order_item;

ALTER TABLE loyalty_transactions
DROP
FOREIGN KEY fk_loyalty_tx_invoice;

ALTER TABLE loyalty_transactions
DROP
FOREIGN KEY fk_loyalty_tx_wallet;

ALTER TABLE loyalty_wallets
DROP
FOREIGN KEY fk_loyalty_user;

ALTER TABLE menu_items
DROP
FOREIGN KEY fk_menu_category;

ALTER TABLE order_items
DROP
FOREIGN KEY fk_order_items_menu;

ALTER TABLE order_items
DROP
FOREIGN KEY fk_order_items_order;

ALTER TABLE orders
DROP
FOREIGN KEY fk_orders_staff;

ALTER TABLE orders
DROP
FOREIGN KEY fk_orders_table;

ALTER TABLE payments
DROP
FOREIGN KEY fk_payments_invoice;

ALTER TABLE menu_item_price_audits
DROP
FOREIGN KEY fk_price_audit_item;

ALTER TABLE menu_item_price_audits
DROP
FOREIGN KEY fk_price_audit_user;

ALTER TABLE password_reset_tokens
DROP
FOREIGN KEY fk_prt_user;

ALTER TABLE refresh_tokens
DROP
FOREIGN KEY fk_refresh_user;

ALTER TABLE reservation_audits
DROP
FOREIGN KEY fk_res_audit_new_tbl;

ALTER TABLE reservation_audits
DROP
FOREIGN KEY fk_res_audit_old_tbl;

ALTER TABLE reservation_audits
DROP
FOREIGN KEY fk_res_audit_res;

ALTER TABLE reservation_audits
DROP
FOREIGN KEY fk_res_audit_user;

ALTER TABLE reservations
DROP
FOREIGN KEY fk_res_customer;

ALTER TABLE reservations
DROP
FOREIGN KEY fk_res_table;

ALTER TABLE voucher_usages
DROP
FOREIGN KEY fk_vch_usage_customer;

ALTER TABLE voucher_usages
DROP
FOREIGN KEY fk_vch_usage_invoice;

ALTER TABLE voucher_usages
DROP
FOREIGN KEY fk_vch_usage_voucher;

ALTER TABLE voucher_audits
DROP
FOREIGN KEY fk_voucher_audit_user;

ALTER TABLE voucher_audits
DROP
FOREIGN KEY fk_voucher_audit_vch;

CREATE TABLE floor_plan_layouts
(
    id         CHAR(36)    NOT NULL,
    table_id   VARCHAR(36) NOT NULL,
    position_x DOUBLE NOT NULL,
    position_y DOUBLE NOT NULL,
    width DOUBLE NOT NULL,
    height DOUBLE NOT NULL,
    rotation DOUBLE NOT NULL,
    shape      VARCHAR(20) NOT NULL,
    updated_at datetime NULL,
    CONSTRAINT pk_floor_plan_layouts PRIMARY KEY (id)
);

CREATE TABLE loyalty_accounts
(
    id             VARCHAR(36)    NOT NULL,
    customer_phone VARCHAR(20)    NOT NULL,
    points_balance DECIMAL(14, 2) NOT NULL,
    updated_at     datetime       NOT NULL,
    CONSTRAINT pk_loyalty_accounts PRIMARY KEY (id)
);

CREATE TABLE loyalty_settings
(
    id                VARCHAR(36)   NOT NULL,
    earn_rate_percent DECIMAL(7, 3) NOT NULL,
    updated_at        datetime      NOT NULL,
    CONSTRAINT pk_loyalty_settings PRIMARY KEY (id)
);

CREATE TABLE payment_orders
(
    id                 VARCHAR(36)    NOT NULL,
    customer_phone     VARCHAR(20)    NOT NULL,
    subtotal           DECIMAL(14, 2) NOT NULL,
    discount_amount    DECIMAL(14, 2) NOT NULL,
    amount             DECIMAL(14, 2) NOT NULL,
    voucher_code       VARCHAR(50) NULL,
    status             VARCHAR(20)    NOT NULL,
    vnp_transaction_no VARCHAR(100) NULL,
    vnp_response_code  VARCHAR(10) NULL,
    points_awarded     DECIMAL(14, 2) NOT NULL,
    created_at         datetime       NOT NULL,
    paid_at            datetime NULL,
    CONSTRAINT pk_payment_orders PRIMARY KEY (id)
);

ALTER TABLE loyalty_accounts
    ADD CONSTRAINT uk_loyalty_phone UNIQUE (customer_phone);

CREATE INDEX idx_payment_orders_phone ON payment_orders (customer_phone);

CREATE INDEX idx_payment_orders_txn ON payment_orders (vnp_transaction_no);

DROP TABLE categories;

DROP TABLE daily_dish_sales_summaries;

DROP TABLE daily_revenue_summaries;

DROP TABLE feedbacks;

DROP TABLE invoice_items;

DROP TABLE invoices;

DROP TABLE kitchen_tickets;

DROP TABLE loyalty_transactions;

DROP TABLE loyalty_wallets;

DROP TABLE menu_item_price_audits;

DROP TABLE menu_items;

DROP TABLE order_item_audits;

DROP TABLE order_items;

DROP TABLE orders;

DROP TABLE payments;

DROP TABLE reservation_audits;

DROP TABLE reservations;

DROP TABLE voucher_audits;

ALTER TABLE users
DROP
COLUMN active_email;

ALTER TABLE users
DROP
COLUMN active_phone;

ALTER TABLE users
DROP
COLUMN active_username;

ALTER TABLE users
DROP
COLUMN `role`;

ALTER TABLE restaurant_tables
DROP
COLUMN active_table_number;

ALTER TABLE restaurant_tables
DROP
COLUMN status;

ALTER TABLE vouchers
DROP
COLUMN active_voucher_code;

ALTER TABLE vouchers
DROP
COLUMN discount_type;

ALTER TABLE table_zones
DROP
COLUMN active_zone_name;

ALTER TABLE voucher_usages
DROP
COLUMN customer_id;

ALTER TABLE password_reset_tokens
    MODIFY created_at datetime NULL;

ALTER TABLE users
    MODIFY created_at datetime NULL;

ALTER TABLE vouchers
    MODIFY created_at datetime NULL;

ALTER TABLE voucher_usages
    MODIFY customer_phone VARCHAR (20) NOT NULL;

ALTER TABLE vouchers
    ADD discount_type VARCHAR(255) NOT NULL;

ALTER TABLE voucher_usages
    MODIFY invoice_id CHAR (36) NULL;

ALTER TABLE users
    ADD `role` VARCHAR(255) NOT NULL;

ALTER TABLE restaurant_tables
    ADD status VARCHAR(20) NOT NULL;

ALTER TABLE refresh_tokens
    MODIFY token_hash VARCHAR (64);

ALTER TABLE vouchers
    MODIFY total_quantity INT NOT NULL;

ALTER TABLE users
    MODIFY updated_at datetime NULL;

ALTER TABLE vouchers
    MODIFY updated_at datetime NULL;

ALTER TABLE voucher_usages
    MODIFY used_at datetime NULL;
-- ==============================================================================
-- UC08: Book Table - Reservation & Audit Tables
-- ==============================================================================
CREATE TABLE IF NOT EXISTS reservations (
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
    special_requests TEXT NULL,
    status ENUM('PENDING_HOLD', 'PENDING_DEPOSIT', 'CONFIRMED', 'ARRIVED', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'PENDING_HOLD',
    cancellation_reason TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    KEY idx_reservations_check_slot (reservation_date, reservation_time, table_id, status),
    KEY idx_reservations_customer (customer_id, status),
    KEY idx_reservations_zone_date (reservation_date, status),
    CONSTRAINT fk_res_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_res_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (id) ON DELETE SET NULL,
    CONSTRAINT chk_res_party CHECK (party_size > 0),
    CONSTRAINT chk_res_deposit CHECK (deposit_amount >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reservation_audits (
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
