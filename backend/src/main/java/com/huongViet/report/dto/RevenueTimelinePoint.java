package com.huongViet.report.dto;

import java.math.BigDecimal;

public record RevenueTimelinePoint(
    String timeLabel,
    BigDecimal revenue,
    long orderCount
) {}

