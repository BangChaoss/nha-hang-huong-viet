package com.huongViet.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Yêu cầu tạo đơn hàng mới (UC-15 Gọi món).
 * {@code submit} mặc định là {@code true} — chỉ khi gửi đơn đi bếp mới tạo phiếu bếp (BR-32).
 */
public record CreateOrderRequest(
    @NotBlank(message = "Mã bàn không được để trống")
    String tableId,

    OrderType orderType,

    @NotEmpty(message = "Danh sách món không được để trống")
    List<@Valid OrderItemRequest> items,

    Boolean submit
) {}
