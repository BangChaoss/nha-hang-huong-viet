package com.huongViet.reservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    String guestName,

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ")
    String guestPhone,

    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    String guestEmail,

    @NotNull(message = "Ngày đặt bàn không được để trống")
    LocalDate reservationDate,

    @NotNull(message = "Giờ đặt bàn không được để trống")
    LocalTime reservationTime,

    @NotNull(message = "Số khách không được để trống")
    @Min(value = 1, message = "Số khách phải ít nhất 1")
    @Max(value = 20, message = "Số khách tối đa 20 người")
    Integer partySize,

    @NotBlank(message = "Khu vực không được để trống")
    String zoneId,

    @Size(max = 500, message = "Yêu cầu đặc biệt tối đa 500 ký tự")
    String specialRequests
) {}
