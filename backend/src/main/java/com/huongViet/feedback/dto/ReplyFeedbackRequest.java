package com.huongViet.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyFeedbackRequest(
    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Size(max = 2000, message = "Phản hồi không được vượt quá 2000 ký tự")
    String staffResponse
) {}

