package com.huongViet.order;

import java.math.BigDecimal;

public record CashCheckoutResponse(
    String tableId, BigDecimal subtotal, BigDecimal vat, BigDecimal discountAmount,
    BigDecimal pointsRedeemed, BigDecimal total, BigDecimal pointsAwarded, String tableNumber
) {}
