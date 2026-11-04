package com.huongViet.order;

/** Thông tin yêu cầu thanh toán tiền mặt. */
public record CashCheckoutRequest(
    String phoneNumber,
    String voucherCode,
    Boolean usePoints
) {}
