package com.huongViet.report.dto;

import java.math.BigDecimal;

public record DishSalesItemResponse(
    String dishId,
    String dishName,
    String categoryName,
    long quantitySold,
    BigDecimal totalSales,
    double percentageOfTotal
) {}

