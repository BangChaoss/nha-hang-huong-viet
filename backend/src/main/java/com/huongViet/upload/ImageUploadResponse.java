package com.huongViet.upload;

/**
 * Kết quả tải ảnh lên: đường dẫn công khai để lưu vào {@code menu_items.image_url}
 * kèm nơi lưu trữ thực tế.
 */
public record ImageUploadResponse(String url, String backend) {}
