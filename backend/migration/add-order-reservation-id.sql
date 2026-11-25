-- Migration: Link orders to reservations via orders.reservation_id
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS reservation_id CHAR(36) NULL AFTER staff_id;

CREATE INDEX idx_orders_reservation ON orders (reservation_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE SET NULL;
