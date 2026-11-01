-- Migration: Add merged_table_ids to reservations table for multi-table merging
ALTER TABLE reservations
    ADD COLUMN merged_table_ids VARCHAR(500) NULL AFTER table_id;
