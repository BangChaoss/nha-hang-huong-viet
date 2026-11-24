package com.huongViet.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdjustOrderItemsRequest(
    @NotEmpty List<@NotBlank String> itemIds,
    @NotNull Action action,
    @NotNull CookingStatus expectedStatus,
    @Positive int expectedQuantity,
    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String note,
    @Size(max = 255, message = "Lý do tối đa 255 ký tự") String reason
) {
    public enum Action { DECREMENT, UPDATE_NOTE, CANCEL }
}
