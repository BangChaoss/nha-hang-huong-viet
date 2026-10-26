package com.huongViet.table;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TableRequest(
    @NotBlank(message = "Số bàn không được để trống")
    @Size(max = 20, message = "Số bàn tối đa 20 ký tự")
    String tableNumber,

    @NotNull(message = "Sức chứa số ghế không được null")
    @Min(value = 1, message = "Sức chứa số ghế phải lớn hơn 0")
    Integer capacity,

    @NotBlank(message = "ID khu vực không được để trống")
    String zoneId
) {}
