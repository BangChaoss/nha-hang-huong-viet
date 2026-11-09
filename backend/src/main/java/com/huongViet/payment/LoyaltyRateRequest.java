package com.huongViet.payment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LoyaltyRateRequest(
    @NotNull(message = "Tỷ lệ tích điểm không được để trống")
    @DecimalMin(value = "0.0", message = "Tỷ lệ tích điểm tối thiểu là 0%")
    @DecimalMax(value = "100.0", message = "Tỷ lệ tích điểm tối đa là 100%")
    @Digits(integer = 3, fraction = 3)
    BigDecimal earnRatePercent,

    @DecimalMin(value = "1.0", message = "Giá trị quy đổi 1 điểm phải tối thiểu 1 VNĐ")
    @Digits(integer = 9, fraction = 2)
    BigDecimal pointValueVnd
) {
    public LoyaltyRateRequest(BigDecimal earnRatePercent) {
        this(earnRatePercent, BigDecimal.ONE);
    }
}
