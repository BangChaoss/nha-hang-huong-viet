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

## Phiên làm việc: Tối ưu hóa Bộ lọc & Danh sách Đặt bàn Nhân viên
- **Thời gian**: 2026-11-13
- **Nội dung yêu cầu**:
  1. Trong phần "Sắp tới" của màn "Đặt bàn" của staff: loại bỏ các đơn có trạng thái "Hoàn tất".
  2. Bỏ 3 nút filter thừa ở dưới ("Cần xử lý ngay", "Hôm nay", "Sắp tới") vì phía trên đã có các Card thống kê có thể bấm trực tiếp.
  3. Sửa thẻ "Đã đến nhận bàn hôm nay" để có thể bấm được và tự động lọc ra các đơn "Đã đến nhận bàn" (`ARRIVED`) trong ngày hôm nay.
  4. Đơn "Chờ xác nhận" (`PENDING_HOLD`): Bổ sung nút "Hủy" bên trái nút "Xác nhận & Gán bàn" trên cả Dạng Thẻ (`StaffBookingCard`) và Dạng Bảng (`StaffBookingRow`).
- **Phân tích mã nguồn**:
  - `frontend/src/features/reservation/components/staff-booking-dashboard.tsx`:
    - Thanh Metrics Bar gồm 4 Card: 3 Card đầu đã có `onClick`, riêng Card thứ 4 ("Đã đến nhận bàn hôm nay") chưa có `onClick` và hiệu ứng active.
    - Thanh Navigation Tabs Bar có 4 nút, trong đó 3 nút trùng lặp chức năng với 3 Card ở trên. Nút thứ 4 là "Tra cứu theo ngày".
    - Tab "Sắp tới" (`activeTab === 'upcoming'`) nhận dữ liệu từ `upcomingQuery.data` (lấy mọi đơn có `reservationDate >= today`), khiến các đơn đã check-in `ARRIVED` hoặc `PAID` vẫn hiển thị.
    - `StaffBookingCard.tsx` (dòng 292-296): nếu không phải `PENDING_HOLD` và không phải `CONFIRMED`, mã hiển thị cứng chữ "Hoàn tất".
- **Kế hoạch triển khai đã chốt**:
  - Tạo artifact kế hoạch chi tiết `staff_booking_filter_optimization_plan.md` và đã được người dùng phê duyệt ("Proceed").
  - Đã thu thập và ghi nhận phản hồi lựa chọn của người dùng:
    1. Vị trí "Tra cứu theo ngày": Giữ thanh tab thu nhỏ chỉ chứa 1 nút "Tra cứu theo ngày" (khi bấm vào mới mở ô chọn ngày như hiện tại).
    2. Trạng thái loại bỏ khỏi "Sắp tới": Loại bỏ toàn bộ các đơn không còn chờ khách: Đã nhận bàn (`ARRIVED`), Đã thanh toán (`PAID`), Đã đánh giá (`REVIEWED`), Đã hủy (`CANCELLED`), Không đến (`NO_SHOW`) — chỉ giữ lại `PENDING_HOLD`, `PENDING_DEPOSIT` và `CONFIRMED`.
    3. Thẻ đặt bàn (`StaffBookingCard`): Giữ nguyên chữ "Hoàn tất" màu xám như hiện tại cho các đơn đã qua bước check-in.
    4. Thẻ "Đã đến nhận bàn hôm nay": Thêm tương tác click, viền active emerald, lọc đơn `ARRIVED` trong `today`.
    5. Đơn "Chờ xác nhận" (`PENDING_HOLD`): Bổ sung nút "Hủy" bên trái nút "Xác nhận & Gán bàn" trên cả Dạng Thẻ (`StaffBookingCard`) và Dạng Bảng (`StaffBookingRow`).

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
3. **Commit & Push GitHub**:
   - **Nhánh**: `feat/uc09-uc13-manage-reservation` (đã đồng bộ với `main`).
   - **Commit**: `f2e4201 feat(reservation): optimize staff booking filters and add cancel button for pending reservations`.
   - **Trạng thái**: Đã push thành công lên `origin/feat/uc09-uc13-manage-reservation`. Sẵn sàng tạo Pull Request vào `main`.
