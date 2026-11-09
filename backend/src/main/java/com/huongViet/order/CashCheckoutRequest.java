package com.huongViet.order;

import java.math.BigDecimal;

/** Thông tin yêu cầu thanh toán tiền mặt / VNPay Callback. */
public record CashCheckoutRequest(
    String phoneNumber,
    String voucherCode,
    Boolean usePoints,
    BigDecimal pointsToUse
) {
    public CashCheckoutRequest(String phoneNumber, String voucherCode, Boolean usePoints) {
        this(phoneNumber, voucherCode, usePoints, null);
    }
}