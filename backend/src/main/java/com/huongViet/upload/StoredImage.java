package com.huongViet.upload;

/**
 * Ảnh đã lưu: đường dẫn công khai và nơi lưu trữ thực tế.
 *
 * @param url     đường dẫn công khai để lưu vào {@code menu_items.image_url}
 * @param backend {@code cloudinary} hoặc {@code local}
 */
public record StoredImage(String url, String backend) {}
