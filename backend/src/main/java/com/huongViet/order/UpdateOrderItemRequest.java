package com.huongViet.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thay đổi số lượng hoặc ghi chú của một món trong đơn đang mở (UC-17, STT 84).
 */
public record UpdateOrderItemRequest(
    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải ít nhất là 1")
    @Max(value = 99, message = "Số lượng tối đa là 99")
    Integer quantity,

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    String note
) {}
