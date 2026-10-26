package com.huongViet.menu;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MenuItemRequest(
    @NotBlank(message = "Danh mục không được để trống")
    String categoryId,

    @NotBlank(message = "Tên món ăn không được để trống")
    @Size(max = 100, message = "Tên món ăn tối đa 100 ký tự")
    String itemName,

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    String description,

    @NotNull(message = "Giá món ăn không được để trống")
    @DecimalMin(value = "0.01", message = "Giá món ăn phải lớn hơn 0")
    BigDecimal currentPrice,

    @Size(max = 255, message = "Đường dẫn ảnh tối đa 255 ký tự")
    String imageUrl,

    Boolean isAvailable
) {}
