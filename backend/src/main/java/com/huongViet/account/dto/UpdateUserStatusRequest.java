package com.huongViet.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(regexp = "^(ACTIVE|INACTIVE|LOCKED)$", message = "Trạng thái chỉ có thể là ACTIVE, INACTIVE hoặc LOCKED")
    String status
) {}
