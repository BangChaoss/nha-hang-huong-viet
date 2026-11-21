package com.huongViet.order;

import java.math.BigDecimal;

/** Thông tin yêu cầu thanh toán tiền mặt / VNPay Callback. */
public record CashCheckoutRequest(
    String phoneNumber,
    String voucherCode,
    Boolean usePoints,
    BigDecimal pointsToUse,
    BigDecimal expectedTotal
) {
    public CashCheckoutRequest(String phoneNumber, String voucherCode, Boolean usePoints, BigDecimal pointsToUse) {
        this(phoneNumber, voucherCode, usePoints, pointsToUse, null);
    }
    public CashCheckoutRequest(String phoneNumber, String voucherCode, Boolean usePoints) {
        this(phoneNumber, voucherCode, usePoints, null);
    }
}