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
) {}