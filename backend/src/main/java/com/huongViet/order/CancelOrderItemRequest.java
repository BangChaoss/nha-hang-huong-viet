package com.huongViet.order;

import jakarta.validation.constraints.Size;

/**
 * Huỷ một món trong đơn (UC-17, STT 85).
 */
public record CancelOrderItemRequest(
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    String reason
) {}
