package com.huongViet.order;

public record CashCheckoutRequest(
    String phoneNumber,
    String voucherCode,
    Boolean usePoints
) {}
