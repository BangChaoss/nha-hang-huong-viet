package com.huongViet.reservation.dto;

import com.huongViet.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateReservationStatusRequest(
    @NotNull(message = "Trạng thái mới không được để trống")
    ReservationStatus status,

    String tableId,
    List<String> tableIds,
    String reason
) {
    public UpdateReservationStatusRequest(ReservationStatus status, String tableId, String reason) {
        this(status, tableId, tableId != null && !tableId.isBlank() ? List.of(tableId) : null, reason);
    }
}
