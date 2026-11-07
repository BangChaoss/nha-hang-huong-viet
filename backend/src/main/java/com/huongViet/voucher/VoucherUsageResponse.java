package com.huongViet.voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherUsageResponse(
    String id,
    String voucherId,
    String customerPhone,
    String invoiceId,
    String invoiceCode,
    BigDecimal discountApplied,
    LocalDateTime usedAt
) {}