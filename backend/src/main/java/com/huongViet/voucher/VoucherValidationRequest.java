package com.huongViet.voucher;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record VoucherValidationRequest(@NotBlank String voucherCode, @NotBlank @Size(max=20) String phoneNumber,
 @NotNull @DecimalMin(value="0.00") BigDecimal orderAmount) {}
