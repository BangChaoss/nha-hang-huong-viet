package com.huongViet.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Thêm nhiều món cùng lúc vào đơn đang mở sau khi nhân viên xác nhận gửi bếp.
 */
public record AddOrderItemsBatchRequest(
    @NotEmpty(message = "Danh sách món không được để trống")
    List<@Valid AddOrderItemRequest> items
) {}
