package com.huongViet.menu;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(
    @NotNull(message = "Trạng thái khả dụng không được null")
    Boolean isAvailable
) {}
