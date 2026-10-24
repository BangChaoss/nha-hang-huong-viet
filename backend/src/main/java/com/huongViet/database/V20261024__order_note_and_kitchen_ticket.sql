-- Hương Việt: bổ sung ghi chú món, snapshot giá và liên kết order cho bếp.
-- Áp dụng một lần trên cùng database dùng cho Spring Boot.
-- Bối cảnh: UC-15 (Create Food Order), UC-16 (View Food Orders), STT 80-83, 87, 89, 90.

-- 1. order_items: lưu giá tại thời điểm gọi món và ghi chú chế biến theo từng món.
ALTER TABLE order_items
    ADD COLUMN unit_price_snapshot DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER quantity;

ALTER TABLE order_items
    ADD COLUMN note VARCHAR(255) NULL AFTER unit_price_snapshot;

-- 2. kitchen_tickets: ghi chú an toàn (BR-36) và liên kết trực tiếp tới order để
--    màn KDS gom phiếu theo bàn mà không phải join ngược qua order_items.
ALTER TABLE kitchen_tickets
    ADD COLUMN note VARCHAR(255) NULL AFTER quantity;

ALTER TABLE kitchen_tickets
    ADD COLUMN order_id CHAR(36) NULL AFTER order_item_id;

ALTER TABLE kitchen_tickets
    ADD KEY idx_kds_order (order_id);

ALTER TABLE kitchen_tickets
    ADD CONSTRAINT fk_kitchen_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;
