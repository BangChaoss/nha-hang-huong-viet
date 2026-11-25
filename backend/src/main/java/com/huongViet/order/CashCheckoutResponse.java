package com.huongViet.order;

import java.math.BigDecimal;

/** Kết quả thanh toán tiền mặt / VNPay. */
public record CashCheckoutResponse(
    String tableId, BigDecimal subtotal, BigDecimal vat, BigDecimal discountAmount,
    BigDecimal pointsRedeemed, BigDecimal total, BigDecimal pointsAwarded, String tableNumber,
    BigDecimal vipFeeAmount, BigDecimal vipDepositPaid
) {
    public CashCheckoutResponse(String tableId, BigDecimal subtotal, BigDecimal vat, BigDecimal discountAmount,
                                BigDecimal pointsRedeemed, BigDecimal total, BigDecimal pointsAwarded,
                                String tableNumber) {
        this(tableId, subtotal, vat, discountAmount, pointsRedeemed, total, pointsAwarded, tableNumber,
            BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
