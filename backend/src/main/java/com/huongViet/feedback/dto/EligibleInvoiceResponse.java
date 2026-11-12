package com.huongViet.feedback.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EligibleInvoiceResponse(
    String id,
    String invoiceCode,
    BigDecimal finalAmount,
    String tableNumber,
    LocalDateTime paidAt
) {}

