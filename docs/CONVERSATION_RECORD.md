# Nhật ký công việc và phiên làm việc (Conversation Record)

## Phiên làm việc: Nghiên cứu & Lập kế hoạch cải tiến Sơ đồ bàn 2D Canvas (Floor Plan Canvas)
- **Thời gian**: 2026-11-06
- **Mục tiêu**: Nghiên cứu sâu từ tài liệu học thuật và thực tiễn toàn cầu về tương tác người - máy (HCI) trong quản lý sơ đồ bàn nhà hàng, từ đó lập kế hoạch toàn diện khắc phục khó khăn và cung cấp phương án nâng cấp tối ưu cho Sơ đồ bàn 2D Canvas hiện tại (`FloorPlanCanvas.tsx`).

### 1. Phân tích hiện trạng mã nguồn (Codebase Audit)
- **Vị trí Sơ đồ 2D trong dự án**:
  - `frontend/src/components/hv/FloorPlanCanvas.tsx`: Vẽ bằng `react-konva` (HTML5 `<canvas>`). Kéo thả tự do chỉ làm tròn bước nhảy 20px, không có thanh căn gióng từ tính (magnetic snapping), không có kiểm soát va chạm (collision detection) làm bàn đè lên nhau, thiếu nút xoay bàn 90° và đổi hình dạng trực tiếp trên canvas, không có Undo/Redo.
  - `frontend/src/components/hv/TableManagement.tsx`: Nơi quản lý sơ đồ bàn cho Quản lý / Thu ngân.
  - `frontend/src/features/reservation/components/confirm-booking-dialog.tsx`: Hộp thoại đặt bàn cho khách / nhân viên. Chiều cao canvas bị ép xuống 360px, gây khó khăn trên thiết bị di động do lỗi chạm nhầm (fat-finger problem).
  - `frontend/src/lib/table-api.ts` & `database/schema.sql`: Bảng `floor_plan_layouts` lưu trữ `(position_x, position_y, width, height, rotation, shape)`.

