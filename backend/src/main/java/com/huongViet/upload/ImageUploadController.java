package com.huongViet.upload;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API tải ảnh lên từ máy người dùng (dùng cho ảnh món ăn).
 *
 * <p>Chỉ Manager/Admin được dùng — khớp với quyền chỉnh sửa thực đơn
 * (BR-49, BR-54). Ảnh được lưu vào thư mục local và phục vụ lại qua
 * {@code /uploads/**}.</p>
 */
@RestController
@RequestMapping("/api/admin/uploads")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8081"})
public class ImageUploadController {

    private static final String MENU_DIRECTORY = "menu";

    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * POST /api/admin/uploads/images
     *
     * @param file tệp ảnh, gửi dưới tên field {@code file}
     * @return {@code { "url": "...", "backend": "cloudinary" }}
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        StoredImage stored = imageStorageService.store(file, MENU_DIRECTORY);
        return ResponseEntity.ok(new ImageUploadResponse(stored.url(), stored.backend()));
    }
}
