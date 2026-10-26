package com.huongViet.table;

import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(
    @NotNull(message = "Trạng thái bàn không được null")
    TableStatus status
) {}
