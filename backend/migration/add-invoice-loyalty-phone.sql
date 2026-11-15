-- Migration: Thêm cột loyalty_phone vào bảng invoices để tách biệt thông tin tích điểm và khách hàng
ALTER TABLE invoices ADD COLUMN loyalty_phone VARCHAR(20) DEFAULT NULL AFTER customer_phone;