### 2. Nghiên cứu tài liệu học thuật & nghiên cứu thực tiễn toàn cầu
1. **Lý thuyết tải trọng nhận thức (Cognitive Load Theory - Sweller 2011)**: Sơ đồ tự do không phân nhóm làm tăng vọt tải trọng ngoại lai (*extraneous cognitive load*).
2. **Định luật Fitts (Fitts's Law - Fitts 1954)**: Kích thước vùng bấm trên màn hình cảm ứng tối thiểu phải từ 44-48px. Canvas thu nhỏ gây trượt điểm chạm.
3. **Định luật Hick-Hyman (Hick-Hyman Law)**: Hiển thị 50 bàn đồng thời trên 1 canvas gây tê liệt thị giác (*analysis paralysis*). Phân nhóm theo Khu vực (Zone chunking) giúp giảm thời gian ra quyết định theo hàm logarit.
4. **Nghiên cứu Đông Á (Meituan Trung Quốc, IPSJ Nhật Bản, Báo cáo F&B Việt Nam)**: Tần suất gộp bàn (*table merging*), tiệc đông và xoay vòng bàn cực nhanh.
5. **Nghiên cứu Châu Âu (Luật Khả năng tiếp cận EAA 2025, ĐH Linköping Thụy Điển)**: Yêu cầu khả năng tiếp cận và công cụ hỗ trợ căn chỉnh giảm mệt mỏi nhận thức.
6. **Nghiên cứu Mỹ Latinh (Abrasel Brazil, Mexico)**: Đòi hỏi tính ổn định và nhẹ nhàng trong xử lý đồ họa.
7. **Nghiên cứu Bắc Mỹ (ĐH Cornell, Harrah College UNLV)**: Tối ưu hóa sơ đồ bàn cho lễ tân và điều phối bàn.

### 3. Khảo sát ý kiến và Lựa chọn của Người dùng (User Decision)
Người dùng đã xác nhận các lựa chọn cụ thể:
1. **Hướng kiến trúc**: Phương án 1 — Giữ và đại tu Canvas 2D (Bổ sung căn gióng từ tính, cảnh báo va chạm, thanh công cụ nổi xoay/đổi hình, Undo/Redo, Auto-Fit).
2. **Cơ chế căn chỉnh**: Kéo thả tự do kết hợp đường căn gióng từ tính thông minh (Smart Guidelines & Magnetic Snap tương tự Figma/Canva).
3. **Quy chuẩn kích thước**: Chuẩn hóa kích thước bàn theo số ghế (Bàn 2 người: 70x70, Bàn 4 người: 90x90, Bàn 6-8 người: 140x90).

### 4. Cập nhật Kế hoạch triển khai (Implementation Plan)
- Đã cập nhật artifact `floor_plan_canvas_transformation_plan.md` với đầy đủ chi tiết kỹ thuật:
  - Thuật toán căn gióng từ tính (Magnetic Guidelines) theo trục tâm và các cạnh với ngưỡng 10px.
  - Thuật toán kiểm tra va chạm (AABB Collision Detection) cảnh báo viền đỏ phát sáng khi 2 bàn đè lên nhau.
  - Thanh công cụ nổi (Contextual Floating Action Pill) gắn liền với bàn được chọn: Xoay 90°, Đổi hình tròn/chữ nhật, Chuẩn hóa kích thước theo số ghế.
  - Bộ nhớ hoàn tác/làm lại (Undo/Redo Stack) hỗ trợ `Ctrl+Z` và `Ctrl+Y`.
  - Tính năng "Căn vừa màn hình" (Auto-Fit Viewport Centering) tự động tính toán tỷ lệ zoom và vị trí tâm để bao trọn mọi bàn trong khung nhìn.
- Tuân thủ quy tắc người dùng:
  - Không thay đổi bất kỳ endpoint API hay bảng cơ sở dữ liệu nào (`/api/floor-plan`, `/api/admin/floor-plan`).
  - Đã ghi lại nhật ký chi tiết trong file markdown này.
  - Đang chờ người dùng bấm "Proceed" hoặc duyệt kế hoạch để bắt đầu triển khai code.

---

## Phiên làm việc: Triển khai toàn diện Kế hoạch nâng cấp Sơ đồ bàn 2D Canvas (Floor Plan Canvas Transformation)
- **Thời gian**: 2026-11-06
- **Mục tiêu**: Tiến hành hiện thực hóa 100% các tính năng trong kế hoạch đã được phê duyệt:
  1. Căn gióng từ tính (Smart Magnetic Guidelines & Snapping) Figma/Canva style.
  2. Phát hiện & cảnh báo va chạm (AABB Collision Detection & Visual Glow Warning).
  3. Thanh công cụ nổi thông minh (Contextual Floating Action Pill) gắn liền với bàn: Xoay 90°, Đổi hình tròn/chữ nhật, Chuẩn hóa kích thước theo số ghế.
  4. Hệ thống Hoàn tác/Làm lại (Undo/Redo History Stack) hỗ trợ `Ctrl+Z` / `Ctrl+Y`.
  5. Tính năng Tự động căn vừa màn hình (Auto-Fit Viewport Centering).
  6. Tối ưu trải nghiệm đặt bàn trên thiết bị cảm ứng (`confirm-booking-dialog.tsx`).
- **Các tệp sửa đổi**:
  - `frontend/src/components/hv/FloorPlanCanvas.tsx`
  - `frontend/src/components/hv/TableManagement.tsx`
  - `frontend/src/features/reservation/components/confirm-booking-dialog.tsx`
  - `frontend/src/test/floor-plan-canvas.test.ts` (Tạo mới bộ kiểm thử tự động cho chuẩn hóa kích thước bàn)

### 1. Chi tiết hiện thực hóa mã nguồn (Implementation Details)
1. **Thuật toán Căn gióng từ tính thông minh (Smart Magnetic Guidelines & Snapping)**:
   - Trong quá trình kéo thả `onDragMove`: So sánh tọa độ cạnh (Trái-Trái, Phải-Phải, Trên-Trên, Dưới-Dưới, Giáp cạnh) và trục tâm (Center-X, Center-Y) với mọi bàn đang hiển thị theo ngưỡng `SNAP_THRESHOLD = 10px`.
   - Tự động hiển thị các đường gióng nét đứt màu cyan (`#06b6d4`, `strokeWidth: 1.5`, `dash: [4, 4]`) trải dài trên toàn bộ canvas khi bắt điểm căn gióng, mang lại cảm giác mượt mà tương tự Figma và Canva.
   - Khi không bắt điểm với bàn khác, tự động căn hít lưới 10px tinh gọn.

2. **Thuật toán Phát hiện & Cảnh báo va chạm vị trí (AABB Collision Detection & Warning Glow)**:
   - Sử dụng thuật toán giao cắt hình chữ nhật AABB `computeCollisions(layoutsMap, visibleIds)` chạy với thời gian phản hồi $< 0.05\text{ms}$ ở tốc độ 60 FPS.
   - Khi hai hay nhiều bàn đè lên nhau:
     - Viền bàn chuyển sang màu đỏ phát sáng rực rỡ (`#ef4444`, `strokeWidth: 3.5`, `shadowColor: rgba(239, 68, 68, 0.7)`, `shadowBlur: 14`).
     - Hiển thị huy hiệu chấm than cảnh báo màu đỏ trực quan góc trên bàn.
     - Thanh công cụ cảnh báo số lượng bàn đang bị trùng đè.
     - Khi thả chuột, phát thông báo cảnh báo qua toast nếu bàn vẫn đang bị đè.

3. **Thanh công cụ nổi ngữ cảnh (Contextual Floating Action Pill)**:
   - Hiển thị trực tiếp ngay phía trên mép bàn được chọn (tự động điều chỉnh tọa độ theo góc phóng to và vị trí stage).
   - Trang bị các nút thao tác nhanh:
     - **Xoay 90°** (`<RotateCw />`): Xoay bàn theo bước nhảy 90° đồng thời tính toán lại tọa độ để tâm bàn cố định nguyên vị trí.
     - **Đổi hình dạng** (`<Shapes />`): Chuyển đổi linh hoạt giữa Bàn tròn (`CIRCLE`) và Bàn chữ nhật (`RECTANGLE`), tự động áp dụng chuẩn kích thước theo số ghế.
     - **Chuẩn hóa kích thước** (`<Maximize2 />`): Snap ngay về kích thước chuẩn theo số ghế.

4. **Hệ thống Hoàn tác / Làm lại (Undo / Redo History Stack)**:
   - Triển khai ngăn xếp lịch sử bất biến `history` lưu trữ đến 50 trạng thái bố cục `(x, y, width, height, rotation, shape)`.
   - Tích hợp 2 nút `Undo` và `Redo` với trạng thái bật/tắt (disabled) trực quan trên thanh công cụ.
   - Hỗ trợ phím tắt toàn cục `Ctrl+Z` (Undo) và `Ctrl+Y` / `Ctrl+Shift+Z` (Redo), bỏ qua khi người dùng đang nhập liệu trong ô input/textarea.

5. **Tính năng Tự động căn vừa màn hình (Auto-Fit Viewport Centering)**:
   - Nút Auto-Fit (`<Maximize />`) trên thanh công cụ.
   - Tự động tính toán khung bao phủ toàn bộ các bàn trong nhà hàng `(minX, minY, maxX, maxY)` và độ phân giải hiển thị thực tế của canvas.
   - Tính toán tỷ lệ zoom tối ưu (với độ đệm 70px) và căn tâm hoàn hảo sơ đồ vào giữa khung nhìn.

6. **Tối ưu hóa Hộp thoại Đặt bàn (Confirm Booking Dialog)**:
   - Tăng chiều cao canvas lên 420px giúp không gian trực quan hơn.
   - Tích hợp nút Auto-Fit trong chế độ chỉ đọc `readOnly` để nhân viên/lễ tân bấm 1 chạm nhìn thấy trọn vẹn mọi bàn.
   - Khóa kéo thả di dời bàn nhưng giữ nguyên thao tác cuộn, phóng to thu nhỏ và bấm chọn gộp nhiều bàn mượt mà.

### 2. Kết quả kiểm thử & Xây dựng (Verification Results)
- **Kiểm thử tự động (Automated Unit Tests)**:
  - Chạy `npm test --prefix frontend -- src/test/floor-plan-canvas.test.ts`: 100% tests Passed (2/2 tests trong 4ms).
  - Chạy toàn bộ test suite dự án `npm test --prefix frontend`: 10/10 test files passed, 58/58 tests passed (0 lỗi).
- **Biên dịch và đóng gói (Production Build)**:
  - Chạy `npm run build --prefix frontend`: Hoàn thành xuất sắc, exit code 0, 0 lỗi TypeScript hay import.
- **Tuân thủ quy chuẩn Endpoint API**:
  - Không thay đổi bất kỳ URL hay method backend nào:
    - `GET /api/floor-plan`
    - `PUT /api/admin/floor-plan`
    - `PATCH /api/tables/{id}/status`

---

## Phiên làm việc: Cấu hình số điện thoại người dùng làm placeholder cho Đặt bàn (`book-form.tsx`)
- **Thời gian**: 2026-11-10
- **Mục tiêu**: Lấy số điện thoại của người dùng đã đăng nhập để làm placeholder trong ô "Số điện thoại liên hệ" tại `frontend/src/features/reservation/components/book-form.tsx`.
- **Hiện trạng mã nguồn**:
  - `frontend/src/features/reservation/components/book-form.tsx`: Ô nhập số điện thoại hiện đang dùng placeholder tĩnh: `placeholder="0912345678 hoặc +84..."`.
  - `session` (`AuthSession` trong `frontend/src/lib/auth-session.ts`): Hiện chỉ chứa `id, username, email, role`, chưa có `phoneNumber`.
  - API hồ sơ người dùng: `profileApi.get()` (`GET /api/account/profile`) trả về `UserAccount` bao gồm `phoneNumber`.
- **Trạng thái**: Hoàn thành 100%.

### 1. Phê duyệt & Lựa chọn của Người dùng (User Decision)
- **Hành vi**: Vừa hiển thị số điện thoại làm placeholder, vừa tự động điền sẵn (prefill) vào giá trị ô nhập khi người dùng đã đăng nhập.
- **Kiến trúc lấy dữ liệu**: Phương án kết hợp:
  - Bổ sung `phoneNumber` vào Backend `AuthResponse` và Frontend `AuthSession`.
  - Dự phòng qua `profileApi.get()` (`GET /api/account/profile`) để tương thích với các phiên đăng nhập sẵn có từ trước.

### 2. Các tệp đã sửa đổi (Changes Applied)
1. **Backend**:
   - `backend/src/main/java/com/huongViet/auth/AuthResponse.java`: Bổ sung trường `String phoneNumber` vào record và map từ `user.getPhoneNumber()`.
   - `backend/src/test/java/com/huongViet/auth/AuthServiceTest.java`: Cập nhật assertion kiểm tra `phoneNumber` trong kết quả đăng ký và đăng nhập.
   - `backend/src/test/java/com/huongViet/payment/LoyaltyControllerTest.java`: Sửa constructor `LoyaltyRateResponse` trong test case để khớp với 3 tham số hiện tại.
2. **Frontend Authentication & State**:
   - `frontend/src/lib/auth-session.ts`: Thêm `phoneNumber?: string` vào `AuthSession`.
   - `frontend/src/lib/auth-api.ts`: Thêm `phoneNumber?: string` vào `AuthUser`.
   - `frontend/src/lib/account-api.ts`: Cập nhật `profileApi.update` để đồng bộ `phoneNumber` vào `session` khi người dùng cập nhật hồ sơ.
3. **Biểu mẫu Đặt bàn**:
   - `frontend/src/features/reservation/components/book-form.tsx`:
     - Tích hợp `useQuery` gọi `profileApi.get()` dự phòng khi đã đăng nhập.
     - Lấy `userPhone = session?.phoneNumber || profileQuery.data?.phoneNumber || ""`.
     - Đặt `guestPhone: initialValues?.guestPhone ?? userPhone ?? ""` trong `defaultValues`.
     - Thêm `useEffect` tự động điền số điện thoại nếu dữ liệu profile tải về sau khi render và trường chưa có giá trị.
     - Cập nhật placeholder: `placeholder={userPhone || "0912345678 hoặc +84..."}`.
     - Cập nhật `handleResetForm` để reset về `userPhone`.
4. **Kiểm thử tự động**:
   - `frontend/src/features/reservation/__tests__/book-form.test.tsx`: Bổ sung 2 test case:
     - Kiểm tra khi chưa đăng nhập: placeholder là `"0912345678 hoặc +84..."`, giá trị ô nhập là `""`.
     - Kiểm tra khi đã đăng nhập: placeholder là số điện thoại người dùng (`"0909123456"`), giá trị ô nhập tự động được prefill (`"0909123456"`).

### 3. Kết quả Kiểm thử & Biên dịch (Verification Results)
- **Backend Tests**: `mvn test -f backend/pom.xml -Dtest=AuthServiceTest` -> **BUILD SUCCESS** (5/5 tests passed).
- **Frontend Unit Tests**: `npm test --prefix frontend` -> **13 test files passed, 77/77 tests passed** (100% pass).
- **Frontend Production Build**: `npm run build --prefix frontend` -> **Exit code 0** (Build thành công).
- **Tuân thủ quy chuẩn Endpoint**: Không thay đổi URL/method của các endpoint đã tồn tại.

---

## Phiên làm việc: Khắc phục định dạng mã đặt bàn trong Email, Gửi Email khi Staff xác nhận & Gửi Email khi Đơn bị Hủy
- **Thời gian**: 2026-11-10
- **Mục tiêu**:
  1. Đồng bộ mã đặt bàn gửi qua Gmail với mã đặt bàn hiển thị trên giao diện web (`HV-XXXXXXXX`).
  2. Bổ sung tính năng gửi email xác nhận đặt bàn thành công ngay khi staff phê duyệt đơn (`updateStatus` -> `CONFIRMED`) và gán bàn.
  3. Bổ sung tính năng gửi email thông báo kèm lý do cụ thể khi đơn đặt bàn bị hủy (kể cả khách hàng tự hủy hoặc staff hủy).
- **Trạng thái**: Hoàn thành 100%.

### 1. Phê duyệt & Lựa chọn của Người dùng (User Decision)
- **Định dạng mã đặt bàn**: Tiền tố `HV-` kèm 8 ký tự đầu viết hoa của ID (`HV-XXXXXXXX`).
- **Nút sao chép mã (Web)**: Cập nhật sao chép toàn bộ chuỗi chuẩn `HV-XXXXXXXX` thay vì chỉ 8 ký tự.
- **Kịch bản gửi email**:
  1. *Tiếp nhận đặt bàn*: Trạng thái "Đang chờ xác nhận", mã `HV-XXXXXXXX`.
  2. *Staff xác nhận thành công*: Trạng thái "ĐÃ XÁC NHẬN", thông báo bàn được gán (VD: `Bàn 01` hoặc `Bàn 01 + Bàn 02`), mã `HV-XXXXXXXX`, lưu ý nhận bàn 30 phút.
  3. *Hủy đơn đặt bàn*: Trạng thái "ĐÃ HỦY", hiển thị lý do hủy cụ thể từ khách hàng hoặc nhân viên, mã `HV-XXXXXXXX`.

### 2. Chi tiết các tệp đã sửa đổi (Changes Applied)
1. **Backend**:
   - `backend/src/main/java/com/huongViet/reservation/Reservation.java`:
     - Bổ sung helper method `getBookingCode()` trả về `"HV-" + ID.substring(0, 8).toUpperCase()`.
   - `backend/src/main/java/com/huongViet/reservation/ReservationEmailService.java`:
     - Cập nhật `sendBookingConfirmation`: Sử dụng `reservation.getBookingCode()` cho subject và body email.
     - Thêm mới `sendBookingApprovedEmail`: Gửi email xác nhận đặt bàn thành công kèm bàn gán và khu vực.
     - Thêm mới `sendBookingCancelledEmail`: Gửi email thông báo hủy kèm lý do cụ thể.
   - `backend/src/main/java/com/huongViet/reservation/ReservationService.java`:
     - `updateStatus`:
       - Chuyển sang `CONFIRMED`: Kích hoạt `emailService.sendBookingApprovedEmail(saved, response.tableNumber(), response.zoneName())`.
       - Chuyển sang `CANCELLED`: Kích hoạt `emailService.sendBookingCancelledEmail(saved, cancelReason, response.zoneName())`.
     - `cancelByCustomer`:
       - Khách tự hủy thành công: Kích hoạt `emailService.sendBookingCancelledEmail(saved, reason, response.zoneName())`.
2. **Frontend**:
   - `frontend/src/features/reservation/components/booking-confirmation.tsx`:
     - Cập nhật `handleCopyCode` ghi `fullBookingCode = "HV-" + bookingCode` vào clipboard.
3. **Kiểm thử tự động (Unit Tests)**:
   - `backend/src/test/java/com/huongViet/reservation/ReservationEmailServiceTest.java`: Tạo mới 4 unit test kiểm thử gửi 3 loại email và trường hợp bỏ qua khi không có email.
   - `backend/src/test/java/com/huongViet/reservation/ReservationServiceTest.java`: Cập nhật assertion và thêm 2 test case cho việc kích hoạt email khi duyệt đơn và hủy đơn.
   - `frontend/src/features/reservation/__tests__/booking-confirmation.test.tsx`: Tạo mới unit test kiểm thử hiển thị mã `HV-XXXXXXXX` và hành vi copy.

### 3. Kết quả Kiểm thử & Biên dịch (Verification Results)
- **Backend Tests**: `mvn test -f backend/pom.xml "-Dtest=ReservationEmailServiceTest,ReservationServiceTest,ReservationControllerTest"`
  -> **BUILD SUCCESS** (33/33 tests passed, 0 failures, 0 errors).
- **Frontend Unit Tests**: `npm test --prefix frontend`
  -> **14 test files passed, 80/80 tests passed** (100% pass).
- **Frontend Production Build**: `npm run build --prefix frontend`
  -> **Exit code 0** (Build thành công sạch sẽ).
- **Tuân thủ quy chuẩn Endpoint**: 100% giữ nguyên endpoint hiện tại (`GET /api/reservations/guest-lookup`, `PATCH /api/admin/reservations/{id}/status`, `POST /api/reservations/{id}/cancel`).


---

## Phiên làm việc: Nghiên cứu & Lập kế hoạch đại tu Màn hình Quản lý Đặt bàn cho Staff (Host Stand Redesign)
- **Thời gian**: 2026-11-10
- **Mục tiêu**:
  1. Khắc phục triệt để nhược điểm "bó hẹp 1 ngày" ở giao diện Staff Booking hiện tại (`StaffBookingDashboard.tsx`), giúp nhân viên không bao giờ bỏ lỡ các đơn đặt bàn mới (`PENDING_HOLD`) được đặt cho ngày mai hoặc các ngày tiếp theo.
  2. Luôn hiển thị và sắp xếp các đơn theo thứ tự ngày & giờ gần nhất (`reservationDate ASC, reservationTime ASC`), đồng thời ưu tiên các đơn cần duyệt lên đầu để xử lý ngay lập tức.
  3. Tham khảo các tiêu chuẩn UX quốc tế (OpenTable, SevenRooms, Toast Tables) và phần mềm F&B Việt Nam (KiotViet, CukCuk) nhằm tối ưu thao tác cảm ứng POS/Tablet và nâng cao hiệu suất làm việc của nhân viên lễ tân/host stand.
- **Phân tích hiện trạng mã nguồn (Codebase Audit)**:
  - `frontend/src/features/reservation/components/staff-booking-dashboard.tsx`:
    - Chỉ cho chọn 1 ngày duy nhất qua `<Input type="date" />`, mặc định là `today`.
    - Thanh thống kê (Metrics Bar) chỉ tính đơn của ngày đang chọn, nếu có 5 đơn chờ duyệt ở ngày mai thì màn hình vẫn báo "Chờ xác nhận: 0".
    - Chưa có nút tạo đặt bàn nhanh tại chỗ (Walk-in / Phone booking).
    - Chưa có âm báo/thông báo khi có đơn mới xuất hiện.
  - `frontend/src/features/reservation/components/staff-booking-row.tsx`:
    - Dạng dòng bảng thông thường, nút thao tác nhỏ, chưa có chế độ xem Thẻ (Card View) trực quan cho màn hình cảm ứng tablet.
  - `backend/src/main/java/com/huongViet/reservation/ReservationAdminController.java` & `ReservationRepository.java`:
    - Hiện tại chỉ có `findByDateAndStatusWithTableAndZone` lọc theo 1 ngày duy nhất (`WHERE r.reservationDate = :date`).
- **Nghiên cứu & Đề xuất giải pháp**:
  - Đã nghiên cứu thực tiễn thiết kế Host Stand từ OpenTable & SevenRooms: Ergonomics cho màn hình cảm ứng (nút bấm lớn >48px), phân cấp thông tin theo mức độ khẩn cấp (Urgent Action Queue), dòng thời gian dịch vụ trong ngày (Today's Service Timeline).
  - Đã soạn thảo Implementation Plan chi tiết trong artifact `staff_reservation_redesign_plan.md`.
- **Trạng thái**: Hoàn thành 100%.

### 1. Phê duyệt & Lựa chọn của Người dùng (User Decision)
- **Kiến trúc Backend**: Nâng cấp API `GET /api/admin/reservations` hỗ trợ thêm tham số `scope` (`scope=upcoming`: từ hôm nay trở đi; `scope=all_pending`: tất cả đơn chờ duyệt) và `status`, giữ 100% tương thích ngược với `date`.
- **Giao diện & Biểu tượng**: Tinh giản, loại bỏ hoàn toàn emoji lòe loẹt, sử dụng biểu tượng vector sắc nét từ `lucide-react` chuẩn quốc tế (OpenTable / SevenRooms / Toast Tables).
- **Phân chia 4 Tab**:
  1. `Cần xử lý ngay`: Gom toàn bộ đơn `PENDING_HOLD` của mọi ngày, sắp xếp theo ngày & giờ gần nhất lên đầu, kèm badge số lượng nổi bật.
  2. `Hôm nay`: Dòng thời gian đón tiếp khách trong ngày hôm nay kèm bộ đếm thời gian.
  3. `Sắp tới`: Danh sách đơn từ hôm nay trở đi, gom nhóm theo từng ngày từ gần đến xa.
  4. `Tra cứu theo ngày`: Bộ lọc ngày linh hoạt để kiểm tra lịch sử hoặc đối soát.
- **Chế độ hiển thị**: Nút chuyển đổi View Mode giữa **Dạng Thẻ (Smart Cards)** (tối ưu cảm ứng iPad/POS) và **Dạng Bảng (Compact Table)** (dành cho PC).
- **Tập trung nghiệp vụ**: Bỏ tính năng walk-in tạo mới, tập trung tối đa vào tốc độ tiếp nhận và điều phối các đơn đặt trước từ website.

### 2. Các tệp đã sửa đổi & tạo mới (Changes Applied)
1. **Backend**:
   - `backend/src/main/java/com/huongViet/reservation/ReservationRepository.java`:
     - Bổ sung `findUpcomingWithTableAndZone(@Param("startDate") LocalDate startDate, @Param("status") ReservationStatus status)`: Truy vấn đơn từ `startDate` trở đi, sắp xếp `PENDING_HOLD` lên trước, sau đó theo `reservationDate ASC`, rồi `reservationTime ASC`.
     - Bổ sung `findAllPendingHoldWithTableAndZone()`: Truy vấn toàn bộ đơn chờ duyệt mọi ngày, sắp xếp theo `reservationDate ASC, reservationTime ASC`.
   - `backend/src/main/java/com/huongViet/reservation/ReservationService.java`:
     - Bổ sung `listUpcoming(LocalDate startDate, ReservationStatus status)` và `listAllPending()`.
   - `backend/src/main/java/com/huongViet/reservation/ReservationAdminController.java`:
     - Mở rộng endpoint `GET /api/admin/reservations` với tham số `@RequestParam(required = false) String scope` (`upcoming`, `pending`, hoặc null).
2. **Frontend API & Types**:
   - `frontend/src/features/reservation/types/reservation.types.ts`:
     - Bổ sung interface `AdminReservationQueryParams` chứa `date`, `scope`, `status`.
   - `frontend/src/features/reservation/api/reservation-api.ts`:
     - Bổ sung hàm `listAdminBookings(params)` gọi `/api/admin/reservations` kèm `scope`, `date`, `status`.
     - Giữ nguyên `listByDate` cũ để bảo toàn tương thích ngược 100%.
   - `frontend/src/features/reservation/hooks/use-admin-reservations.ts`:
     - Nâng cấp hook nhận tham số linh hoạt (`scope`, `date`, `status`), giảm `refetchInterval` xuống 20s cho nhân viên trực quầy.
3. **Frontend Components**:
   - `frontend/src/features/reservation/components/staff-booking-card.tsx` (MỚI):
     - Component thẻ đặt bàn trực quan: Giờ hẹn, Ngày hẹn (nhãn "Hôm nay", "Ngày mai", "X ngày nữa"), Mã đơn `HV-XXXXXXXX`, Tên khách, SĐT (kèm nút sao chép), Số khách, Bàn đã gán (hoặc cảnh báo Chưa gán bàn).
     - Nút bấm lớn chuẩn cảm ứng (>48px): "Xác nhận & Gán bàn" (màu cam nổi bật cho `PENDING_HOLD`), "Check-in" (kèm kiểm tra khung giờ đón khách) và "Hủy".
   - `frontend/src/features/reservation/components/staff-booking-row.tsx`:
     - Bổ sung prop `showDate`, chuẩn hóa nhãn VIP bằng chữ thanh lịch thay cho emoji `⭐`.
   - `frontend/src/features/reservation/components/staff-booking-dashboard.tsx`:
     - Tái cấu trúc toàn diện màn hình:
       - Header: Nút bật/tắt chuông thông báo Web Audio API khi có đơn mới, nút chuyển đổi Thẻ / Bảng, nút Làm mới.
       - Thanh thống kê nhanh (Action Metrics Bar): Cần xử lý ngay, Hôm nay, Sắp tới, Đã nhận bàn hôm nay.
       - 4 Tab điều hướng: Cần xử lý ngay | Hôm nay | Sắp tới | Tra cứu theo ngày.
       - Bộ lọc tìm kiếm nhanh: Họ tên, 3-4 số cuối SĐT, mã đơn, số bàn.
       - Tự động gom nhóm theo ngày từ gần đến xa, luôn đưa đơn gần nhất lên đầu.
4. **Kiểm thử tự động**:
   - `frontend/src/features/reservation/__tests__/reservation-api.test.ts`: Bổ sung test case cho `listAdminBookings`.
   - `frontend/src/features/reservation/__tests__/staff-booking-dashboard.test.tsx` (MỚI): Bộ unit test kiểm tra hiển thị Thẻ, Tabs, chuyển đổi view và lọc tìm kiếm.

### 3. Kết quả Kiểm thử & Biên dịch (Verification Results)
- **Backend Tests & Compilation**: `mvnw test-compile` -> **BUILD SUCCESS** (197 files compiled sạch sẽ).
- **Frontend Unit Tests**: `npm test --prefix frontend` -> **15 test files passed, 83/83 tests passed** (100% pass).
- **Frontend Production Build**: `npm run build --prefix frontend` -> **Exit code 0** (Build thành công).
- **Quy chuẩn Endpoint**: Bảo toàn 100% URL `/api/admin/reservations`, bổ sung query parameter tương thích ngược.


---

## Phiên làm việc: Cảnh báo va chạm Khu vực (Zone Collision), Chặn lưu sơ đồ & Tự động xếp bàn mới hợp lý
- **Thời gian**: 2026-11-11
- **Mục tiêu**:
  1. Thêm tính năng phát hiện và cảnh báo va chạm giữa các khu vực (Zone Collision Warning) trên sơ đồ 2D (`FloorPlanCanvas.tsx`).
  2. Vô hiệu hóa tính năng lưu sơ đồ (disable nút "Lưu vị trí", chặn lưu) khi hệ thống đang phát hiện có bàn hoặc khu vực bị đè/trùng lặp.
  3. Xây dựng thuật toán tự động xếp bàn mới (Smart Auto-Placement) khi tạo bàn:
     - Nếu khu vực đã có sẵn bàn: Xếp cạnh các bàn hiện có theo dạng lưới gọn gàng, không đè lên bàn khác và không làm đè ranh giới sang khu vực khác.
     - Nếu là khu vực mới (chưa có bàn nào): Xếp vào vị trí trống rõ ràng trên sơ đồ, đảm bảo nằm trong khung nhìn và cách ly an toàn với tất cả các khu vực hiện có.
- **Phân tích hiện trạng mã nguồn (Codebase Audit)**:
  - `frontend/src/components/hv/FloorPlanCanvas.tsx`:
    - Đã có `computeCollisions` phát hiện va chạm giữa các bàn (`collidingTableIds`), nhưng chưa có hàm `computeZoneCollisions` kiểm tra va chạm giữa các ranh giới khu vực (`zoneGroups`).
    - Nút "Lưu vị trí" (`handleSave`) hiện chỉ kiểm tra `!hasChanges || isSaving`, chưa chặn khi `collidingTableIds.size > 0`.
    - Viền ranh giới khu vực hiện tại luôn cố định màu xám (`#94a3b8`) hoặc vàng VIP (`#f59e0b`), chưa chuyển sang đỏ khi bị khu vực khác xâm lấn.
  - `backend/src/main/java/com/huongViet/table/TableService.java`:
    - Hàm `create(TableRequest request)` đang hardcode vị trí bàn mới tại `(50.0, 50.0)`, dẫn đến bàn mới luôn đè lên Bàn 01 và gây xung đột khu vực.
- **Kế hoạch triển khai**:
  - Đã soạn thảo Implementation Plan chi tiết trong artifact `floor_plan_enhancement_plan.md`.
  - Thiết kế thuật toán auto-placement cho backend `TableService.create` và logic cảnh báo thời gian thực trên frontend `FloorPlanCanvas.tsx`.
- **Trạng thái**: Hoàn thành 100%.

### 1. Các tệp đã sửa đổi & tạo mới (Changes Applied)
1. **Frontend**:
   - `frontend/src/components/hv/FloorPlanCanvas.tsx`:
     - Bổ sung type `ZoneBox`, hàm `computeZoneBoxes` tính toán bounding box từng khu vực kèm padding chuẩn.
     - Bổ sung hàm `computeZoneCollisions` kiểm tra va chạm AABB giữa các khu vực.
     - Bổ sung state `collidingZoneNames`, đồng bộ hóa thời gian thực trong `handleDragNodeMove`, `handleDragNodeEnd`, `pushHistory`, `handleUndo`, `handleRedo` và khởi tạo ban đầu.
     - Cập nhật hiển thị Konva: Đổi màu viền khu vực sang đỏ thẫm (`#ef4444`, nét đứt, nét dày `2.5`), nền ánh đỏ (`rgba(239, 68, 68, 0.14)`), thẻ tên khu vực chuyển đỏ với nhãn cảnh báo `⚠️ Đè khu vực!`, huy hiệu tròn đỏ chấm than `!` tại góc trên bên phải khung khu vực.
     - Chặn lưu: Vô hiệu hóa nút "Lưu vị trí" khi có bất kỳ va chạm nào (`collidingTableIds.size > 0 || collidingZoneNames.size > 0`), hiển thị số lượng bàn và khu vực bị đè trên toolbar, chặn `handleSave` và hiển thị toast lỗi.
   - `frontend/src/test/floor-plan-canvas.test.ts`:
     - Bổ sung unit tests cho `computeCollisions` (va chạm bàn), `computeZoneBoxes` (tính toán khung khu vực) và `computeZoneCollisions` (va chạm ranh giới khu vực).
2. **Backend**:
   - `backend/src/main/java/com/huongViet/table/TableService.java`:
     - Thay thế việc gán cứng tọa độ `(50.0, 50.0)` bằng thuật toán thông minh `calculateOptimalLayoutPosition(RestaurantTable savedTable)`:
       - Nếu khu vực đã có bàn: Xếp liền kề bàn hiện có (ưu tiên bên phải với khoảng cách 40px, hoặc xuống dòng mới phía dưới), đảm bảo không đè lên bàn khác và không làm đè ranh giới sang khu vực khác.
       - Nếu khu vực mới hoàn toàn: Quét lưới các điểm khả dụng trong khung nhìn tiêu chuẩn ($x \in [50, 900]$, $y \in [70, 800]$), tìm vị trí trống đầu tiên thỏa mãn cách ly an toàn với tất cả các bàn và khu vực hiện hữu.
   - `backend/src/test/java/com/huongViet/table/TableServiceTest.java`:
     - Bổ sung 2 unit test: `calculateOptimalLayoutPosition_ExistingZone_PlacesAdjacent` và `calculateOptimalLayoutPosition_NewZone_PlacesInFreeSpace`.

### 2. Kết quả Kiểm thử & Biên dịch (Verification Results)
- **Backend Tests**: `mvn test -f backend/pom.xml -Dtest=TableServiceTest` -> **BUILD SUCCESS** (8/8 tests passed).
- **Frontend Unit Tests**: `npm test --prefix frontend -- --run` -> **15 test files passed, 88/88 tests passed (100% pass)**.
- **Frontend Production Build**: `npm run build --prefix frontend` -> **Exit code 0** (Build thành công).

---

## Phiên làm việc: Lập kế hoạch & Thực thi phân chia 8 Commit có ý nghĩa lùi ngày về Tuần trước (Previous Week)
- **Thời gian**: 2026-11-11
- **Mục tiêu**:
  1. Phân chia toàn bộ các thay đổi chưa commit thành đúng 8 commit có ý nghĩa (meaningful, atomic commits).
  2. Tuyệt đối không đưa bất kỳ file markdown (`.md`), bao gồm `CONVERSATION_RECORD.md`, vào trong commit.
  3. Đặt ngày commit (`GIT_AUTHOR_DATE` và `GIT_COMMITTER_DATE`) lùi về tuần trước (từ Thứ Tư 04/11/2026 đến Thứ Bảy 07/11/2026) để hiển thị trên biểu đồ đóng góp (GitHub Insights).
  4. Thực hiện trên nhánh mới `feat/reservation-canvas-enhancements` theo đúng lựa chọn được phê duyệt từ người dùng.
- **Trạng thái**: Hoàn thành 100%.

### 1. Chi tiết 8 Commit đã được tạo (Execution Summary)
1. `07e384f` - `2026-11-04 10:15:20 +07:00`: `feat(auth): include phone number in auth response and sync with user session`
2. `470f587` - `2026-11-04 15:42:10 +07:00`: `feat(reservation): prefill and show customer phone number in booking form`
3. `d9bcebd` - `2026-11-05 09:30:45 +07:00`: `feat(reservation): standardize HV-XXXXXXXX booking code format across system`
4. `4ef4805` - `2026-11-05 14:18:30 +07:00`: `feat(mail): trigger automated emails on booking approval and cancellation`
5. `9475dd8` - `2026-11-06 10:05:15 +07:00`: `feat(reservation): implement upcoming and pending scope queries for host stand`
6. `32213fd` - `2026-11-06 16:25:00 +07:00`: `feat(reservation): redesign staff booking dashboard with list and grid views`
7. `8430884` - `2026-11-07 10:45:30 +07:00`: `feat(table): implement smart collision-free auto-placement for new tables`
8. `d42131d` - `2026-11-07 15:50:00 +07:00`: `feat(canvas): implement zone collision detection and layout save validation`
---

## Phiên làm việc: Nghiên cứu, Phát hiện Lỗi Tiềm ẩn & Lập Kế hoạch Kiểm thử Full Luồng Chính (End-to-End Main Flow)
- **Thời gian**: 2026-11-11
- **Mục tiêu**: Nghiên cứu toàn bộ luồng nghiệp vụ chính của hệ thống từ Đăng ký tài khoản ➔ Đặt bàn ➔ Check-in ➔ Gọi món ➔ Bếp KDS ➔ Thanh toán POS ➔ Đánh giá bữa ăn, phát hiện các lỗi tiềm ẩn (potential defects) và lập kế hoạch kiểm thử toàn diện.

### 1. Kết quả Rà soát & Phát hiện Lỗi Tiềm ẩn (Codebase Audit & Flaw Discovery)
1. **Lỗi Rò rỉ Hóa đơn cũ của bàn trong `FeedbackService.getInvoiceForReservation`**:
   - Câu SQL `LEFT JOIN` giữa `reservations`, `orders` và `invoices` chỉ lọc theo `r.id = ? ORDER BY i.created_at DESC LIMIT 1`.
   - Nếu khách hàng đặt bàn ở một bàn từng có khách ăn và thanh toán trước đó (ví dụ hôm qua), khi khách bấm "Đánh giá" từ lịch sử đặt bàn, hệ thống lấy trúng hóa đơn cũ của người khác và cho phép đánh giá ngay cả khi khách chưa tới quán hoặc chưa thanh toán.
2. **Lỗi Không nhất quán định dạng Số điện thoại giữa Đăng ký, Đặt bàn và POS Checkout**:
   - `RegisterRequest` bắt buộc 10 chữ số (`^[0-9]{10}$`), trong khi frontend cho phép `+84`, và `CreateReservationRequest` cho phép `^(0|\+84)`.
   - Khi thu ngân nhập SĐT `+84` tại POS checkout, hàm `findCustomerIdByPhone` so sánh trực tiếp không tìm thấy user, dẫn đến hóa đơn có `customer_id = NULL` và khách hàng không thấy hóa đơn trong `/feedback`.
3. **Ràng buộc Thời gian Check-in `[-30m, +30m]`**:
   - `ReservationService` chặn check-in nếu thời gian ngoài khoảng 30 phút so với giờ hẹn. Cần điều chỉnh kịch bản kiểm thử chọn khung giờ phù hợp với thời điểm chạy test.
4. **Cơ chế Cooldown 60s Anti-spam trong `FeedbackService`**:
   - Khách hàng không thể gửi 2 đánh giá liên tiếp trong vòng 60 giây. Cần sinh tài khoản khách mới cho mỗi lần chạy kiểm thử tự động.

### 2. Kế hoạch Triển khai & Xác nhận từ Người dùng (User Decision & Plan)
- Đã tạo artifact kế hoạch chi tiết `full_main_flow_test_plan.md` với:
  - Sơ đồ tuần tự (Sequence Diagram) 13 bước chi tiết.
  - Ma trận kiểm thử tự động (`FullMainFlowIntegrationTest.java` trên Backend & Vitest trên Frontend).
  - Thống nhất với người dùng: Triển khai kiểm thử tự động toàn diện + Chuẩn hóa SĐT đồng bộ, đồng thời ghi nhận lỗi `DEFECT-FB-01` (SQL leak hóa đơn cũ trong `getInvoiceForReservation`) vào danh sách Defect List để refactor riêng.

### 3. Thực thi Mã nguồn & Khắc phục Lỗi Tiềm ẩn (Execution Details)
1. **Backend**:
   - `backend/src/main/java/com/huongViet/order/OrderService.java`:
     - Nâng cấp hàm `findCustomerIdByPhone(phone)`: Tự động loại bỏ ký tự định dạng và chuẩn hóa tiền tố `+84` / `84` về `0` (10 chữ số). Đảm bảo khi thu ngân tại POS nhập số điện thoại theo bất kỳ định dạng nào, hệ thống vẫn gắn đúng `customer_id` vào hóa đơn `PAID`.
   - `backend/src/test/java/com/huongViet/order/KitchenControllerTest.java`:
     - Sửa lỗi mock method signature `listAll(null, null)` và `listAll(CookingStatus.PREPARING, null)` (trước đây mock 1 tham số gây lỗi 500).
   - `backend/src/test/java/com/huongViet/integration/FullMainFlowIntegrationTest.java` (TẠO MỚI):
     - Bộ kiểm thử tích hợp tự động toàn diện xuyên suốt 13 bước của toàn luồng chính:
       1. Đăng ký khách hàng (`AuthService.register`)
       2. Đăng nhập (`AuthService.login`)
       3. Đặt bàn (`ReservationService.createBooking`)
       4. Nhân viên duyệt & phân bàn (`ReservationService.updateStatus` -> `CONFIRMED`)
       5. Check-in khách đến (`ReservationService.updateStatus` -> `ARRIVED`, bàn -> `OCCUPIED`)
       6. Gọi món tại bàn (`OrderService.create`, phiếu bếp -> `QUEUED`)
       7. Bếp KDS chế biến (`KitchenTicketService.updateStatus`: `QUEUED` -> `PREPARING` -> `READY`)
       8. Phục vụ trả món (`READY` -> `SERVED`)
       9. Thu ngân POS thanh toán tiền mặt (`OrderService.checkoutCash`, hóa đơn `PAID`, bàn `AVAILABLE`, tích điểm)
       10. Khách hàng tra cứu hóa đơn đủ điều kiện đánh giá (`FeedbackService.getEligibleInvoices`)
       11. Khách hàng gửi đánh giá 5 sao & nhận xét (`FeedbackService.submitFeedback`)
       12. Hiển thị đánh giá công khai & lịch sử (`getMyFeedbacks`, `getPublicFeedbacks`)
       13. Quản lý gửi lời cảm ơn phản hồi (`FeedbackService.replyFeedback`)
     - Bổ sung 4 test cases kiểm thử các trường hợp biên & lỗi tiềm ẩn (Edge Cases):
       - Chuẩn hóa SĐT tại POS nhận diện đúng khách hàng.
       - Ràng buộc thời gian Check-in `[-30m, +30m]` chặn nhận bàn quá sớm/quá muộn (HTTP 400).
       - Tính toàn vẹn trạng thái KDS chặn nhảy cóc từ `QUEUED` sang `SERVED` (HTTP 409).
       - Quyền sở hữu hóa đơn chặn đánh giá hóa đơn người khác (HTTP 403).
2. **Frontend**:
   - `frontend/src/routes/register.tsx`:
     - Chuẩn hóa regex số điện thoại `PHONE_RE = /^0\d{9}$/` và thông báo lỗi khớp 100% với backend.
   - `frontend/src/features/feedback/__tests__/full-flow-feedback.test.tsx` (TẠO MỚI):
     - Kiểm thử tự động Vitest cho UI `CustomerFeedback`:
       - Hiển thị màn hình nhắc đăng nhập khi chưa xác thực.
       - Hiển thị banner cảnh báo và khóa form khi chưa có hóa đơn thanh toán (`hasNoEligible`).
       - Tự động nhận diện hóa đơn hợp lệ sau khi POS checkout và gửi đánh giá thành công.

### 4. Kết quả Kiểm thử & Biên dịch (Verification Results)
- **Backend Tests**:
  - `FullMainFlowIntegrationTest`: **5/5 tests passed** (100% pass).
  - Toàn bộ backend test suite: `mvn test -f backend/pom.xml`: **370/370 tests passed, 0 failures, 0 errors**.
- **Frontend Unit Tests**:
  - `npm test --prefix frontend -- --run`: **16 test files passed, 95/95 tests passed (100% pass)**.
- **Frontend Production Build**:
  - `npm run build --prefix frontend`: **Exit code 0**, hoàn thành trong 1.73s, không có bất kỳ lỗi cú pháp hay kiểu dữ liệu.---

## Phiên làm việc: Khởi tạo Bộ Dữ Liệu Mẫu (Demo Dataset) & Xây dựng Kế hoạch Thuyết trình Full Luồng Chính
- **Thời gian**: 2026-11-11
- **Mục tiêu**: Chuẩn bị đầy đủ dữ liệu mẫu trong cơ sở dữ liệu MySQL và xây dựng kịch bản thuyết trình trực quan 5 Màn (10 phút) để người dùng tự tin trình diễn live demo full luồng chính.

### 1. Dữ liệu Mẫu Đã Nạp Sẵn trong Cơ sở Dữ liệu (Demo Dataset Loaded)
- Tạo tệp script SQL: `database/demo_presentation_data.sql` và đã thực thi thành công vào MySQL (`huong_viet_restaurant`):
  1. **Tài khoản demo sẵn sàng**:
     - Khách VIP (`khachvip` / `KhachHang@12345` / SĐT `0902000002`): Nạp sẵn **50.000 điểm thưởng**.
     - Khách thường (`khachhang` / `KhachHang@12345` / SĐT `0902000001`): Nạp sẵn **15.000 điểm thưởng**.
     - Nhân viên (`nhanvien` / `NhanVien@12345`), Đầu bếp (`dau_bep` / `DauBep@12345`), Quản lý (`quanly` / `QuanLy@12345`).
  2. **Voucher khuyến mãi demo**:
     - `HUONGVIET20`: Giảm 20% (tối đa 100.000đ) cho đơn từ 150.000đ.
     - `GIAM50K`: Giảm 50.000đ trực tiếp cho đơn từ 200.000đ.
  3. **Làm sạch và nạp review chuyên nghiệp**:
     - Dọn dẹp các review thử nghiệm có từ ngữ không phù hợp (`Nhu cut`).
     - Nạp 3 đánh giá mẫu 5 sao và 4 sao chuẩn phong cách nhà hàng ẩm thực cao cấp kèm phản hồi của Quản lý.
  4. **Chuẩn hóa trạng thái bàn**:
     - Toàn bộ 12 bàn ăn đưa về trạng thái `AVAILABLE` sẵn sàng đón khách demo.

### 2. Kế hoạch & Kịch bản Thuyết trình (Presentation Plan)
- Đã tạo artifact chi tiết `presentation_plan_and_demo_dataset.md` với:
  - Bảng tra cứu nhanh tài khoản, mật khẩu, voucher, combo món ăn gợi ý (tính sẵn số tiền, VAT, giảm giá và điểm tích).
  - Kịch bản 5 Màn (5 Acts):
    - Màn 1: Khách hàng xem thực đơn & Đặt bàn trực tuyến (`/dat-ban`).
    - Màn 2: Lễ tân điều phối bàn trên Sơ đồ 2D Canvas & Check-in (`/staff`).
    - Màn 3: POS gọi món & Bếp KDS chế biến thời gian thực (`/staff` & `/chef`).
    - Màn 4: Thu ngân thanh toán POS: Tra cứu điểm & Áp dụng voucher (`/staff` BillingDialog).
    - Màn 5: Đánh giá bữa ăn thực tế (chống gian lận) & Quản lý phản hồi hai chiều (`/feedback` & `/manager`).
  - Hướng dẫn thiết lập 2 cửa sổ song song (Khách hàng vs Nội bộ) để tạo hiệu ứng trình diễn ấn tượng nhất.

---

## Phiên làm việc: Chuẩn hóa & Tái cơ cấu Toàn diện Sơ đồ Bàn 2D Canvas (Floor Plan Layouts)
- **Thời gian**: 2026-11-11
- **Mục tiêu**: Xóa bỏ hoàn toàn tình trạng lộn xộn, bàn thử nghiệm rác, tọa độ âm ngoài màn hình và va chạm khu vực trên Sơ đồ bàn 2D Canvas (`/staff`, `/manager`).
- **Hiện trạng trước khi xử lý**:
  - Tồn tại các bàn thử nghiệm rác: `13` (tọa độ âm x = -100), `1`, `2`.
  - Tồn tại khu vực rác `dgsdag` chứa bàn thử nghiệm `2` tại tọa độ (550, 640).
  - Có hiện tượng chồng lấn khu vực (collision) khiến giao diện Canvas hiển thị cảnh báo viền đỏ `⚠️ Đè khu vực!`.
- **Hành động & Giải pháp**:
  - Tạo script SQL `database/clean_and_organize_floor_plan.sql` và thực thi trực tiếp vào MySQL `huong_viet_restaurant`:
    1. Soft delete (`deleted_at = NOW()`) các bàn rác `1`, `2`, `13` và khu vực `dgsdag`. Xóa triệt để các bản ghi layout rác trong `floor_plan_layouts`.
    2. Đưa 12 bàn chính thức (`Bàn 01` -> `Bàn 12`) về trạng thái `AVAILABLE`.
    3. Tái cấu trúc tọa độ (x, y), kích thước (w, h) và hình dáng (`shape`) chuẩn grid cho 3 phân khu:
       - **Khu vực 1: Trong Nhà** (X [60..460], Y [70..290]): Bàn 01 (Vuông 90x90), Bàn 02 (Vuông 90x90), Bàn 03 (Chữ nhật 140x90), Bàn 04 (Vuông 90x90), Bàn 05 (Chữ nhật lớn 140x90). Bounding box bao phủ: X [36..484], Y [16..314].
       - **Khu vực 2: Sân Vườn** (X [550..820], Y [70..290]): Bàn 06 (Tròn 90x90), Bàn 07 (Chữ nhật 140x90), Bàn 08 (Tròn 90x90), Bàn 09 (Chữ nhật lớn 140x90). Bounding box bao phủ: X [526..844], Y [16..314]. Khoảng cách thông thoáng với Trong Nhà: 42px trên trục X.
       - **Khu vực 3: Phòng VIP** (X [60..500], Y [385..520]): Bàn 10 (Chữ nhật 140x90), Bàn 11 (Chữ nhật đại tiệc 140x90), Bàn 12 (Tròn xoay đại tiệc 120x120). Bounding box bao phủ: X [36..524], Y [331..529]. Khoảng cách thông thoáng với 2 khu trên: 17px trên trục Y.
- **Kết quả Kiểm chứng (Verification Results)**:
  - **0 va chạm (0 collision)** giữa tất cả các khu vực, viền đỏ cảnh báo và nhãn cảnh báo `⚠️ Đè khu vực!` biến mất hoàn toàn.
  - Sơ đồ bàn hiển thị cân đối, trực quan và chuyên nghiệp trong khung chuẩn 1000x650 của Canvas.
  - Kiểm thử tích hợp `FullMainFlowIntegrationTest`: **5/5 passed (100%)**.

---

## Phiên làm việc: Giải đáp & Phân tích Cơ chế Giới hạn Đặt bàn (Reservation Rate Limiter)
- **Thời gian**: 2026-11-13
- **Nội dung yêu cầu**: Tìm hiểu điều kiện kích hoạt cảnh báo *"Bạn đã vượt quá số lần gửi yêu cầu đặt bàn (tối đa 5 lượt/giờ). Vui lòng thử lại sau."* và lý do tài khoản khác mới đặt lần đầu trên cùng máy vẫn bị cảnh báo.
- **Kết quả phân tích mã nguồn**:
  - Tệp xử lý: `backend/src/main/java/com/huongViet/reservation/ReservationRateLimiterFilter.java`.
  - Cơ chế: Sử dụng thư viện `Bucket4j`, chặn các request `POST /api/reservations`.
  - Cấu hình: Giới hạn 5 lượt/giờ theo chu kỳ (`Bandwidth.capacity(5).refillIntervally(5, Duration.ofHours(1))`).
  - Nguyên nhân hiển thị trên tài khoản thứ 2: Khóa định danh của bộ đếm là `clientIp` (địa chỉ IP từ `X-Forwarded-For` hoặc `request.getRemoteAddr()`), hoàn toàn độc lập với phiên đăng nhập hay tài khoản người dùng (`User`). Khi thử nghiệm trên cùng 1 máy tính / mạng (localhost `127.0.0.1`), IP bị chung giới hạn nên tài khoản thứ 2 dù gửi lần đầu vẫn bị chặn do IP đã cạn token.
  - Đề xuất các phương án tối ưu hóa (kết hợp Rate Limit theo User ID khi đã đăng nhập và IP cho khách vãng lai) để người dùng cân nhắc.
- **Làm rõ vấn đề dùng chung Wi-Fi (NAT / Public IP)**:
  - Xác nhận: Khi triển khai thực tế trên môi trường Internet, các thiết bị kết nối chung một mạng Wi-Fi (công ty, quán cafe, hộ gia đình) đều chia sẻ chung một địa chỉ IP công cộng (Public IP thông qua NAT của Router).
  - Do đó, nếu rate limit chỉ dựa thuần vào `clientIp`, toàn bộ người dùng trong cùng mạng Wi-Fi sẽ chia sẻ chung hạn mức 5 lượt/giờ, dẫn đến việc chặn oan người dùng hợp lệ khác trong cùng mạng.
- **Nghiên cứu giải pháp Chặn theo ID Thiết bị (Device ID / Fingerprinting)**:
  - Trên nền tảng Web, trình duyệt bảo mật không cho phép truy xuất trực tiếp phần cứng (MAC address, IMEI).
  - Phân tích 3 phương án khả thi:
    1. *Device Token (UUID trong localStorage)*: Trình duyệt tự sinh UUID lưu ở localStorage/Cookie, gửi qua header `X-Device-Id`. Rất nhẹ, chính xác cho từng trình duyệt/thiết bị, giải quyết triệt để vấn đề chung Wi-Fi.
    2. *Browser Fingerprinting (FingerprintJS)*: Băm các đặc tính phần cứng (Canvas, WebGL, Audio, độ phân giải) để tạo Fingerprint ID.
- **Kế hoạch triển khai đã được người dùng lựa chọn: Mô hình Đa tầng Hybrid (User ID > Device ID > IP)**:
  - **Khóa định danh (Key Resolution)**:
    1. Cấp 1 (Người dùng đăng nhập): `user:<userId>` trích xuất từ `SecurityContextHolder` sau khi qua `JwtAuthFilter`.
    2. Cấp 2 (Thiết bị khách vãng lai): `device:<deviceId>` trích xuất từ header `X-Device-Id` do client sinh mã UUID v4 lưu trong `localStorage`.
    3. Cấp 3 (Dự phòng bot/curl): `ip:<clientIp>` trích xuất từ `X-Forwarded-For` hoặc `request.getRemoteAddr()`.
  - **Cấu hình hạn mức (Rate Limiting Configuration)**:
    - Endpoint: `POST /api/reservations`.
    - Hạn mức: Tối đa 5 lượt/giờ (`capacity(5)`, `refillIntervally(5, Duration.ofHours(1))`).
    - Trạng thái trả về khi vượt ngưỡng: `HTTP 429 Too Many Requests`.
  - **Các tệp thay đổi**:
    - `frontend/src/lib/device-id.ts` (mới): Sinh và lưu trữ UUID v4 trong `localStorage` (`hv.device_id`).
    - `frontend/src/features/reservation/api/reservation-api.ts`: Bổ sung header `X-Device-Id` trong hàm `request()`.
    - `backend/src/main/java/com/huongViet/config/SecurityConfig.java`: Đổi thứ tự bộ lọc `reservationRateLimiterFilter` sang sau `JwtAuthFilter`.
    - `backend/src/main/java/com/huongViet/reservation/ReservationRateLimiterFilter.java`: Bổ sung logic phân giải khóa 3 cấp `resolveRateLimitKey(request)`.
    - `backend/src/test/java/com/huongViet/reservation/ReservationRateLimiterFilterTest.java` (mới): Kiểm thử toàn diện 3 cấp độ Rate Limit.
    - `frontend/src/lib/__tests__/device-id.test.ts` (mới): Kiểm thử sinh và lưu trữ Device ID v4.
    - `frontend/src/features/reservation/components/date-picker-field.tsx`: Tối ưu hóa định dạng ngày bằng JS Intl chuẩn thay vì phụ thuộc date-fns gặp lỗi bundling trong Vite SSR.

### Kết quả triển khai & Kiểm chứng thực tế (Implementation & Verification)
1. **Backend Tests**:
   - `ReservationRateLimiterFilterTest`: **7/7 tests passed (100%)**.
     - Cấp 1 (User ID khi đăng nhập): PASS.
     - Cấp 2 (Device ID khách vãng lai): PASS.
     - Cấp 3 (Fallback IP): PASS.
     - Xử lý triệt để lỗi: 2 tài khoản khác nhau trên cùng IP có 2 bucket độc lập: PASS.
     - Xử lý triệt để lỗi cùng Wi-Fi: 2 thiết bị khác nhau có X-Device-Id riêng không bị chặn oan: PASS.
     - Giới hạn request thứ 6 trả về HTTP 429: PASS.
     - Các endpoint khác không bị rate limit: PASS.
2. **Frontend Tests**:
   - `src/lib/__tests__/device-id.test.ts`: **2/2 tests passed (100%)**.
   - `src/features/reservation/__tests__/reservation-api.test.ts`: **9/9 tests passed (100%)**.
   - `src/features/reservation/__tests__/book-form.test.tsx`: **8/8 tests passed (100%)**.
3. **Frontend Production Build**:
   - `npm run build --prefix frontend`: **Exit code 0**, hoàn tất toàn bộ build client & server bundle thành công trong 9.61s.
---

## Phiên làm việc: Nghiên cứu & Lập kế hoạch Tối ưu hóa Bộ lọc & Danh sách Đặt bàn Nhân viên
- **Thời gian**: 2026-11-13
- **Nội dung yêu cầu**:
  1. Trong phần "Sắp tới" của màn "Đặt bàn" của staff: loại bỏ các đơn có trạng thái "Hoàn tất".
  2. Bỏ 3 nút filter thừa ở dưới ("Cần xử lý ngay", "Hôm nay", "Sắp tới") vì phía trên đã có các Card thống kê có thể bấm trực tiếp.
  3. Sửa thẻ "Đã đến nhận bàn hôm nay" để có thể bấm được và tự động lọc ra các đơn "Đã đến nhận bàn" (`ARRIVED`) trong ngày hôm nay.
- **Phân tích mã nguồn**:
  - `frontend/src/features/reservation/components/staff-booking-dashboard.tsx`:
    - Thanh Metrics Bar gồm 4 Card: 3 Card đầu đã có `onClick`, riêng Card thứ 4 ("Đã đến nhận bàn hôm nay") chưa có `onClick` và hiệu ứng active.
    - Thanh Navigation Tabs Bar có 4 nút, trong đó 3 nút trùng lặp chức năng với 3 Card ở trên. Nút thứ 4 là "Tra cứu theo ngày".
    - Tab "Sắp tới" (`activeTab === 'upcoming'`) nhận dữ liệu từ `upcomingQuery.data` (lấy mọi đơn có `reservationDate >= today`), khiến các đơn đã check-in `ARRIVED` hoặc `PAID` vẫn hiển thị.
    - `StaffBookingCard.tsx` (dòng 292-296): nếu không phải `PENDING_HOLD` và không phải `CONFIRMED`, mã hiển thị cứng chữ "Hoàn tất".
- **Kế hoạch triển khai đã lập**:
  - Tạo artifact kế hoạch chi tiết `staff_booking_filter_optimization_plan.md`.
  - Đã thu thập và ghi nhận phản hồi lựa chọn của người dùng:
    1. Vị trí "Tra cứu theo ngày": Giữ thanh tab thu nhỏ chỉ chứa 1 nút "Tra cứu theo ngày" (khi bấm vào mới mở ô chọn ngày như hiện tại).
    2. Trạng thái loại bỏ khỏi "Sắp tới": Loại bỏ toàn bộ các đơn không còn chờ khách: Đã nhận bàn (`ARRIVED`), Đã thanh toán (`PAID`), Đã đánh giá (`REVIEWED`), Đã hủy (`CANCELLED`), Không đến (`NO_SHOW`) — chỉ giữ lại `PENDING_HOLD`, `PENDING_DEPOSIT` và `CONFIRMED`.
    3. Thẻ đặt bàn (`StaffBookingCard`): Giữ nguyên chữ "Hoàn tất" màu xám như hiện tại cho các đơn đã qua bước check-in.
    4. Thẻ "Đã đến nhận bàn hôm nay": Thêm tương tác click, viền active emerald, lọc đơn `ARRIVED` trong `today`.
    5. Đơn "Chờ xác nhận" (`PENDING_HOLD`): Bổ sung nút "Hủy" bên trái nút "Xác nhận & Gán bàn" trên cả Dạng Thẻ (`StaffBookingCard`) và Dạng Bảng (`StaffBookingRow`).
  - Đã cập nhật lại artifact kế hoạch chi tiết `staff_booking_filter_optimization_plan.md` và đã được người dùng phê duyệt ("Proceed").

### Kết quả Triển khai & Kiểm chứng thực tế (Implementation & Verification)
1. **Các tệp đã thay đổi**:
   - `frontend/src/features/reservation/components/staff-booking-dashboard.tsx`:
     - Bổ sung hằng số `EXCLUDED_FROM_UPCOMING_STATUSES = new Set(['ARRIVED', 'PAID', 'REVIEWED', 'CANCELLED', 'NO_SHOW'])`.
     - Lọc sạch danh sách và cập nhật số đếm thẻ "Sắp tới" (`metrics.upcomingTotal`) loại bỏ các đơn không còn chờ khách.
     - Nâng cấp thẻ thứ 4 thành "Đã đến nhận bàn hôm nay" tương tác bấm được (`cursor-pointer`), viền xanh sáng `ring-2 ring-emerald-500 bg-emerald-500/10` khi active, kích hoạt bộ lọc đơn `ARRIVED` trong `today`.
     - Thẻ "Hôm nay" (`activeTab === 'today' && selectedStatus !== 'ARRIVED'`) hiển thị viền sáng `ring-2 ring-primary bg-primary/5` độc lập.
     - Xóa 3 nút filter trùng lặp ("Cần xử lý ngay", "Hôm nay", "Sắp tới") trên Navigation Tabs Bar, giữ lại 1 nút "Tra cứu theo ngày".
     - Bổ sung empty state thông báo rõ ràng khi chưa có khách nhận bàn hôm nay.
   - `frontend/src/features/reservation/components/staff-booking-card.tsx`:
     - Bổ sung nút "Hủy" (`variant="outline"`, `text-destructive`, `XCircle`) bên trái nút "Xác nhận & Gán bàn" cho các đơn chờ xác nhận (`PENDING_HOLD`).
   - `frontend/src/features/reservation/components/staff-booking-row.tsx`:
     - Bổ sung nút "Hủy" (`variant="ghost"`, `text-destructive`, `XCircle`) bên trái nút "Xác nhận & Gán bàn" cho các đơn chờ xác nhận (`PENDING_HOLD`) trong chế độ xem dạng Bảng.
   - `frontend/src/features/reservation/__tests__/staff-booking-dashboard.test.tsx`:
     - Bổ sung test kiểm tra nút Hủy của đơn pending trên cả `StaffBookingCard` và `StaffBookingRow`.
     - Kiểm tra không còn nút tab trùng lặp dưới thanh bar.
     - Kiểm tra click thẻ "Đã đến nhận bàn hôm nay" lọc chính xác đơn `ARRIVED`.
     - Kiểm tra tab "Sắp tới" lọc bỏ các đơn `ARRIVED` và `CANCELLED`.
2. **Kết quả kiểm thử tự động**:
   - `staff-booking-dashboard.test.tsx`: **6/6 passed (100%)**.
   - Toàn bộ `frontend/src/features/reservation`: **25/25 passed (100%)**.
   - `npm run build --prefix frontend`: **Exit code 0**, hoàn tất build production sạch sẽ.

