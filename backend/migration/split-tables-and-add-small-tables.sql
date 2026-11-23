-- ==============================================================================
-- MIGRATION: Tách bàn 6/8 chỗ thành bàn modular + thêm bàn 2 chỗ
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------
-- BƯỚC 1: Xóa floor_plan_layouts của bàn sẽ bị tách
-- -----------------------------------------------
DELETE FROM floor_plan_layouts WHERE table_id IN (
  SELECT id FROM restaurant_tables WHERE table_number IN ('Bàn 03', 'Bàn 05', 'Bàn 07', 'Bàn 09')
);

-- -----------------------------------------------
-- BƯỚC 2: Soft-delete bàn cũ (giữ history cho reservations cũ)
-- -----------------------------------------------
UPDATE restaurant_tables 
SET deleted_at = NOW(6)
WHERE table_number IN ('Bàn 03', 'Bàn 05', 'Bàn 07', 'Bàn 09')
  AND deleted_at IS NULL;

-- -----------------------------------------------
-- BƯỚC 3: Tạo bàn tách từ bàn 6/8 (ký hiệu A/B)
-- -----------------------------------------------

-- Bàn 03 (6 chỗ, Trong Nhà) → 03A (2) + 03B (4)
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000003-aaaa-4000-8000-000000000001', id, 'Bàn 03A', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000003-bbbb-4000-8000-000000000002', id, 'Bàn 03B', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

-- Bàn 05 (8 chỗ, Trong Nhà) → 05A (4) + 05B (4)
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000005-aaaa-4000-8000-000000000003', id, 'Bàn 05A', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000005-bbbb-4000-8000-000000000004', id, 'Bàn 05B', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

-- Bàn 07 (6 chỗ, Sân Vườn) → 07A (2) + 07B (4)
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000007-aaaa-4000-8000-000000000005', id, 'Bàn 07A', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000007-bbbb-4000-8000-000000000006', id, 'Bàn 07B', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

-- Bàn 09 (8 chỗ, Sân Vườn) → 09A (4) + 09B (4)
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000009-aaaa-4000-8000-000000000007', id, 'Bàn 09A', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'a0000009-bbbb-4000-8000-000000000008', id, 'Bàn 09B', 4, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

-- -----------------------------------------------
-- BƯỚC 4: Thêm bàn 2 chỗ mới
-- -----------------------------------------------

-- 3 bàn 2 chỗ Trong Nhà
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'b0000013-0000-4000-8000-000000000001', id, 'Bàn 13', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'b0000014-0000-4000-8000-000000000002', id, 'Bàn 14', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'b0000015-0000-4000-8000-000000000003', id, 'Bàn 15', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Trong Nhà' LIMIT 1;

-- 2 bàn 2 chỗ Sân Vườn
INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'b0000016-0000-4000-8000-000000000004', id, 'Bàn 16', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

INSERT INTO restaurant_tables (id, zone_id, table_number, capacity, status, version) 
SELECT 'b0000017-0000-4000-8000-000000000005', id, 'Bàn 17', 2, 'AVAILABLE', 0 
FROM table_zones WHERE zone_name = 'Sân Vườn' LIMIT 1;

-- -----------------------------------------------
-- BƯỚC 5: Thêm floor_plan_layouts cho bàn mới
-- -----------------------------------------------

-- Bàn 03A, 03B (thay thế Bàn 03 ở vị trí x=300, y=70)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000003-aaaa-4000-8000-000000000001', 'a0000003-aaaa-4000-8000-000000000001', 
   300, 70, 60, 90, 0, 'RECTANGLE'),
  ('f0000003-bbbb-4000-8000-000000000002', 'a0000003-bbbb-4000-8000-000000000002', 
   370, 70, 80, 90, 0, 'RECTANGLE');

-- Bàn 05A, 05B (thay thế Bàn 05 ở vị trí x=180, y=190)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000005-aaaa-4000-8000-000000000003', 'a0000005-aaaa-4000-8000-000000000003', 
   180, 190, 80, 90, 0, 'RECTANGLE'),
  ('f0000005-bbbb-4000-8000-000000000004', 'a0000005-bbbb-4000-8000-000000000004', 
   270, 190, 80, 90, 0, 'RECTANGLE');

-- Bàn 07A, 07B (thay thế Bàn 07 ở vị trí x=700, y=70 trong Sân Vườn)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000007-aaaa-4000-8000-000000000005', 'a0000007-aaaa-4000-8000-000000000005', 
   700, 70, 60, 90, 0, 'RECTANGLE'),
  ('f0000007-bbbb-4000-8000-000000000006', 'a0000007-bbbb-4000-8000-000000000006', 
   770, 70, 80, 90, 0, 'RECTANGLE');

-- Bàn 09A, 09B (thay thế Bàn 09 ở vị trí x=700, y=190 trong Sân Vườn)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000009-aaaa-4000-8000-000000000007', 'a0000009-aaaa-4000-8000-000000000007', 
   700, 190, 80, 90, 0, 'RECTANGLE'),
  ('f0000009-bbbb-4000-8000-000000000008', 'a0000009-bbbb-4000-8000-000000000008', 
   790, 190, 80, 90, 0, 'RECTANGLE');

-- Bàn 13, 14, 15 (Trong Nhà - vị trí x=480)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000013-0000-4000-8000-000000000001', 'b0000013-0000-4000-8000-000000000001', 
   480, 70, 60, 60, 0, 'CIRCLE'),
  ('f0000014-0000-4000-8000-000000000002', 'b0000014-0000-4000-8000-000000000002', 
   480, 150, 60, 60, 0, 'CIRCLE'),
  ('f0000015-0000-4000-8000-000000000003', 'b0000015-0000-4000-8000-000000000003', 
   480, 230, 60, 60, 0, 'CIRCLE');

-- Bàn 16, 17 (Sân Vườn - vị trí x=900)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape) VALUES
  ('f0000016-0000-4000-8000-000000000004', 'b0000016-0000-4000-8000-000000000004', 
   900, 70, 60, 60, 0, 'CIRCLE'),
  ('f0000017-0000-4000-8000-000000000005', 'b0000017-0000-4000-8000-000000000005', 
   900, 160, 60, 60, 0, 'CIRCLE');

SET FOREIGN_KEY_CHECKS = 1;
