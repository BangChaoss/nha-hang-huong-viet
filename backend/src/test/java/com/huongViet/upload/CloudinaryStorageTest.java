package com.huongViet.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryStorageTest {

    @Test
    @DisplayName("isConfigured - false khi chưa đặt CLOUDINARY_URL")
    void isConfigured_False_WhenNotConfigured() {
        assertFalse(new CloudinaryStorage("", "huongviet/menu").isConfigured());
    }

    @Test
    @DisplayName("isConfigured - true khi có CLOUDINARY_URL hợp lệ")
    void isConfigured_True_WhenUrlValid() {
        CloudinaryStorage storage = new CloudinaryStorage(
                "cloudinary://819336383876664:fAsa2GTicPn_ZFqvj0LAji-GOCQ@db8sy6nay", "huongviet/menu");
        assertTrue(storage.isConfigured());
    }

    @Test
    @DisplayName("isConfigured - false khi URL không hợp lệ, ứng dụng vẫn khởi động được")
    void isConfigured_False_WhenUrlInvalid() {
        assertFalse(new CloudinaryStorage("khong-phai-url-cloudinary", "huongviet/menu").isConfigured());
    }

    @Test
    @DisplayName("extractPublicId - bỏ qua segment phiên bản và đuôi tệp")
    void extractPublicId_RemovesVersionAndExtension() {
        String url = "https://res.cloudinary.com/db8sy6nay/image/upload/v1792809248/huongviet/menu/abc123.png";

        assertEquals("huongviet/menu/abc123", CloudinaryStorage.extractPublicId(url));
    }

    @Test
    @DisplayName("extractPublicId - xử lý ảnh không nằm trong thư mục con")
    void extractPublicId_WithoutFolder() {
        String url = "https://res.cloudinary.com/db8sy6nay/image/upload/v123/abc123.jpg";

        assertEquals("abc123", CloudinaryStorage.extractPublicId(url));
    }

    @Test
    @DisplayName("extractPublicId - bỏ qua tham số truy vấn")
    void extractPublicId_IgnoresQueryString() {
        String url = "https://res.cloudinary.com/db8sy6nay/image/upload/v123/huongviet/menu/abc.png?w=200";

        assertEquals("huongviet/menu/abc", CloudinaryStorage.extractPublicId(url));
    }

    @Test
    @DisplayName("extractPublicId - trả null với URL không phải của Cloudinary")
    void extractPublicId_Null_ForForeignUrl() {
        assertNull(CloudinaryStorage.extractPublicId("https://example.com/photo.jpg"));
        assertNull(CloudinaryStorage.extractPublicId("/uploads/menu/abc.png"));
        assertNull(CloudinaryStorage.extractPublicId(null));
    }
}
