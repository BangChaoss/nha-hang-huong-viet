package com.huongViet.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageServiceTest {

    @TempDir
    Path uploadDir;

    private ImageStorageService storageService;

    @BeforeEach
    void setUp() {
        // Cloudinary chưa cấu hình -> lưu ảnh trên ổ đĩa cục bộ.
        CloudinaryStorage cloudinary = new CloudinaryStorage("", "huongviet/menu");
        storageService = new ImageStorageService(cloudinary, "huongviet/menu", uploadDir.toString(), "/uploads", 5 * 1024 * 1024);
    }

    @Test
    @DisplayName("store - lưu ảnh và trả về đường dẫn công khai trong thư mục con")
    void store_SavesFile_ReturnsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mon-an.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        StoredImage stored = storageService.store(file, "menu");

        assertTrue(stored.url().startsWith("/uploads/menu/"));
        assertTrue(stored.url().endsWith(".jpg"));
        assertEquals("local", stored.backend());

        Path saved = uploadDir.resolve("menu").resolve(stored.url().substring("/uploads/menu/".length()));
        assertTrue(Files.exists(saved), "Ảnh phải được ghi xuống đĩa");
    }

    @Test
    @DisplayName("store - tên file luôn được sinh lại, không dùng tên do client gửi lên")
    void store_IgnoresClientFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.png", "image/png", new byte[]{1, 2, 3});

        StoredImage stored = storageService.store(file, "menu");

        assertFalse(stored.url().contains(".."));
        assertFalse(stored.url().contains("passwd"));
    }

    @Test
    @DisplayName("store - ném lỗi 415 khi định dạng không phải ảnh")
    void store_NonImage_ThrowsUnsupportedMediaType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "tay-khoan.txt", "text/plain", "hello".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storageService.store(file, "menu"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
    }

    @Test
    @DisplayName("store - ném lỗi 413 khi ảnh vượt dung lượng cho phép")
    void store_TooLarge_ThrowsPayloadTooLarge() {
        ImageStorageService limited = new ImageStorageService(
                new CloudinaryStorage("", "huongviet/menu"), "huongviet/menu", uploadDir.toString(), "/uploads", 10);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lon.jpg", "image/jpeg", new byte[100]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> limited.store(file, "menu"));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
    }

    @Test
    @DisplayName("store - ném lỗi 400 khi không có tệp nào được chọn")
    void store_Empty_ThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storageService.store(file, "menu"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("delete - xoá được ảnh đã lưu trong thư mục upload")
    void delete_RemovesStoredFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mon-an.png", "image/png", new byte[]{1, 2, 3});
        String url = storageService.store(file, "menu").url();

        boolean deleted = storageService.delete(url);

        assertTrue(deleted);
        assertFalse(Files.exists(uploadDir.resolve("menu").resolve(url.substring("/uploads/menu/".length()))));
    }

    @Test
    @DisplayName("delete - bỏ qua đường dẫn không thuộc thư mục upload (chống path traversal)")
    void delete_OutsideUploadDir_IsIgnored() {
        assertFalse(storageService.delete("/etc/passwd"));
        assertFalse(storageService.delete("https://example.com/photo.jpg"));
        assertFalse(storageService.delete("/uploads/../../secret.txt"));
        assertFalse(storageService.delete(null));
    }

    @Test
    @DisplayName("storePlaceholder - tạo ảnh minh hoạ SVG trong thư mục upload")
    void storePlaceholder_CreatesSvgFile() {
        StoredImage stored = storageService.storePlaceholder("menu", "mon-1.svg", "Phở Bò Tái", "#3f5c4a");

        assertEquals("/uploads/menu/mon-1.svg", stored.url());
        assertEquals("local", stored.backend());

        Path saved = uploadDir.resolve("menu").resolve("mon-1.svg");
        assertTrue(Files.exists(saved));

        String content = readString(saved);
        assertTrue(content.contains("Phở Bò Tái"));
        assertTrue(content.contains("#3f5c4a"));
    }

    @Test
    @DisplayName("storePlaceholder - escape ký tự đặc biệt trong tên món")
    void storePlaceholder_EscapesXml() {
        storageService.storePlaceholder("menu", "mon-2.svg", "Bún & Chả <test>", "#000000");

        String content = readString(uploadDir.resolve("menu").resolve("mon-2.svg"));
        assertTrue(content.contains("Bún &amp; Chả &lt;test&gt;"));
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
