-- Lưu bàn POS cho mỗi giao dịch VNPAY để chốt đơn và giải phóng đúng bàn sau khi xác minh thanh toán.
ALTER TABLE payment_orders ADD COLUMN table_id VARCHAR(36) NULL;
