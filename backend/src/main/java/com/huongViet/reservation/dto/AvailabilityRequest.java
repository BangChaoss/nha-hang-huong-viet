package com.huongViet.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AvailabilityRequest(
    @NotNull(message = "Ngày không được để trống")
    LocalDate date,

    @NotNull(message = "Số khách không được để trống")
    @Min(value = 1, message = "Số khách phải ít nhất 1")
    Integer partySize
) {}
