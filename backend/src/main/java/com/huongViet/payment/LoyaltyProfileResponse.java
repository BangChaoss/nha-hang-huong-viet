package com.huongViet.payment;

import java.math.BigDecimal;
import java.util.List;

public record LoyaltyProfileResponse(
        String phoneNumber,
        BigDecimal pointsBalance,
        List<AvailableVoucherResponse> vouchers
) {
    public record AvailableVoucherResponse(
            String id, String voucherCode, String title, String description,
            String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount,
            BigDecimal minOrderAmount, String startDate, String endDate
    ) {}
}
