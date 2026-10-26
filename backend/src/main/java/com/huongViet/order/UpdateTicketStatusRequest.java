package com.huongViet.order;

import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu cập nhật trạng thái phiếu bếp (BR-37: bếp chỉ được đổi trạng thái).
 */
public record UpdateTicketStatusRequest(
    @NotNull(message = "Trạng thái phiếu bếp không được null")
    CookingStatus status
) {}
