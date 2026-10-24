package com.huongViet.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một dòng món được yêu cầu trong đơn (UC-15 Gọi món).
 */
public record OrderItemRequest(
    @NotBlank(message = "Mã món ăn không được để trống")
    String menuItemId,

    @NotNull(message = "Số lượng không được null")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    Integer quantity,

    @Size(max = 255, message = "Ghi chú đặc biệt tối đa 255 ký tự")
    String note
) {}
