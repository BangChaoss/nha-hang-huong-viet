package com.huongViet.reservation.dto;

public record CancelReservationRequest(
    String reason,
    String phone
) {
    public CancelReservationRequest(String reason) {
        this(reason, null);
    }
}
