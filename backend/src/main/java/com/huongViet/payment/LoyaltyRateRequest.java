package com.huongViet.payment;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record LoyaltyRateRequest(@NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer=3, fraction=3) BigDecimal earnRatePercent) {}
