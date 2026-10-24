# Hướng dẫn phát triển — Nhà Hàng Hương Việt

Tài liệu này mô tả cách dựng môi trường chạy dự án từ đầu.

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 22 | `pom.xml` đặt `java.version=22` |
| Maven | 3.9+ | |
| Node.js | 20+ | `package.json` dùng Vite 8 / Vitest 4 |
| MySQL | 8.0+ | Bắt buộc có `CHECK` constraint và generated column |

## 2. Cơ sở dữ liệu

Nguồn schema chuẩn duy nhất: **`database/schema.sql`**.
Bản sao cho runtime nằm ở `backend/src/main/resources/schema.sql` (dùng để đối chiếu khi
`ddl-auto=validate` báo lệch).

### 2.1. Chạy nhanh trên máy mới

```bat
start-all.bat
```

Script làm hết: kiểm tra Java 22 / Node.js / MySQL, tạo database + tài khoản nếu chưa có,
nạp schema và dữ liệu mẫu, rồi mở hai cửa sổ chạy backend và frontend.

Cấu hình MySQL nằm trong `backend/src/main/resources/application.properties`:

```properties
app.db.host=127.0.0.1
app.db.port=3306
app.db.name=huong_viet_restaurant
app.db.username=huongviet
app.db.password=huongviet123
```

Đây là **nguồn cấu hình duy nhất** — script `start-all.bat` đọc chính 5 dòng `app.db.*` này
để tạo database, còn ứng dụng Spring Boot ghép URL JDBC từ chúng. Sửa ở đây thì cả hai
cùng dùng giá trị mới.

Nếu muốn dùng MySQL trong Docker thì đổi `app.db.port` thành cổng bạn map.

### 2.2. Tạo database thủ công

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```

Lệnh đầu tạo database `huong_viet_restaurant` cùng toàn bộ bảng. Lệnh sau nạp sẵn
3 khu vực, 12 bàn, sơ đồ sàn, 4 danh mục, 20 món ăn và 7 tài khoản (quản trị + 6 tài khoản demo).

Script `start-all.bat` thực hiện đúng hai lệnh này khi database chưa có.

Nếu bạn khởi động backend trên database rỗng mà **không** chạy `seed.sql` thì các seeder
trong ứng dụng vẫn tự tạo đúng dữ liệu đó (xem mục 3.1). Chạy cả hai cùng lúc cũng an toàn:
các seeder phát hiện dữ liệu đã có và bỏ qua.

### 2.3. Script nâng cấp cho database đã có dữ liệu

Các script migration đặt trong `backend/src/main/java/com/huongViet/database/`, đặt tên theo
`V<yyyyMMdd>__<mô_tả>.sql`. Chạy theo thứ tự ngày tăng dần:

| Script | Nội dung |
|---|---|
| `V20261022__vnpay_loyalty.sql` | Bảng thanh toán VNPAY và tích điểm |
| `V20261023__loyalty_profile_and_points.sql` | Hồ sơ khách hàng và điểm thưởng |
| `V20261024__order_note_and_kitchen_ticket.sql` | Ghi chú món, snapshot giá, liên kết order cho bếp |

### 2.4. Cấu hình kết nối

`backend/src/main/resources/application.properties` đọc biến môi trường, có giá trị mặc định
cho môi trường local:

| Biến | Mặc định |
|---|---|
| `JWT_SECRET` | khoá dự phòng cho local — **bắt buộc đặt lại khi triển khai thật** |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin` / `admin@huongviet.com` / `Admin@123456` |
| `CLOUDINARY_URL` | `cloudinary://<api_key>:<api_secret>@<cloud_name>` — tài khoản demo dùng chung |
| `CLOUDINARY_FOLDER` | `huongviet/menu` — thư mục lưu ảnh trên Cloudinary |
| `DEMO_ACCOUNTS_ENABLED` | `true` — đặt `false` để không tạo tài khoản demo |

Thông tin kết nối MySQL nằm ở nhóm `app.db.*` trong cùng file, xem mục 2.1.

