package com.huongViet.table;

import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(
    @NotNull(message = "Trạng thái bàn không được null")
    TableStatus status,
    String reservationId,
    String reservationAction,
    String reason
) {
    public UpdateTableStatusRequest(TableStatus status) {
        this(status, null, null, null);
    }
}
