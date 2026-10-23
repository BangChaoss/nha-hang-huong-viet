package com.huongViet.reservation.dto;

import com.huongViet.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReservationStatusRequest(
    @NotNull(message = "Trạng thái mới không được để trống")
    ReservationStatus status,

    String tableId,
    String reason
) {}
