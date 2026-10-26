# Nhà Hàng Hương Việt

Hệ thống quản lý nhà hàng: đặt bàn, thực đơn, gọi món, màn hình bếp (KDS), thanh toán và tích điểm.

| Thành phần | Công nghệ |
| --- | --- |
| `backend/` | Spring Boot 3.5 · Java 22 · Spring Data JPA · Spring Security (JWT) · STOMP WebSocket · MySQL 8 |
| `frontend/` | TanStack Start · React 19 · TanStack Query · Tailwind v4 · shadcn/Radix · Konva · Vitest |
| `database/` | Schema MySQL (nguồn chuẩn duy nhất) |

## Chạy nhanh

Trên Windows, chạy `start-all.bat` để mở backend và frontend trong các cửa sổ riêng
rồi mở trang web tại `http://localhost:5173`. Cần bật MySQL trước.

Chạy thủ công:

```bash
# 1. Database
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql      # tuỳ chọn: nạp sẵn dữ liệu demo

# 2. Backend — http://localhost:8080
cd backend && mvn spring-boot:run

# 3. Frontend — http://localhost:5173
cd frontend && npm install && npm run dev
```

Bước 1 có thể bỏ qua `seed.sql`: khi backend khởi động lần đầu trên database rỗng, hệ thống
tự seed **3 khu vực, 12 bàn, sơ đồ sàn, 4 danh mục, 20 món ăn** và **6 tài khoản demo**.

### Tài khoản demo

| Username | Mật khẩu | Vai trò | Màn hình |
|---|---|---|---|
| `admin` | `Admin@123456` | ADMIN | `/admin` |
| `quanly` | `QuanLy@12345` | MANAGER | `/manager` |
| `nhanvien` | `NhanVien@12345` | STAFF | `/staff` |
| `nhanvien2` | `NhanVien@12345` | STAFF | `/staff` |
| `dau_bep` | `DauBep@12345` | CHEF | `/chef` |
| `khachhang` | `KhachHang@12345` | CUSTOMER | `/` |
| `khachvip` | `KhachHang@12345` | CUSTOMER | `/` |

Tắt tài khoản demo khi triển khai thật: `DEMO_ACCOUNTS_ENABLED=false`.

Hướng dẫn đầy đủ (biến môi trường, script migration, cấu trúc mã nguồn, WebSocket):
**[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**

## Kiểm thử

```bash
cd backend  && mvn test     # 87 unit test
cd frontend && npm run test # 27 unit test
```

## Tính năng

- **Đặt bàn** — khách đặt bàn công khai, nhân viên xác nhận/gán bàn, check-in, huỷ, tự động xử lý no-show.
- **Thực đơn** — quản lý danh mục và món ăn, bật/tắt còn hàng theo thời gian thực, thực đơn công khai cho khách.
- **Gọi món & bếp** — nhân viên gọi món tại bàn, phiếu đẩy thẳng sang màn hình bếp theo thứ tự FIFO,
  bếp cập nhật trạng thái và phục vụ nhận thông báo món xong.
- **Voucher, thanh toán VNPAY, tích điểm** — quản lý voucher, cổng thanh toán, ví điểm thưởng.
- **Quản lý** — nhân viên, bàn, sơ đồ sàn, báo cáo doanh thu.
