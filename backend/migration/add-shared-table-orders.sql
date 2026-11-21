ALTER TABLE orders
    ADD COLUMN party_size INT NULL,
    ADD COLUMN seats_released_at DATETIME(6) NULL;

ALTER TABLE kitchen_tickets
    ADD COLUMN is_additional BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_orders
    ADD COLUMN dining_order_id VARCHAR(36) NULL,
    ADD INDEX idx_payment_orders_dining_pending (dining_order_id, status, created_at);
