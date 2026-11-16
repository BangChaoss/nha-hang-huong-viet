package com.huongViet.report.dto;

import java.math.BigDecimal;

public record RevenueSummaryResponse(
    BigDecimal netRevenue,
    BigDecimal grossRevenue,
    BigDecimal totalVoucherDiscount,
    BigDecimal totalDiscount,
    BigDecimal totalTax,
    long totalInvoices,
    BigDecimal averageOrderValue,
    BigDecimal cashRevenue,
    BigDecimal vnpayRevenue,
    BigDecimal otherPaymentRevenue
) {
    public static RevenueSummaryResponse empty() {
        return new RevenueSummaryResponse(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0L,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}

