package com.huongViet.table;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FloorPlanLayoutRequest(
    @NotBlank(message = "ID bàn không được để trống")
    String tableId,

    @NotNull(message = "Tọa độ X không được null")
    Double positionX,

    @NotNull(message = "Tọa độ Y không được null")
    Double positionY,

    @NotNull(message = "Chiều rộng không được null")
    @DecimalMin(value = "10.0", message = "Chiều rộng tối thiểu 10.0")
    Double width,

    @NotNull(message = "Chiều cao không được null")
    @DecimalMin(value = "10.0", message = "Chiều cao tối thiểu 10.0")
    Double height,

    @NotNull(message = "Góc xoay không được null")
    Double rotation,

    @NotNull(message = "Hình dạng không được null")
    FloorPlanShape shape
) {}
