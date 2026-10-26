package com.huongViet.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record VnpayCreateRequest(
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

        Boolean usePoints
) {}