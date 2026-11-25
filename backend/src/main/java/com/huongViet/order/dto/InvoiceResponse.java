package com.huongViet.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
    String id,
    String invoiceCode,
    String orderId,
    String tableNumber,
    String staffId,
    String customerPhone,
    String loyaltyPhone,
    String voucherCode,
    BigDecimal voucherDiscountAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal finalAmount,
    int loyaltyPointsEarned,
    String status,
    LocalDateTime paidAt,
    List<InvoiceItemResponse> items,
    BigDecimal vipFeeAmount,
    BigDecimal vipDepositPaid
) {
    public InvoiceResponse(
        String id,
        String invoiceCode,
        String orderId,
        String tableNumber,
        String staffId,
        String customerPhone,
        String loyaltyPhone,
        String voucherCode,
        BigDecimal voucherDiscountAmount,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal finalAmount,
        int loyaltyPointsEarned,
        String status,
        LocalDateTime paidAt,
        List<InvoiceItemResponse> items
    ) {
        this(id, invoiceCode, orderId, tableNumber, staffId, customerPhone, loyaltyPhone, voucherCode,
            voucherDiscountAmount, subtotalAmount, discountAmount, taxAmount, finalAmount, loyaltyPointsEarned,
            status, paidAt, items, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
