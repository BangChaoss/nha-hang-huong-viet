ALTER TABLE reservations
    ADD COLUMN zone_id CHAR(36) NULL AFTER table_id;
