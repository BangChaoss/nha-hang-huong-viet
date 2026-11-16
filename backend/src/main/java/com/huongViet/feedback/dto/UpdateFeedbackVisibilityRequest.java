package com.huongViet.feedback.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFeedbackVisibilityRequest(
    @NotNull(message = "Trạng thái hiển thị không được để trống")
    Boolean isPublic
) {}

