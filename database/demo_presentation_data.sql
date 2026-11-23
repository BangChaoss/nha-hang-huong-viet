-- ==============================================================================
-- DỮ LIỆU MẪU DÀNH CHO BUỔI THUYẾT TRÌNH (PRESENTATION DEMO DATASET)
-- Hệ thống Quản lý Nhà hàng Hương Việt
-- ==============================================================================

USE huong_viet_restaurant;
SET NAMES utf8mb4;

-- 1. LÀM SẠCH VÀ CHUẨN HÓA TRẠNG THÁI BÀN (TẤT CẢ SẴN SÀNG ĐỂ DEMO)
UPDATE restaurant_tables SET status = 'AVAILABLE' WHERE deleted_at IS NULL;

-- 2. TẠO VOUCHER KHUYẾN MÃI DEMO
DELETE FROM vouchers WHERE voucher_code IN ('HUONGVIET20', 'GIAM50K');

INSERT INTO vouchers (
    id, voucher_code, title, description, discount_type, discount_value,
    max_discount_amount, min_order_amount, start_date, end_date, total_quantity,
    used_quantity, max_uses_per_customer, is_active, version, created_at, updated_at
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
);

-- 3. NẠP ĐIỂM THƯỞNG TÍCH LŨY (LOYALTY POINTS) SẴN CHO KHÁCH HÀNG DEMO
-- Khách VIP (0902000002): Có sẵn 50.000 điểm để demo tính năng đổi điểm trừ tiền tại POS
INSERT INTO loyalty_accounts (id, customer_phone, points_balance, updated_at)
VALUES 
    ('loyalty-vip-001', '0902000002', 50000.00, NOW()),
    ('loyalty-reg-001', '0902000001', 15000.00, NOW())
ON DUPLICATE KEY UPDATE 
    points_balance = VALUES(points_balance),
    updated_at = NOW();

-- Cấu hình điểm thưởng: 1 điểm = 1 VNĐ, tích 5% tổng hóa đơn
INSERT INTO loyalty_settings (id, earn_rate_percent, point_value_vnd, updated_at)
VALUES ('default', 5.000, 1.00, NOW())
ON DUPLICATE KEY UPDATE 
    earn_rate_percent = 5.000,
    point_value_vnd = 1.00,
    updated_at = NOW();

-- 4. DỌN DẸP DỮ LIỆU ĐÁNH GIÁ THỬ NGHIỆM VÀ NẠP ĐÁNH GIÁ MẪU CHUYÊN NGHIỆP
DELETE FROM feedbacks WHERE comment LIKE '%cut%' OR comment LIKE '%test%';

INSERT INTO feedbacks (
    id, customer_id, guest_name, guest_phone, invoice_id, rating, comment, staff_response, is_public, created_at
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
ON DUPLICATE KEY UPDATE id = id;