> ⚠️ `app.db.password` đang để trần trong file. Trước khi triển khai thật, hãy bỏ giá trị
> mặc định và bắt buộc cấp qua biến môi trường.
>
> ⚠️ `app.cloudinary.url` cũng đang để sẵn thông tin tài khoản demo trong repo. Khi đưa dự án
> lên công khai, hãy xoá giá trị mặc định đó và chỉ cấp qua biến môi trường `CLOUDINARY_URL`.

### 2.5. Lưu trữ ảnh (Cloudinary)

Ảnh món ăn được tải lên từ máy người dùng và lưu trên **Cloudinary**:

```
POST /api/admin/uploads/images      multipart/form-data, field: file
→ { "url": "https://res.cloudinary.com/.../huongviet/menu/<id>.png", "backend": "cloudinary" }
```

| Mục | Chi tiết |
|---|---|
| Thư mục lưu trữ | `CLOUDINARY_FOLDER` (mặc định `huongviet/menu`) |
| Định dạng cho phép | JPG, PNG, GIF, WEBP, SVG |
| Dung lượng tối đa | 5 MB (`UPLOAD_MAX_FILE_SIZE`) |
| Tên file | luôn sinh lại từ UUID, không dùng tên file do client gửi lên |
| Thay ảnh | ảnh cũ tự động bị xoá khỏi Cloudinary |

Nếu `CLOUDINARY_URL` không được cấu hình, ảnh lưu trên ổ đĩa cục bộ (`UPLOAD_DIR`, mặc định
`backend/uploads`) và được phục vụ qua `/uploads/**` — ứng dụng vẫn chạy được offline.

> Ảnh minh hoạ của dữ liệu mẫu cũng được đưa lên Cloudinary khi seeder chạy, nên lần seed đầu
> tiên sẽ mất khoảng 40 giây (20 ảnh, mỗi ảnh một lệnh gọi mạng).

## 3. Chạy backend

```bash
cd backend
mvn spring-boot:run
```

Backend lắng nghe ở `http://localhost:8080`.

Khi khởi động lần đầu, các seeder tự chạy:

| Seeder | Nội dung |
|---|---|
| `AdminSeeder` (`config`) | Tạo tài khoản admin mặc định nếu chưa có |
| `DemoAccountSeeder` (`config`) | Tạo 6 tài khoản demo (quanly, nhanvien, nhanvien2, dau_bep, khachhang, khachvip) |
| `RestaurantSeedData` (`config`) | Tạo 3 khu vực, 12 bàn và sơ đồ sàn nếu bảng `table_zones` còn trống |
| `MenuSeeder` (`menu`) | Tạo 4 danh mục và 20 món ăn mẫu nếu bảng `categories` còn trống |

Các seeder đều **idempotent**: chạy lại không ghi đè dữ liệu đã có.

### 3.1. Tài khoản mẫu

| Tài khoản | Mật khẩu | Vai trò | Màn hình sau khi đăng nhập |
|---|---|---|---|
| `admin` | `Admin@123456` | ADMIN | `/admin` |
| `quanly` | `QuanLy@12345` | MANAGER | `/manager` |
| `nhanvien` | `NhanVien@12345` | STAFF | `/staff` |
| `nhanvien2` | `NhanVien@12345` | STAFF | `/staff` |
| `dau_bep` | `DauBep@12345` | CHEF | `/chef` |
| `khachhang` | `KhachHang@12345` | CUSTOMER | `/` |
| `khachvip` | `KhachHang@12345` | CUSTOMER | `/` |

Tài khoản demo được `DemoAccountSeeder` tạo tự động. Đặt `DEMO_ACCOUNTS_ENABLED=false` để tắt.

## 4. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy ở `http://localhost:5173`, gọi API backend qua `VITE_API_BASE_URL`
(mặc định `http://localhost:8080`).

### 4.1. Biến môi trường frontend

| Biến | Mặc định |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` |

CORS phía backend đã cho phép `http://localhost:5173` và `http://localhost:8081`
(điều chỉnh qua `app.cors.allowed-origins`).

