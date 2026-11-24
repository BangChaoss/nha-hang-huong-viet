-- ==============================================================================
-- DỌN DẸP & BỐ TRÍ LẠI SƠ ĐỒ BÀN 2D CANVAS HOÀN MỸ (CLEAN & AESTHETIC FLOOR PLAN)
-- Chuẩn hóa: 3 khu vực tách biệt hoàn toàn, 0 va chạm (0 collision), thẳng hàng thẳng lối
-- ==============================================================================

USE huong_viet_restaurant;
SET NAMES utf8mb4;

-- 1. ẨN/XÓA CÁC BÀN VÀ KHU VỰC THỬ NGHIỆM RÁC
UPDATE table_zones SET deleted_at = NOW() WHERE zone_name = 'dgsdag';
UPDATE restaurant_tables SET deleted_at = NOW() WHERE table_number IN ('1', '2', '13') OR zone_id IN (SELECT id FROM table_zones WHERE zone_name = 'dgsdag');
DELETE FROM floor_plan_layouts WHERE table_id IN (SELECT id FROM restaurant_tables WHERE deleted_at IS NOT NULL);

-- Đảm bảo 12 bàn chính thức đang hoạt động
UPDATE restaurant_tables SET deleted_at = NULL, status = 'AVAILABLE' WHERE table_number BETWEEN 'Bàn 01' AND 'Bàn 12';

-- 2. CẬP NHẬT TỌA ĐỘ VÀ KÍCH THƯỚC CHUẨN ĐẸP TRÊN SƠ ĐỒ SÀN (CANVAS LAYOUT)
-- ==============================================================================
-- KHU VỰC 1: TRONG NHÀ (Góc trên bên trái: X [60..460], Y [70..290])
-- ==============================================================================
-- Bàn 01 (4 khách, Vuông 90x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-01', id, 60, 70, 90, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 01' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 60, position_y = 70, width = 90, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 02 (4 khách, Vuông 90x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-02', id, 190, 70, 90, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 02' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 190, position_y = 70, width = 90, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 03 (6 khách, Chữ nhật 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-03', id, 320, 70, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 03' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 320, position_y = 70, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 04 (4 khách, Vuông 90x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-04', id, 60, 200, 90, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 04' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 60, position_y = 200, width = 90, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 05 (8 khách, Chữ nhật lớn 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-05', id, 190, 200, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 05' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 190, position_y = 200, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- ==============================================================================
-- KHU VỰC 2: SÂN VƯỜN (Góc trên bên phải: X [550..820], Y [70..290])
-- ==============================================================================
-- Bàn 06 (4 khách, Tròn sân vườn 90x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-06', id, 550, 70, 90, 90, 0, 'CIRCLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 06' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 550, position_y = 70, width = 90, height = 90, rotation = 0, shape = 'CIRCLE', updated_at = NOW();

-- Bàn 07 (6 khách, Chữ nhật 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-07', id, 680, 70, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 07' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 680, position_y = 70, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 08 (4 khách, Tròn sân vườn 90x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-08', id, 550, 200, 90, 90, 0, 'CIRCLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 08' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 550, position_y = 200, width = 90, height = 90, rotation = 0, shape = 'CIRCLE', updated_at = NOW();

-- Bàn 09 (8 khách, Chữ nhật lớn 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-09', id, 680, 200, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 09' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 680, position_y = 200, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- ==============================================================================
-- KHU VỰC 3: PHÒNG VIP (Góc dưới bên trái: X [60..500], Y [510..630])
-- ==============================================================================
-- Bàn 10 (10 khách, Bàn tiệc VIP chữ nhật 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-10', id, 60, 520, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 10' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 60, position_y = 520, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 11 (12 khách, Bàn đại tiệc VIP chữ nhật 140x90)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-11', id, 220, 520, 140, 90, 0, 'RECTANGLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 11' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 220, position_y = 520, width = 140, height = 90, rotation = 0, shape = 'RECTANGLE', updated_at = NOW();

-- Bàn 12 (10 khách, Bàn tròn tiệc xoay VIP 120x120)
INSERT INTO floor_plan_layouts (id, table_id, position_x, position_y, width, height, rotation, shape, updated_at)
SELECT 'layout-12', id, 380, 510, 120, 120, 0, 'CIRCLE', NOW()
FROM restaurant_tables WHERE table_number = 'Bàn 12' AND deleted_at IS NULL
ON DUPLICATE KEY UPDATE position_x = 380, position_y = 510, width = 120, height = 120, rotation = 0, shape = 'CIRCLE', updated_at = NOW();
