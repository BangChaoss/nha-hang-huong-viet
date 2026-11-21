package com.huongViet.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record VnpayCreateRequest(
        @NotBlank @Size(max = 36) String tableId,
        @NotNull
        @DecimalMin(value = "1000.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal subtotal,

        @Pattern(
                regexp = "^$|^[0-9+() .-]{8,20}$",
                message = "Số điện thoại không hợp lệ"
        )
        String phoneNumber,

        @Size(max = 50)
        String voucherCode,

        @DecimalMin(value = "0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal pointsToUse,

        Boolean usePoints,

        @NotBlank @Size(max = 36) String diningOrderId
) {
    public VnpayCreateRequest(String tableId, BigDecimal subtotal, String phoneNumber, String voucherCode,
                              BigDecimal pointsToUse, Boolean usePoints) {
        this(tableId, subtotal, phoneNumber, voucherCode, pointsToUse, usePoints, null);
    }
}