## 5. Kiểm thử

```bash
# Backend — unit test
cd backend && mvn test

# Frontend — unit test
cd frontend && npm run test

# Frontend — kiểm tra kiểu
cd frontend && npx tsc --noEmit
```

## 6. Cấu trúc mã nguồn

```
backend/src/main/java/com/huongViet/
├── auth/        JWT, refresh token, đăng ký/đăng nhập/quên mật khẩu
├── account/     Hồ sơ cá nhân, quản lý nhân viên
├── table/       Khu vực, bàn, sơ đồ sàn (WebSocket `/topic/table-status`, `/topic/floor-plan`)
├── reservation/ Đặt bàn, audit, no-show scheduler, rate limiter, email
├── menu/        Danh mục và món ăn (UC-14, UC-20, UC-21)
├── order/       Order gọi món, phiếu bếp KDS (UC-15, UC-16, UC-17)
├── upload/      Tải ảnh lên Cloudinary (kèm lưu cục bộ dự phòng)
├── payment/     Cổng VNPAY, tích điểm
├── voucher/     Voucher
└── config/      Security, WebSocket, xử lý lỗi tập trung, seeder

frontend/src/
├── routes/           Route theo file (TanStack Start)
├── components/hv/    Component giao diện nghiệp vụ
├── features/         Tính năng theo domain (reservation/, menu/, order/)
└── lib/              API client dùng chung, store, hook WebSocket
```

## 7. API quản trị thực đơn (phân trang & sắp xếp)

```
GET /api/admin/menu-items?categoryId=&q=&available=&page=&size=&sort=&direction=
```

| Tham số | Mặc định | Ý nghĩa |
|---|---|---|
| `page` | `0` | Trang hiện tại, đánh số từ 0 |
| `size` | `20` | Số món trên một trang (tối đa 100) |
| `sort` | `createdAt` | `createdAt` · `updatedAt` · `name` · `price` |
| `direction` | theo `sort` | `asc` · `desc` |

Mặc định sắp xếp theo **thời gian tạo, món mới nhất lên đầu**.

Response dạng phân trang:

```json
{
  "content": [ /* các món trong trang */ ],
  "page": 0, "size": 20, "totalElements": 21, "totalPages": 2, "last": false
}
```

## 8. API điều chỉnh món trong đơn (UC-17)

Nhân viên có thể thêm, sửa hoặc huỷ món trong một đơn đang mở:

```
POST   /api/orders/{orderId}/items              thêm món vào đơn
PATCH  /api/orders/{orderId}/items/{itemId}     đổi số lượng / ghi chú
DELETE /api/orders/{orderId}/items/{itemId}     huỷ món (body: { reason })
```

| Quy tắc | Hành vi |
|---|---|
| BR-38 | Mọi thay đổi được đồng bộ xuống phiếu bếp và phát WebSocket ngay lập tức |
| BR-39 | Món đã ở trạng thái `READY` hoặc `SERVED` không được sửa/huỷ (bếp đã nấu xong) → 409 |
| BR-40 | Món hết hàng không được thêm vào đơn → 409 |

Mọi thay đổi đều được ghi vào bảng `order_item_audits` (số lượng và trạng thái trước/sau,
lý do, ai thay đổi) để đối soát khi lập hoá đơn.

## 9. WebSocket

Broker STOMP tại `/ws`, prefix đích `/app`, kênh broadcast `/topic/**`.

| Kênh | Nội dung |
|---|---|
| `/topic/table-status` | Bàn đổi trạng thái |
| `/topic/floor-plan` | Sơ đồ sàn được lưu |
| `/topic/menu/availability` | Món bật/tắt còn hàng (BR-50) |
| `/topic/menu/refresh` | Danh mục/món thay đổi, client cần tải lại (BR-55) |
| `/topic/kitchen/tickets` | Phiếu bếp mới hoặc đổi trạng thái (BR-34) |
| `/topic/kitchen/completed` | Món đã xong, thông báo cho phục vụ (STT 90) |
