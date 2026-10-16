package com.huongViet.voucher;
import java.math.BigDecimal;
public record VoucherValidationResponse(boolean valid, String message, BigDecimal discountAmount, String voucherCode) {}
