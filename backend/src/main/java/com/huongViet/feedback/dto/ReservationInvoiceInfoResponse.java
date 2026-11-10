package com.huongViet.feedback.dto;

import java.math.BigDecimal;

public record ReservationInvoiceInfoResponse(
    String reservationId,
    String invoiceId,
    String invoiceCode,
    String invoiceStatus,
    BigDecimal finalAmount,
    boolean isPaid,
    boolean hasFeedback
) {}

