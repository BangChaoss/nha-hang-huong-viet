Đóng vai trò là Principal System Reliability Engineer (SRE). 
Ứng dụng đã pass Build-time nhưng đang gặp lỗi Runtime và có nguy cơ bị lỗi dây chuyền (regression). 
Tôi không muốn kiểm tra thủ công từng API/màn hình. Hãy thực hiện quy trình AUTOMATED RUNTIME SWEEP toàn diện sau:

### 1. KIỂM SOÁT PHẠM VI (BLAST RADIUS CHECK)
- Chạy `git status` và `git diff --name-only`.
- Liệt kê toàn bộ các file đã bị chỉnh sửa trong task này.
- Nếu bạn thấy có bất kỳ file nào thuộc về logic cũ của đồng đội bị sửa đổi ngoài phạm vi tính năng mới, hãy rollback (`git checkout`) những file đó ngay lập tức.

### 2. TỰ ĐỘNG QUÉT TOÀN BỘ RUNTIME BACKEND (Automated Integration Test)
- Tại `backend/`: Hãy tạo một file integration test tạm thời: `src/test/java/.../FeatureSmokeTest.java` sử dụng `@SpringBootTest` và `MockMvc`.
- Viết test case gọi trực tiếp vào TẤT CẢ các endpoint mới tạo (cả trường hợp có token và không token).
- Tự chạy lệnh: `./mvnw test -Dtest=FeatureSmokeTest` (hoặc `gradle test`).
- Phân tích kết quả: Nếu có lỗi (Security 401/403, JPA Lazy loading, Jackson JSON mapping, hay Bean Context fail) -> SỬA TOÀN BỘ CÁC LỖI ĐÓ NGAY LẬP TỨC.

### 3. TỰ ĐỘNG QUÉT TOÀN BỘ RUNTIME FRONTEND
- Rà soát lại toàn bộ cây Component của tính năng mới:
  - Kiểm tra các đoạn code truy cập dữ liệu sâu (ví dụ: `data.items.map`, `response.data.user.name`): Đảm bảo 100% đều có Optional Chaining (`data?.items?.map`) hoặc Guard Clause (`if (!data) return <Skeleton />`).
  - Kiểm tra xem có `useEffect` nào bị lặp vô tận (missing dependency) hay không.
  - Kiểm tra file gọi API: Đảm bảo tầng bóc tách dữ liệu (`response.data` hay `response.data.data`) khớp chính xác với cấu trúc JSON thật từ Backend vừa test ở Bước 2.

### 4. BÁO CÁO NGHIỆM THU TỔNG THỂ
Sau khi tự chạy test và sửa lỗi xong, in ra báo cáo:
1. Danh sách các file bị sửa nhầm đã được khôi phục (nếu có).
2. Toàn bộ các lỗi Runtime tiềm ẩn đã được phát hiện và tự động sửa.
3. Kết quả chạy test cuối cùng (All Green / Pass).