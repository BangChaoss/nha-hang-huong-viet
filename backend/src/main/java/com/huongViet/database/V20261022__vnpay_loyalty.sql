-- Hương Việt: tables for VNPAY payments and loyalty points.
-- Apply once to the same database used by the Spring Boot application.
CREATE TABLE IF NOT EXISTS payment_orders (
  id CHAR(36) NOT NULL PRIMARY KEY,
  customer_phone VARCHAR(20) NOT NULL,
  subtotal DECIMAL(14,2) NOT NULL,
  discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  amount DECIMAL(14,2) NOT NULL,
  voucher_code VARCHAR(50) NULL,
  status VARCHAR(20) NOT NULL,
  vnp_transaction_no VARCHAR(100) NULL,
  vnp_response_code VARCHAR(10) NULL,
  points_awarded DECIMAL(14,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  paid_at DATETIME NULL,
  INDEX idx_payment_orders_txn (vnp_transaction_no),
  INDEX idx_payment_orders_phone (customer_phone)
);

CREATE TABLE IF NOT EXISTS loyalty_accounts (
  id CHAR(36) NOT NULL PRIMARY KEY,
  customer_phone VARCHAR(20) NOT NULL,
  points_balance DECIMAL(14,2) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_loyalty_phone (customer_phone)
);

CREATE TABLE IF NOT EXISTS loyalty_settings (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  earn_rate_percent DECIMAL(7,3) NOT NULL DEFAULT 5.000,
  updated_at DATETIME NOT NULL
);

INSERT INTO loyalty_settings (id, earn_rate_percent, updated_at)
SELECT 'default', 5.000, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM loyalty_settings WHERE id = 'default');
