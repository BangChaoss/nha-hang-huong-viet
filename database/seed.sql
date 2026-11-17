-- ==============================================================================
-- HƯƠNG VIỆT RESTAURANT — DỮ LIỆU MẪU (SEED)
-- ==============================================================================
-- Script này nạp dữ liệu demo vào database đã được tạo bởi `database/schema.sql`.
--
--   mysql -u root -p < database/schema.sql
--   mysql -u root -p < database/seed.sql
--
-- LƯU Ý:
--   * Script chỉ chạy MỘT LẦN trên database rỗng. Chạy lại sẽ báo lỗi trùng khóa.
--   * Mật khẩu trong file này là mật khẩu demo công khai — không dùng cho môi trường thật.
--   * Backend cũng tự seed khi khởi động lần đầu (xem `config/AdminSeeder`,
--     `config/RestaurantSeedData`, `config/DemoAccountSeeder`, `menu/MenuSeeder`),
--     nên nếu bạn đã khởi động app thì không cần chạy file này.
--
-- TÀI KHOẢN DEMO (đăng nhập tại http://localhost:5173 hoặc :8081/login):
--
--   Username     | Mật khẩu        | Vai trò  | Màn hình sau khi đăng nhập
--   -------------|-----------------|----------|---------------------------
--   admin        | Admin@123456    | ADMIN    | /admin
--   quanly       | QuanLy@12345    | MANAGER  | /manager
--   nhanvien     | NhanVien@12345  | STAFF    | /staff
--   nhanvien2    | NhanVien@12345  | STAFF    | /staff
--   dau_bep      | DauBep@12345    | CHEF     | /chef
--   khachhang    | KhachHang@12345 | CUSTOMER | /
--   khachvip     | KhachHang@12345 | CUSTOMER | /
--
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;
USE huong_viet_restaurant;


-- --------------------------------------------------------------------------
-- Tài khoản: quản trị, nhân viên, bếp, khách hàng demo
-- --------------------------------------------------------------------------
INSERT INTO `users` (`id`, `username`, `email`, `phone_number`, `password_hash`, `role`, `status`, `version`, `created_at`, `updated_at`, `deleted_at`) VALUES
  ('00000000-0000-0000-0000-000000000000','system','system@huongviet.local','0000000000','$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/YxV9Rvh7e1t3fQ7x8.2nFpA6.t6q','ADMIN','ACTIVE',0,'2026-10-23 17:09:56.167842','2026-10-23 17:09:56.167842',NULL),
  ('365be270-166a-4d6b-92f9-896c513ee0c9','khachvip','vip@huongviet.vn','0902000002','$2a$10$.mt4PiCxf5kAaM2gXJtKTuxvowuQHIMI0BZQcWaW4jPOq5mtl.xtK','CUSTOMER','ACTIVE',0,'2026-10-24 02:15:26.583291','2026-10-24 02:15:26.583291',NULL),
  ('3cbc6df6-a84d-4a1b-94a3-468d8bdcc9d6','nhanvien','nhanvien@huongviet.vn','0901000002','$2a$10$xfRLEhPwwwLCkpOp5afXQeSz7bz9la1SDUDG91sB0qqBtOaYyQz9i','STAFF','ACTIVE',0,'2026-10-24 02:15:25.706578','2026-10-24 02:15:25.706578',NULL),
  ('6b724c44-b82d-4808-8dff-2b44fb5f548e','dau_bep','daubep@huongviet.vn','0901000004','$2a$10$jh5li6fhx9II7RFiOlpUeu1CjMCS94ZjBOmsHCR74IpRAIQURIpDi','CHEF','ACTIVE',0,'2026-10-24 02:15:26.258728','2026-10-24 02:15:26.258728',NULL),
  ('6f9a3e2a-8e43-4f78-89c0-34212cbebfe7','quanly','quanly@huongviet.vn','0901000001','$2a$10$YbM.bv6Mv3n6Et7pa40q0emWZzZTmpY9fpKLpJLQdNsa7IR5Ok8N.','MANAGER','ACTIVE',0,'2026-10-24 02:15:25.449694','2026-10-24 02:15:25.449694',NULL),
  ('7af2018d-70bc-4023-a75a-a67a7aac4060','nhanvien2','nhanvien2@huongviet.vn','0901000003','$2a$10$l6mst7jbvY/QUc9IjH903uSh2vrjUg.ChMkyGD93Cl8rvRm4FJnHC','STAFF','ACTIVE',0,'2026-10-24 02:15:25.944946','2026-10-24 02:15:25.944946',NULL),
  ('cbe8a239-3dca-4267-b5dd-3ea6bb19d57a','admin','admin@huongviet.com','0999999999','$2a$10$D9osRLWxmk7twrlfOEkKx.nZg9HqeNh1qVgdAVShJRH2ZBkHqbFve','ADMIN','ACTIVE',0,'2026-10-23 17:09:56.133664','2026-10-23 17:09:56.133664',NULL),
  ('cf3c1975-81aa-44d1-a67d-dde5809069e9','khachhang','khach@huongviet.vn','0902000001','$2a$10$yKMP4OOQH1Mt4SLVOQJK1ux25oSFQjaINWmTNzWJ7OShlLf9DFSnC','CUSTOMER','ACTIVE',0,'2026-10-24 02:15:26.458270','2026-10-24 02:15:26.458270',NULL)
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  email = VALUES(email),
  phone_number = VALUES(phone_number),
  password_hash = VALUES(password_hash),
  role = VALUES(role),
  status = VALUES(status);

-- --------------------------------------------------------------------------
-- Khu vực nhà hàng
-- --------------------------------------------------------------------------
INSERT INTO `table_zones` (`id`, `zone_name`, `is_vip`, `deleted_at`) VALUES
  ('18c70d37-d685-4ccf-bedd-c5285aa91298','Trong Nhà',0,NULL),
  ('4cbfed1d-2165-4c64-a798-85ef0412a50a','Sân Vườn',0,NULL),
  ('d0bf5211-7285-4d61-9f54-dc52613b669f','Phòng VIP',1,NULL)
ON DUPLICATE KEY UPDATE
  zone_name = VALUES(zone_name),
  is_vip = VALUES(is_vip),
  deleted_at = NULL;

-- --------------------------------------------------------------------------
-- 21 bàn modular thuộc 3 khu vực (tối đa 4 chỗ/bàn cho non-VIP, bàn VIP giữ nguyên)
-- --------------------------------------------------------------------------
INSERT INTO `restaurant_tables` (`id`, `zone_id`, `table_number`, `capacity`, `status`, `version`, `deleted_at`) VALUES
  -- Trong Nhà (30 chỗ)
  ('89366f6c-b97a-4c69-8c86-530e8ee8ecdd','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 01',4,'AVAILABLE',0,NULL),
  ('c0bbc469-74d3-47c2-b79a-480e9a9f2638','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 02',4,'AVAILABLE',0,NULL),
  ('a0000003-aaaa-4000-8000-000000000001','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 03A',2,'AVAILABLE',0,NULL),
  ('a0000003-bbbb-4000-8000-000000000002','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 03B',4,'AVAILABLE',0,NULL),
  ('3463683e-3edb-4168-b5c7-82d53fdd1404','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 04',4,'AVAILABLE',0,NULL),
  ('a0000005-aaaa-4000-8000-000000000003','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 05A',4,'AVAILABLE',0,NULL),
  ('a0000005-bbbb-4000-8000-000000000004','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 05B',4,'AVAILABLE',0,NULL),
  ('b0000013-0000-4000-8000-000000000001','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 13',2,'AVAILABLE',0,NULL),
  ('b0000014-0000-4000-8000-000000000002','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 14',2,'AVAILABLE',0,NULL),
  ('b0000015-0000-4000-8000-000000000003','18c70d37-d685-4ccf-bedd-c5285aa91298','Bàn 15',2,'AVAILABLE',0,NULL),
  -- Sân Vườn (22 chỗ)
  ('122f300e-27ed-4a39-9858-9fd1b731d8f8','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 06',4,'AVAILABLE',0,NULL),
  ('a0000007-aaaa-4000-8000-000000000005','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 07A',2,'AVAILABLE',0,NULL),
  ('a0000007-bbbb-4000-8000-000000000006','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 07B',4,'AVAILABLE',0,NULL),
  ('285f58f0-1d15-4d3c-b454-6aa9421bb698','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 08',4,'AVAILABLE',0,NULL),
  ('a0000009-aaaa-4000-8000-000000000007','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 09A',4,'AVAILABLE',0,NULL),
  ('a0000009-bbbb-4000-8000-000000000008','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 09B',4,'AVAILABLE',0,NULL),
  ('b0000016-0000-4000-8000-000000000004','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 16',2,'AVAILABLE',0,NULL),
  ('b0000017-0000-4000-8000-000000000005','4cbfed1d-2165-4c64-a798-85ef0412a50a','Bàn 17',2,'AVAILABLE',0,NULL),
  -- Phòng VIP (32 chỗ)
  ('5cd8549d-5ae8-47ac-9b9a-f967fc410122','d0bf5211-7285-4d61-9f54-dc52613b669f','Bàn 10',10,'AVAILABLE',0,NULL),
  ('6d2b499b-4ecd-450b-8bff-cc0f210a89f0','d0bf5211-7285-4d61-9f54-dc52613b669f','Bàn 11',12,'AVAILABLE',0,NULL),
  ('91c6b356-c194-4610-b084-e79610aa2386','d0bf5211-7285-4d61-9f54-dc52613b669f','Bàn 12',10,'AVAILABLE',0,NULL)
ON DUPLICATE KEY UPDATE
  zone_id = VALUES(zone_id),
  table_number = VALUES(table_number),
  capacity = VALUES(capacity),
  status = 'AVAILABLE',
  deleted_at = NULL;

-- --------------------------------------------------------------------------
-- Toạ độ bố trí bàn trên sơ đồ sàn (Floor Plan Layouts)
-- Chuẩn hóa theo clean_and_organize_floor_plan.sql: 3 khu vực tách biệt hoàn toàn, 0 va chạm
-- --------------------------------------------------------------------------
INSERT INTO `floor_plan_layouts` (`id`, `table_id`, `position_x`, `position_y`, `width`, `height`, `rotation`, `shape`, `updated_at`) VALUES
  -- Khu vực 1: Trong Nhà (Góc trên bên trái: X in [60..540], Y in [70..290])
  ('95ee8895-9b60-49e8-b8ed-40b92c0fdf9b','89366f6c-b97a-4c69-8c86-530e8ee8ecdd',60,70,90,90,0,'RECTANGLE','2026-10-23 17:09:56.256379'),
  ('e1617cfc-0587-4ddf-b70c-6a08bc0d21a2','c0bbc469-74d3-47c2-b79a-480e9a9f2638',180,70,90,90,0,'RECTANGLE','2026-10-23 17:09:56.260433'),
  ('f0000003-aaaa-4000-8000-000000000001','a0000003-aaaa-4000-8000-000000000001',300,70,60,90,0,'RECTANGLE','2026-10-23 17:09:56.264110'),
  ('f0000003-bbbb-4000-8000-000000000002','a0000003-bbbb-4000-8000-000000000002',370,70,80,90,0,'RECTANGLE','2026-10-23 17:09:56.264110'),
  ('a607a1d1-81d9-4ccf-8990-137b7403b792','3463683e-3edb-4168-b5c7-82d53fdd1404',60,190,90,90,0,'RECTANGLE','2026-10-23 17:09:56.268462'),
  ('f0000005-aaaa-4000-8000-000000000003','a0000005-aaaa-4000-8000-000000000003',180,190,80,90,0,'RECTANGLE','2026-10-23 17:09:56.272557'),
  ('f0000005-bbbb-4000-8000-000000000004','a0000005-bbbb-4000-8000-000000000004',270,190,80,90,0,'RECTANGLE','2026-10-23 17:09:56.272557'),
  ('f0000013-0000-4000-8000-000000000001','b0000013-0000-4000-8000-000000000001',480,70,60,60,0,'CIRCLE','2026-10-23 17:09:56.276377'),
  ('f0000014-0000-4000-8000-000000000002','b0000014-0000-4000-8000-000000000002',480,150,60,60,0,'CIRCLE','2026-10-23 17:09:56.276377'),
  ('f0000015-0000-4000-8000-000000000003','b0000015-0000-4000-8000-000000000003',480,230,60,60,0,'CIRCLE','2026-10-23 17:09:56.276377'),
  -- Khu vực 2: Sân Vườn (Góc trên bên phải: X in [580..960], Y in [70..290])
  ('7294047e-bec5-4532-aee6-387dc4d9e0be','122f300e-27ed-4a39-9858-9fd1b731d8f8',580,70,90,90,0,'CIRCLE','2026-10-23 17:09:56.276377'),
  ('f0000007-aaaa-4000-8000-000000000005','a0000007-aaaa-4000-8000-000000000005',700,70,60,90,0,'RECTANGLE','2026-10-23 17:09:56.280029'),
  ('f0000007-bbbb-4000-8000-000000000006','a0000007-bbbb-4000-8000-000000000006',770,70,80,90,0,'RECTANGLE','2026-10-23 17:09:56.280029'),
  ('fe6ee75d-b219-4833-9b30-90daa82c2905','285f58f0-1d15-4d3c-b454-6aa9421bb698',580,190,90,90,0,'CIRCLE','2026-10-23 17:09:56.284749'),
  ('f0000009-aaaa-4000-8000-000000000007','a0000009-aaaa-4000-8000-000000000007',700,190,80,90,0,'RECTANGLE','2026-10-23 17:09:56.290814'),
  ('f0000009-bbbb-4000-8000-000000000008','a0000009-bbbb-4000-8000-000000000008',790,190,80,90,0,'RECTANGLE','2026-10-23 17:09:56.290814'),
  ('f0000016-0000-4000-8000-000000000004','b0000016-0000-4000-8000-000000000004',900,70,60,60,0,'CIRCLE','2026-10-23 17:09:56.290814'),
  ('f0000017-0000-4000-8000-000000000005','b0000017-0000-4000-8000-000000000005',900,160,60,60,0,'CIRCLE','2026-10-23 17:09:56.290814'),
  -- Khu vực 3: Phòng VIP (Hàng dưới: X in [60..520], Y in [510..630] bảo đảm khoảng cách đệm an toàn)
  ('fa93479c-7153-4a17-a76e-e055515fb90b','5cd8549d-5ae8-47ac-9b9a-f967fc410122',60,520,140,90,0,'RECTANGLE','2026-10-23 17:09:56.295130'),
  ('5bc17a48-b8a4-4bfb-8d8a-a77101696494','6d2b499b-4ecd-450b-8bff-cc0f210a89f0',230,520,140,90,0,'RECTANGLE','2026-10-23 17:09:56.299702'),
  ('0733979f-7043-4bdb-9355-c5215d1c4e20','91c6b356-c194-4610-b084-e79610aa2386',400,510,120,120,0,'CIRCLE','2026-10-23 17:09:56.304831')
ON DUPLICATE KEY UPDATE
  table_id = VALUES(table_id),
  position_x = VALUES(position_x),
  position_y = VALUES(position_y),
  width = VALUES(width),
  height = VALUES(height),
  rotation = VALUES(rotation),
  shape = VALUES(shape),
  updated_at = NOW();

-- --------------------------------------------------------------------------
-- 4 danh mục thực đơn
-- --------------------------------------------------------------------------
INSERT INTO `categories` (`id`, `category_name`, `display_order`, `deleted_at`) VALUES
  ('421cc496-87c0-43c1-8164-5d7d6e0b6198','Món chính',2,NULL),
  ('58885e8e-acdf-4e4f-9ad5-397513ed7177','Khai vị',1,NULL),
  ('66629b25-1b35-4f52-b487-22d2128a4478','Lẩu & Nướng',3,NULL),
  ('fedbdf44-bb81-4e4f-90b4-91506669ecdc','Tráng miệng & Đồ uống',4,NULL)
ON DUPLICATE KEY UPDATE
  category_name = VALUES(category_name),
  display_order = VALUES(display_order),
  deleted_at = NULL;

-- --------------------------------------------------------------------------
-- 20 món ăn (2 món đã hết hàng để kiểm tra trạng thái 'Hết món')
-- --------------------------------------------------------------------------
INSERT INTO `menu_items` (`id`, `category_id`, `item_name`, `description`, `current_price`, `image_url`, `is_available`, `version`, `created_at`, `updated_at`, `deleted_at`) VALUES
  ('04656c8c-5a4b-420f-a056-e72cf964bbca','66629b25-1b35-4f52-b487-22d2128a4478','Lẩu Thái Chua Cay','Lẩu thái chua cay, tôm, mực, thịt bò và rau nhúng.',320000.00,'https://images.unsplash.com/photo-1559314809-0d155014e29e?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.423799','2026-10-24 00:09:56.423799',NULL),
  ('0fe75918-98ae-4099-809b-3ff8e5be2634','66629b25-1b35-4f52-b487-22d2128a4478','Mực Nướng Mắm Nhứt','Mực lá nướng mắm nhứt, dọn nướng trên đĩa lá chuối.',180000.00,'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80',0,0,'2026-10-24 00:09:56.426436','2026-10-24 00:09:56.426436',NULL),
  ('180c9075-b419-4388-922b-c42276562530','58885e8e-acdf-4e4f-9ad5-397513ed7177','Chả Giò Rế Cua Biển','Chả giò vỏ rế giòn rụm, nhân cua biển và nấm mèo.',70000.00,'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.374551','2026-10-24 00:09:56.374551',NULL),
  ('28ace7e2-d160-4b38-90e1-de8431f2a1ec','421cc496-87c0-43c1-8164-5d7d6e0b6198','Mì Quảng Tôm Thịt','Mì Quảng sợi to, nước dùng nhợt, tôm thịt và rau thơm.',72000.00,'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.411183','2026-10-24 00:09:56.411183',NULL),
  ('30a59d6d-1409-415a-b33d-7ee9152665a0','421cc496-87c0-43c1-8164-5d7d6e0b6198','Gà Kho Tộ Sơn Tây','Gà ta kho tộ nước dừa, thơm dứa và nước mắm rượu.',95000.00,'https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.407409','2026-10-24 00:09:56.407409',NULL),
  ('393b881a-46c3-4a11-9e51-a6f0c820725a','fedbdf44-bb81-4e4f-90b4-91506669ecdc','Sữa Chua Dĩa Trái Cây','Sữa chua dĩa trộn dâu, xoài, chuối và mật ong.',45000.00,'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.440686','2026-10-24 00:09:56.440686',NULL),
  ('39708ff3-6dd4-4c19-83ba-aae222821a00','58885e8e-acdf-4e4f-9ad5-397513ed7177','Gỏi Vịt Bát Lôi','Vịt áp chảo mềm, xà lách giòn, điều chấm mè tôm và đậu tương.',88000.00,'https://images.unsplash.com/photo-1555126634-323283e090fa?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.381661','2026-10-24 00:09:56.381661',NULL),
  ('41140342-35d8-44da-bee3-7b69226df82a','421cc496-87c0-43c1-8164-5d7d6e0b6198','Cá Kho Tộ Niêu Đất','Cá basa kho tiêu nước màu dừa trong niêu đất nung.',120000.00,'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=800&q=80',0,0,'2026-10-24 00:09:56.399618','2026-10-24 00:09:56.399618',NULL),
  ('523e8ad7-1b34-4375-bfa8-ca5750dac384','58885e8e-acdf-4e4f-9ad5-397513ed7177','Nem Chua Rán Muối','Nem lụa cuộn bìn thơm, rán giòn, ăn kèm chấm mắm tôm.',85000.00,'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.377674','2026-10-24 00:09:56.377674',NULL),
  ('5a263175-2e35-41f5-bbbe-8a544b7efca2','421cc496-87c0-43c1-8164-5d7d6e0b6198','Bún Chả Hương Quê','Chả nướng than hoa, nước mắm chua ngọt, rau sống.',75000.00,'https://images.unsplash.com/photo-1555126634-323283e090fa?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.394580','2026-10-24 00:09:56.394580',NULL),
  ('695a93c2-da8f-4af4-ba8d-abd5482020a3','66629b25-1b35-4f52-b487-22d2128a4478','Lẩu Gà Lá Ép','Lẩu gà lá ép thanh mát, nấm, đậu hũ và bắp non.',280000.00,'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.429181','2026-10-24 00:09:56.429181',NULL),
  ('69607f7a-44c0-449e-b7ab-e77d1f9c8664','fedbdf44-bb81-4e4f-90b4-91506669ecdc','Bánh Chuối Nướng Mật Ong','Bánh chuối nướng mật ong, dẻo thơm, ăn kèm dừa sấy.',30000.00,'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.438059','2026-10-24 00:09:56.438059',NULL),
  ('6a30358a-0ced-441e-9878-9c24b0afb5f2','421cc496-87c0-43c1-8164-5d7d6e0b6198','Phở Bò Tái Lăn Hà Nội','Nước dùng xương ninh 12 giờ, thịt bò tái lăn gừng tỏi.',85000.00,'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.390845','2026-10-24 00:09:56.390845',NULL),
  ('6c194f38-1e9a-4067-8340-34fcf79ad5a3','421cc496-87c0-43c1-8164-5d7d6e0b6198','Cơm Tấm Bì Chả Lòt','Cơm tấm dẻo, bì heo giòn, chả lòt nước mắm và rau xanh.',82000.00,'https://images.unsplash.com/photo-1563805042-7684c019e1cb?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.402173','2026-10-24 00:09:56.402173',NULL),
  ('7646497e-3664-4167-87e6-661fa27e6391','58885e8e-acdf-4e4f-9ad5-397513ed7177','Gỏi Cuốn Tôm Thịt Ba Chỉ','Bánh tráng cuốn tôm, ba chỉ, bún và rau thơm, chấm tương đậu.',60000.00,'https://images.unsplash.com/photo-1559314809-0d155014e29e?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.370410','2026-10-24 00:09:56.370410',NULL),
  ('92e0084f-ac26-4d37-8cba-3a11f0fa4580','66629b25-1b35-4f52-b487-22d2128a4478','Bò Nướng Lá Lốt','Bò cuốn lá lốt nướng than, mỡ hành, đậu phộng rang.',140000.00,'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.421023','2026-10-24 00:09:56.421023',NULL),
  ('9a20821e-d2f1-43ac-8900-0e291b9fee65','fedbdf44-bb81-4e4f-90b4-91506669ecdc','Trà Sen Vàng Cố Đô','Trà Thái Nguyên ướp sen Tây Hồ, phục vụ ấm.',40000.00,'https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.435431','2026-10-24 00:09:56.435431',NULL),
  ('a610b5ad-041e-4695-ac5c-df05dbabf7e2','fedbdf44-bb81-4e4f-90b4-91506669ecdc','Chè Hạt Sen Long Nhãn','Hạt sen Huế bùi, long nhãn Hưng Yên, nước đường phèn.',35000.00,'https://images.unsplash.com/photo-1563805042-7684c019e1cb?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.431804','2026-10-24 00:09:56.431804',NULL),
  ('c809561c-c4df-4ec8-981d-6c77fd896d00','66629b25-1b35-4f52-b487-22d2128a4478','Lẩu Riêu Cua Bắp Bò','Riêu cua đồng, bắp bò, sườn sụn, đậu phụ chiên và rau đồng.',380000.00,'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.416120','2026-10-24 00:09:56.416120',NULL),
  ('d3e1218c-589a-461d-8ae5-cb646b0b803c','58885e8e-acdf-4e4f-9ad5-397513ed7177','Bánh Xèo Nhân Tôm Thịt','Bánh xèo giòn rụm nhân tôm thịt, ăn kèm rau sống và nước chấm.',75000.00,'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=800&q=80',1,0,'2026-10-24 00:09:56.387671','2026-10-24 00:09:56.387671',NULL)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  item_name = VALUES(item_name),
  description = VALUES(description),
  current_price = VALUES(current_price),
  image_url = VALUES(image_url),
  is_available = VALUES(is_available),
  deleted_at = NULL;

-- --------------------------------------------------------------------------
-- Voucher khuyến mãi demo (từ demo_presentation_data.sql)
-- --------------------------------------------------------------------------
INSERT INTO `vouchers` (
    `id`, `voucher_code`, `title`, `description`, `discount_type`, `discount_value`,
    `max_discount_amount`, `min_order_amount`, `start_date`, `end_date`, `total_quantity`,
    `used_quantity`, `max_uses_per_customer`, `is_active`, `version`, `created_at`, `updated_at`
) VALUES 
(
    'vouch-demo-001', 'HUONGVIET20', 'Ưu đãi Khách hàng Thân thiết 20%',
    'Giảm 20% tổng hóa đơn (tối đa 100.000đ) cho đơn từ 150.000đ.',
    'PERCENTAGE', 20.00, 100000.00, 150000.00,
    NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, 10, 1, 0, NOW(), NOW()
),
(
    'vouch-demo-002', 'GIAM50K', 'Voucher Chào Bạn Mới 50.000đ',
    'Giảm ngay 50.000đ trực tiếp vào hóa đơn từ 200.000đ.',
    'FIXED_AMOUNT', 50000.00, NULL, 200000.00,
    NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 365 DAY, 500, 0, 5, 1, 0, NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  description = VALUES(description),
  discount_type = VALUES(discount_type),
  discount_value = VALUES(discount_value),
  max_discount_amount = VALUES(max_discount_amount),
  min_order_amount = VALUES(min_order_amount),
  is_active = VALUES(is_active);

-- --------------------------------------------------------------------------
-- Cấu hình và tài khoản tích điểm Loyalty (từ demo_presentation_data.sql)
-- --------------------------------------------------------------------------
INSERT INTO `loyalty_settings` (`id`, `earn_rate_percent`, `point_to_vnd_rate`, `point_value_vnd`, `updated_at`)
VALUES ('default', 5.000, 1.000, 1.00, NOW())
ON DUPLICATE KEY UPDATE
  earn_rate_percent = VALUES(earn_rate_percent),
  point_to_vnd_rate = VALUES(point_to_vnd_rate),
  point_value_vnd = VALUES(point_value_vnd),
  updated_at = NOW();

INSERT INTO `loyalty_accounts` (`id`, `customer_phone`, `points_balance`, `updated_at`)
VALUES 
    ('loyalty-vip-001', '0902000002', 50000.00, NOW()),
    ('loyalty-reg-001', '0902000001', 15000.00, NOW())
ON DUPLICATE KEY UPDATE
  points_balance = VALUES(points_balance),
  updated_at = NOW();

-- --------------------------------------------------------------------------
-- Đánh giá khách hàng mẫu (từ demo_presentation_data.sql)
-- --------------------------------------------------------------------------
INSERT INTO `feedbacks` (
    `id`, `customer_id`, `guest_name`, `guest_phone`, `invoice_id`, `rating`, `comment`, `staff_response`, `is_public`, `created_at`
) VALUES
(
    'fb-demo-001', '365be270-166a-4d6b-92f9-896c513ee0c9', 'Trần Bảo Ngọc (VIP)', '0902000002', NULL,
    5, 'Không gian sân vườn rất thoáng mát, Phở Bò Tái Lăn nước dùng ninh xương ngọt thanh chuẩn vị Hà Nội. Nhân viên phục vụ rất chu đáo!',
    'Hương Việt chân thành cảm ơn chị Ngọc! Nhà hàng luôn nỗ lực giữ gìn hương vị truyền thống tinh tế nhất để phục vụ quý khách.',
    1, NOW() - INTERVAL 1 DAY
),
(
    'fb-demo-002', 'cf3c1975-81aa-44d1-a67d-dde5809069e9', 'Lê Hoàng Nam', '0902000001', NULL,
    5, 'Đặt bàn qua website rất nhanh và tiện, đến nơi là có bàn sẵn sàng ngay. Chả Giò Rế Cua Biển giòn rụm, cả nhà ai cũng khen ngon.',
    'Cảm ơn anh Nam đã tin tưởng lựa chọn Hương Việt cho bữa tiệc gia đình. Rất mong sớm được đón tiếp anh và người thân lần tới ạ!',
    1, NOW() - INTERVAL 2 DAY
),
(
    'fb-demo-003', NULL, 'Nguyễn Minh Trang', '0988776655', NULL,
    4, 'Món ăn lên nhanh, ấm nóng. Trà Sen Vàng thơm dịu rất hợp với các món lẩu nướng.',
    'Dạ Hương Việt cảm ơn lời khen của bạn Trang ạ! Chúc bạn luôn có những bữa ăn ngon miệng cùng bạn bè.',
    1, NOW() - INTERVAL 3 DAY
)
ON DUPLICATE KEY UPDATE
  rating = VALUES(rating),
  comment = VALUES(comment),
  staff_response = VALUES(staff_response),
  is_public = VALUES(is_public);

SET FOREIGN_KEY_CHECKS = 1;
