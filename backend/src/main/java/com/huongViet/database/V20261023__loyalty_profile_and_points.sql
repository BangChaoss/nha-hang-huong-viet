-- Apply to the same MySQL database as Spring Boot. Do not run schema.sql again on production.
ALTER TABLE loyalty_accounts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS points_used DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS points_deducted BOOLEAN NOT NULL DEFAULT FALSE;

-- The existing loyalty_settings table and its default rate are reused.
INSERT INTO loyalty_settings (id, earn_rate_percent, updated_at)
SELECT 'default', 5.000, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM loyalty_settings WHERE id = 'default');
