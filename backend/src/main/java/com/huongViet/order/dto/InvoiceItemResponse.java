package com.huongViet.order.dto;

import java.math.BigDecimal;

public record InvoiceItemResponse(
    String id,
    String dishId,
    String dishName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal totalPrice
) {}