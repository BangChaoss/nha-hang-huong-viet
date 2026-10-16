package com.huongViet.voucher;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherRequest(
    @NotBlank @Size(max=50) String voucherCode,
    @NotBlank @Size(max=150) String title,
    String description,
    @NotNull Voucher.DiscountType discountType,
    @NotNull @DecimalMin(value="0.01") BigDecimal discountValue,
    @DecimalMin(value="0.00") BigDecimal maxDiscountAmount,
    @NotNull @DecimalMin(value="0.00") BigDecimal minOrderAmount,
    @NotNull LocalDateTime startDate,
    @NotNull LocalDateTime endDate,
    @NotNull @Min(1) Integer totalQuantity,
    @NotNull @Min(1) Integer maxUsesPerCustomer
) {}
