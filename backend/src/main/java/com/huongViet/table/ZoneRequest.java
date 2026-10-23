package com.huongViet.table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ZoneRequest(
    @NotBlank(message = "Tên khu vực không được để trống")
    @Size(max = 50, message = "Tên khu vực tối đa 50 ký tự")
    String zoneName,

    @NotNull(message = "Trạng thái VIP không được null")
    Boolean isVip
) {}
